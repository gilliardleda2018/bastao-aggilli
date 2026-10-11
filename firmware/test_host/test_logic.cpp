// Testes da fila persistente (diario em flash), gatilho, bateria, datas, CSV do cartao SD e display.
#include <stdio.h>
#include <string.h>

#include <vector>

#include "bastao_logic.h"
#include "persist_log.h"
#include "reading_queue.h"

using namespace logic;

static int failures = 0, checks = 0;
#define CHECK(cond)                                                                       \
  do {                                                                                    \
    checks++;                                                                             \
    if (!(cond)) { failures++; printf("  FALHOU %s:%d  %s\n", __FILE__, __LINE__, #cond); } \
  } while (0)

typedef ReadingQueue<8> Q;

// "Arquivo" em memoria: simula o LittleFS
struct FakeFile {
  std::vector<uint8_t> data;
  void append(const uint8_t* p, size_t n) { data.insert(data.end(), p, p + n); }
};

static void pushLog(FakeFile& f, Q& q, uint32_t seq, uint16_t boot, uint32_t up, uint32_t ep, const char* id) {
  q.push(seq, boot, up, ep, id);
  uint8_t rec[plog::REC_SIZE];
  plog::encodePush(q.at(q.size() - 1), rec);
  f.append(rec, sizeof rec);
}
static void ackLog(FakeFile& f, Q& q, uint32_t seq) {
  q.ack(seq);
  uint8_t rec[plog::REC_SIZE];
  plog::encodeAck(seq, rec);
  f.append(rec, sizeof rec);
}

static void testPersistReplay() {
  printf("Diario: desligar e religar o bastao\n");
  FakeFile f;
  Q q;
  pushLog(f, q, 1, 7, 1000, 0, "076000000000001");
  pushLog(f, q, 2, 7, 2000, 0, "076000000000002");
  pushLog(f, q, 3, 7, 3000, 1760000000u, "076000000000003");
  ackLog(f, q, 1);
  CHECK(q.size() == 2);

  // "Religa": fila nova a partir do arquivo
  Q r;
  plog::ReplayResult res = plog::replay(f.data.data(), f.data.size(), r);
  CHECK(!res.corrupted);
  CHECK(res.records == 4);
  CHECK(res.maxSeq == 3);
  CHECK(res.maxBoot == 7);
  CHECK(r.size() == 2);
  CHECK(r.at(0).seq == 2);
  CHECK(strcmp(r.at(0).id, "076000000000002") == 0);
  CHECK(r.at(1).epoch == 1760000000u);
  CHECK(r.at(1).boot == 7 && r.at(1).uptimeMs == 3000);
  CHECK(r.unsent() == 2);
}

static void testPersistTornWrite() {
  printf("Diario: falta de energia no meio da gravacao\n");
  FakeFile f;
  Q q;
  pushLog(f, q, 1, 1, 10, 0, "076000000000001");
  pushLog(f, q, 2, 1, 20, 0, "076000000000002");
  f.data.resize(f.data.size() - 10);  // ultimo registro cortado
  Q r;
  plog::ReplayResult res = plog::replay(f.data.data(), f.data.size(), r);
  CHECK(res.corrupted);
  CHECK(r.size() == 1);
  CHECK(res.validBytes == plog::REC_SIZE);

  // bit trocado no meio: para no registro ruim
  FakeFile g;
  Q q2;
  pushLog(g, q2, 1, 1, 10, 0, "076000000000001");
  pushLog(g, q2, 2, 1, 20, 0, "076000000000002");
  pushLog(g, q2, 3, 1, 30, 0, "076000000000003");
  g.data[plog::REC_SIZE + 25] ^= 0x01;
  Q r2;
  res = plog::replay(g.data.data(), g.data.size(), r2);
  CHECK(res.corrupted);
  CHECK(r2.size() == 1);
}

static void testPersistCompact() {
  printf("Diario: compactacao guarda a numeracao\n");
  FakeFile f;
  Q q;
  for (uint32_t s = 1; s <= 5; s++) pushLog(f, q, s, 2, s * 100, 0, "076000000000009");
  ackLog(f, q, 5);  // tudo confirmado: fila vazia
  CHECK(q.size() == 0);
  FakeFile c;
  plog::compact(q, 5, [&](const uint8_t* p, size_t n) { c.append(p, n); });
  CHECK(c.data.size() == plog::compactSize(q));
  Q r;
  plog::ReplayResult res = plog::replay(c.data.data(), c.data.size(), r);
  CHECK(r.size() == 0);
  CHECK(res.maxSeq == 5);  // proxima leitura sera a 6, mesmo depois de religar

  // com pendentes: ACK marcador nao apaga leituras
  Q q2;
  q2.push(7, 3, 1, 0, "A");
  q2.push(8, 3, 2, 0, "B");
  FakeFile c2;
  plog::compact(q2, 8, [&](const uint8_t* p, size_t n) { c2.append(p, n); });
  Q r2;
  plog::replay(c2.data.data(), c2.data.size(), r2);
  CHECK(r2.size() == 2);
  CHECK(r2.at(1).seq == 8);
}

static void testFillEpoch() {
  printf("Fila: hora informada pelo celular depois das leituras\n");
  Q q;
  q.push(1, 4, 5000, 0, "A");   // ligacao 4, sem hora
  q.push(2, 3, 9000, 0, "B");   // ligacao anterior: nao da para saber
  q.push(3, 4, 65000, 0, "C");
  CHECK(q.fillEpoch(4, 1760000000u) == 2);
  CHECK(q.at(0).epoch == 1760000005u);
  CHECK(q.at(1).epoch == 0);
  CHECK(q.at(2).epoch == 1760000065u);
}

static void testTrigger() {
  printf("Gatilho: ruido, toque curto e toque longo\n");
  TriggerFsm t(30, 1500);
  CHECK(t.update(true, 0) == EV_NONE);
  CHECK(t.update(false, 10) == EV_NONE);  // ruido de 10 ms
  CHECK(t.update(false, 50) == EV_NONE);
  CHECK(!t.pressed());
  CHECK(t.update(true, 100) == EV_NONE);
  CHECK(t.update(true, 131) == EV_PRESS);
  CHECK(t.pressed());
  CHECK(t.update(true, 900) == EV_NONE);
  CHECK(t.update(true, 1631) == EV_LONG_PRESS);
  CHECK(t.update(true, 3000) == EV_NONE);  // longo dispara uma vez so
  CHECK(t.update(false, 3010) == EV_NONE);
  CHECK(t.update(false, 3045) == EV_RELEASE);
}

static void testBattery() {
  printf("Bateria\n");
  CHECK(batteryPercent(4250) == 100);
  CHECK(batteryPercent(4200) == 100);
  CHECK(batteryPercent(3840) == 50);
  CHECK(batteryPercent(3000) == 0);
  int a = batteryPercent(3900), b = batteryPercent(3950);
  CHECK(a > 60 && a < 70 && b == 70);
}

static void testCivil() {
  printf("Datas\n");
  Civil c = toCivil(0, 0);
  CHECK(c.year == 1970 && c.month == 1 && c.day == 1 && c.hour == 0);
  // 2026-10-11 17:43:20 UTC = 14:43:20 em Sao Paulo (-03:00)
  c = toCivil(1791740600u, -180);
  CHECK(c.year == 2026 && c.month == 10 && c.day == 11);
  CHECK(c.hour == 14 && c.minute == 43 && c.second == 20);
  c = toCivil(1709164800u, 0);  // 2024-02-29 (ano bissexto)
  CHECK(c.year == 2024 && c.month == 2 && c.day == 29);
  c = toCivil(1767225599u, 0);  // 2025-12-31 23:59:59
  CHECK(c.year == 2025 && c.month == 12 && c.day == 31 && c.hour == 23 && c.second == 59);
  c = toCivil(1767225600u, -180);  // 2026-01-01 00:00 UTC ainda e 31/12 em -03:00
  CHECK(c.year == 2025 && c.day == 31 && c.hour == 21);
}

static void testCsv() {
  printf("Cartao SD: nomes de arquivo e linhas\n");
  char name[48], line[128];
  csvFileName(1791740600u, -180, 12, name, sizeof name);
  CHECK(strcmp(name, "/leituras/2026-10-11.csv") == 0);
  csvFileName(0, -180, 12, name, sizeof name);
  CHECK(strcmp(name, "/leituras/sem-data-ligacao-0012.csv") == 0);
  csvLine(42, 1791740600u, -180, "076000000001234", 12, 98765, true, line, sizeof line);
  CHECK(strcmp(line, "42;2026-10-11T14:43:20-03:00;076000000001234;12;98765;online\n") == 0);
  csvLine(43, 0, -180, "076000000001235", 12, 99000, false, line, sizeof line);
  CHECK(strcmp(line, "43;;076000000001235;12;99000;offline\n") == 0);
  CHECK(strcmp(csvHeader(), "seq;data_hora;brinco;ligacao;uptime_ms;origem\n") == 0);
}

static void testScreen() {
  printf("Display\n");
  ScreenState s;
  Screen o;
  buildScreen(s, o);
  CHECK(strcmp(o.national, "aguardando") == 0);
  CHECK(strncmp(o.status, "-- CONT", 7) == 0);
  CHECK(strstr(o.status, "--%") != nullptr);

  s.connected = true; s.mode = 1; s.sd = SD_OK; s.battPct = 87;
  s.lastId = "076000000001234"; s.sessionCount = 47; s.pending = 0;
  buildScreen(s, o);
  CHECK(strcmp(o.status, "BT GAT SD 87%") == 0);
  CHECK(strcmp(o.country, "076") == 0);
  CHECK(strcmp(o.national, "000000001234") == 0);
  CHECK(strcmp(o.footer, "Sessao 47  Fila 0") == 0);

  s.flash = FL_DUP; buildScreen(s, o);
  CHECK(strcmp(o.footer, "REPETIDO") == 0);
  s.connected = false; s.flash = FL_STORED; s.pending = 3; buildScreen(s, o);
  CHECK(strcmp(o.footer, "GUARDADO (fila 3)") == 0);
  s.flash = FL_NONE; s.localCount = 12; buildScreen(s, o);
  CHECK(strcmp(o.footer, "Lidos 12  Fila 3") == 0);
  s.lastId = "02AB0311223344556677"; buildScreen(s, o);
  CHECK(strcmp(o.country, "RAW") == 0 && strlen(o.national) == 12);
  for (const char* line : {o.status, o.footer}) CHECK(strlen(line) <= 21);  // cabe em 128 px (fonte 6 px)
}

int runLogicTests() {
  testPersistReplay();
  testPersistTornWrite();
  testPersistCompact();
  testFillEpoch();
  testTrigger();
  testBattery();
  testCivil();
  testCsv();
  testScreen();
  printf("\nlogica do bastao: %d verificacoes, %d falhas\n", checks, failures);
  return failures;
}
