// Testes do parser e da fila, rodando no PC (sem ESP32).
//   cd firmware/test_host && make
#include <stdio.h>
#include <string.h>

#include "reading_queue.h"
#include "tag_parser.h"

using namespace tagparser;

static int failures = 0, checks = 0;
#define CHECK(cond)                                                     \
  do {                                                                  \
    checks++;                                                           \
    if (!(cond)) { failures++; printf("  FALHOU %s:%d  %s\n", __FILE__, __LINE__, #cond); } \
  } while (0)

static void testWl134Valid() {
  printf("WL134: pacote valido\n");
  uint8_t p[WL134_LEN];
  buildWl134(982, 123456789ULL, true, p);
  Tag t;
  CHECK(parseWl134(p, sizeof p, t) == OK);
  CHECK(strcmp(t.id, "982000123456789") == 0);
  CHECK(t.country == 982);
  CHECK(t.national == 123456789ULL);
  CHECK(t.animal);
  CHECK(endsWithWl134(p, sizeof p));
}

static void testWl134KnownBytes() {
  // Vetor escrito a mao (independente do buildWl134): pais 076 (Brasil), nacional 000000001234.
  // 1234 = 0x4D2 -> LSB primeiro "2D40000000"; 76 = 0x4C -> "C400"
  printf("WL134: vetor conhecido (Brasil 076)\n");
  const char* body = "2D40000000" "C400" "1" "1" "0000" "000000";
  uint8_t p[WL134_LEN];
  p[0] = STX;
  memcpy(p + 1, body, 26);
  uint8_t x = 0;
  for (int i = 1; i <= 26; i++) x ^= p[i];
  p[27] = x; p[28] = (uint8_t)~x; p[29] = ETX;
  char out[32];
  CHECK(parse(MODE_WL134, p, sizeof p, out, sizeof out) == OK);
  CHECK(strcmp(out, "076000000001234") == 0);
}

static void testWl134Checksum() {
  printf("WL134: checksum errado e descartado\n");
  uint8_t p[WL134_LEN];
  buildWl134(982, 123456789ULL, true, p);
  p[3] = (p[3] == '0') ? '1' : '0';  // corrompe um digito do ID
  Tag t;
  CHECK(parseWl134(p, sizeof p, t, true) == BAD_CHECKSUM);
  CHECK(parseWl134(p, sizeof p, t, false) == OK);  // modo diagnostico aceita
  char out[32];
  // No AUTO, o pacote corrompido NAO pode cair no ASCII15 e virar um ID falso
  CHECK(parse(MODE_AUTO, p, sizeof p, out, sizeof out) == BAD_CHECKSUM);
}

static void testWl134WithNoise() {
  printf("WL134: lixo antes e pacote repetido\n");
  uint8_t buf[80];
  size_t n = 0;
  buf[n++] = 0xFF; buf[n++] = 0x00; buf[n++] = 0x02;  // STX solto
  uint8_t p[WL134_LEN];
  buildWl134(900, 274877906943ULL, false, p);          // maior numero nacional possivel
  memcpy(buf + n, p, WL134_LEN); n += WL134_LEN;
  memcpy(buf + n, p, 10); n += 10;                      // inicio do proximo pacote
  char out[32];
  CHECK(parse(MODE_AUTO, buf, n, out, sizeof out) == OK);
  CHECK(strcmp(out, "900274877906943") == 0);
  CHECK(!endsWithWl134(buf, n));
}

static void testWl134Range() {
  printf("WL134: fora da faixa ISO\n");
  uint8_t p[WL134_LEN];
  buildWl134(982, 274877906944ULL, false, p);  // 2^38: nao cabe em 38 bits
  Tag t;
  CHECK(parseWl134(p, sizeof p, t) == OUT_OF_RANGE);
}

static void testAscii15() {
  printf("ASCII15: formatos decimais\n");
  char out[32];
  const char* a = "982000123456789\r\n";
  CHECK(parse(MODE_ASCII15, (const uint8_t*)a, strlen(a), out, sizeof out) == OK);
  CHECK(strcmp(out, "982000123456789") == 0);

  const char* b = "\x02" "982 000123456789" "\x03";
  CHECK(parse(MODE_AUTO, (const uint8_t*)b, strlen(b), out, sizeof out) == OK);
  CHECK(strcmp(out, "982000123456789") == 0);

  const char* c = "ID:076_000000001234;";
  CHECK(parse(MODE_ASCII15, (const uint8_t*)c, strlen(c), out, sizeof out) == OK);
  CHECK(strcmp(out, "076000000001234") == 0);

  const char* d = "9820001234567890";  // 16 digitos: nao e um ID ISO
  CHECK(parse(MODE_ASCII15, (const uint8_t*)d, strlen(d), out, sizeof out) == NOT_FOUND);

  const char* e = "12345";
  CHECK(parse(MODE_ASCII15, (const uint8_t*)e, strlen(e), out, sizeof out) == NOT_FOUND);

  const char* f = "seq 12 id 982000123456789";
  CHECK(parse(MODE_ASCII15, (const uint8_t*)f, strlen(f), out, sizeof out) == OK);
  CHECK(strcmp(out, "982000123456789") == 0);
}

static void testRawHex() {
  printf("RAW_HEX\n");
  const uint8_t f[] = {0x02, 0xAB, 0x03};
  char out[16];
  CHECK(parse(MODE_RAW_HEX, f, sizeof f, out, sizeof out) == OK);
  CHECK(strcmp(out, "02AB03") == 0);
}

static void testQueue() {
  printf("Fila: push, envio, ACK, reconexao e estouro\n");
  ReadingQueue<4> q;
  CHECK(q.push(1, 100, "A"));
  CHECK(q.push(2, 200, "B"));
  CHECK(q.push(3, 300, "C"));
  CHECK(q.size() == 3);

  // Envia duas, cai a conexao
  CHECK(q.nextToSend()->seq == 1); q.markSent();
  CHECK(q.nextToSend()->seq == 2); q.markSent();
  CHECK(q.unsent() == 1);

  // Reconecta: reenvia tudo o que nao foi confirmado
  q.rewind();
  CHECK(q.unsent() == 3);
  CHECK(q.nextToSend()->seq == 1); q.markSent();
  CHECK(q.nextToSend()->seq == 2); q.markSent();

  // App confirma ate 2
  CHECK(q.ack(2) == 2);
  CHECK(q.size() == 1);
  CHECK(q.nextToSend()->seq == 3);
  CHECK(strcmp(q.nextToSend()->id, "C") == 0);
  q.markSent();
  CHECK(q.nextToSend() == nullptr);

  // ACK repetido/antigo nao remove nada
  CHECK(q.ack(1) == 0);

  // Estouro: capacidade 4, a mais antiga sai
  CHECK(q.push(4, 400, "D"));
  CHECK(q.push(5, 500, "E"));
  CHECK(q.push(6, 600, "F"));
  CHECK(!q.push(7, 700, "G"));  // descartou a 3
  CHECK(q.size() == 4);
  CHECK(q.dropped() == 1);
  q.rewind();
  CHECK(q.nextToSend()->seq == 4);

  CHECK(q.ack(7) == 4);
  CHECK(q.size() == 0);
  CHECK(q.nextToSend() == nullptr);

  // Volta do contador de 32 bits
  ReadingQueue<4> w;
  w.push(0xFFFFFFFEu, 0, "X");
  w.push(0xFFFFFFFFu, 0, "Y");
  w.push(0u, 0, "Z");
  CHECK(w.ack(0xFFFFFFFFu) == 2);
  CHECK(w.nextToSend()->seq == 0u);
}

int main() {
  testWl134Valid();
  testWl134KnownBytes();
  testWl134Checksum();
  testWl134WithNoise();
  testWl134Range();
  testAscii15();
  testRawHex();
  testQueue();
  printf("\n%d verificacoes, %d falhas\n", checks, failures);
  return failures ? 1 : 0;
}
