// Bastao de coleta RFID - Aggilli
// Le brincos FDX-B por um modulo UART e envia os IDs ao app Android por BLE.
//
// v0.3:
//  - fila offline gravada na flash interna (LittleFS): sobrevive a desligar o bastao
//  - historico completo em cartao microSD (CSV, um arquivo por dia)
//  - display OLED 1,3" SH1106: status, ultimo brinco, contagem, avisos
//  - gatilho com toque longo para trocar o modo (continuo <-> gatilho), modo salvo na memoria
//  - data/hora e fuso enviados pelo celular (T), contagem da sessao (C), aviso de repetido (D)
//  - nome Bluetooth unico por bastao (sufixo do MAC)
//
// A logica testavel fica em lib/bastao_core (testes em firmware/test_host).

#include <Arduino.h>
#include "config.h"
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <FS.h>
#include <LittleFS.h>
#include <Preferences.h>
#include <SD.h>
#include <SPI.h>
#include <Wire.h>
#if DISPLAY_ENABLED
#include <U8g2lib.h>
#endif

#include "bastao_logic.h"
#include "persist_log.h"
#include "reading_queue.h"
#include "tag_parser.h"
#include <stdlib.h>

#ifndef DISPLAY_ENABLED
#define DISPLAY_ENABLED 1
#endif

static HardwareSerial RfidSerial(RFID_UART_NUM);
static Preferences prefs;
#if DISPLAY_ENABLED
static U8G2_SH1106_128X64_NONAME_F_HW_I2C oled(U8G2_R0, U8X8_PIN_NONE);
#endif

static BLECharacteristic* chReading = nullptr;
static BLECharacteristic* chStatus  = nullptr;
static char deviceName[32];

// ---------------------------------------------------------------------------
// Estado compartilhado com os callbacks do BLE (rodam em outra tarefa do FreeRTOS)
// ---------------------------------------------------------------------------
static portMUX_TYPE bleMux = portMUX_INITIALIZER_UNLOCKED;
static volatile bool     bleConnected = false;
static volatile bool     connEvent    = false;
static volatile bool     helloEvent   = false;
static volatile bool     ackEvent     = false;
static volatile uint32_t ackSeq       = 0;
static volatile bool     beepEvent    = false;
static volatile bool     dupEvent     = false;
static volatile int      modeRequest  = -1;    // -1 nada, 0/1 pedido do app
static volatile bool     timeEvent    = false;
static volatile uint32_t timeEpoch    = 0;
static volatile int32_t  timeTz       = 0;
static volatile int32_t  sessionCount = -1;

// ---------------------------------------------------------------------------
// Estado do firmware
// ---------------------------------------------------------------------------
static uint8_t  triggerMode = TRIGGER_MODE_DEFAULT;
static uint16_t bootNo = 0;
static int32_t  tzMinutes = TZ_MINUTES_DEFAULT;
static uint32_t epochAtBoot = 0;          // 0 = hora desconhecida nesta ligacao

static bool     streaming = false;        // conexao pronta para receber leituras
static uint32_t connectedAtMs = 0;
static uint32_t lastNotifyMs = 0;

static ReadingQueue<QUEUE_CAPACITY> queue;
static uint32_t seqNo = 0;
static uint32_t localCount = 0;
static bool     flashOk = false;          // LittleFS montado
static size_t   logBytes = 0;             // tamanho atual do diario (evita abrir o arquivo para medir)

static logic::SdState sdState = logic::SD_NONE;
static uint32_t sdRetryAt = 0;

static uint8_t  frameBuf[FRAME_MAX];
static size_t   frameLen = 0;
static uint32_t lastByteMs = 0;

static char     lastId[READING_ID_MAX] = {0};
static uint32_t lastIdMs = 0;

static logic::TriggerFsm trigger(DEBOUNCE_MS, LONG_PRESS_MS);

static uint32_t battMv = 0;
static uint32_t lastStatusMs = 0;

static logic::Flash flashKind = logic::FL_NONE;
static uint32_t flashUntil = 0;
static bool     screenDirty = true;
static uint32_t lastDrawMs = 0;

// ---------------------------------------------------------------------------
// Sinais (buzzer, LED, vibracao) por padroes, sem bloquear o loop
// Cada padrao alterna ligado/desligado com as duracoes em ms (termina em 0).
// ---------------------------------------------------------------------------
static const uint16_t PAT_OK[]     = {80, 0};                 // leitura entregue ao celular
static const uint16_t PAT_STORED[] = {350, 0};                // guardada na fila (celular longe)
static const uint16_t PAT_DUP[]    = {60, 80, 60, 0};         // brinco repetido na sessao
static const uint16_t PAT_MODE[]   = {40, 60, 40, 60, 40, 0}; // modo trocado
static const uint16_t PAT_BOOT[]   = {120, 0};

static const uint16_t* pattern = nullptr;
static uint8_t  patStep = 0;
static uint32_t patNextAt = 0;
static bool     patVibra = true;

static void outputs(bool on) {
  digitalWrite(PIN_BUZZER, on ? HIGH : LOW);
  digitalWrite(PIN_LED, on ? HIGH : LOW);
  digitalWrite(PIN_VIBRA, (on && patVibra) ? HIGH : LOW);
}

static void play(const uint16_t* p, bool vibra = true) {
  pattern = p;
  patStep = 0;
  patVibra = vibra;
  outputs(true);
  patNextAt = millis() + p[0];
}

static void servicePattern() {
  if (pattern == nullptr) return;
  if ((int32_t)(millis() - patNextAt) < 0) return;
  patStep++;
  uint16_t d = pattern[patStep];
  if (d == 0) { outputs(false); pattern = nullptr; return; }
  outputs(patStep % 2 == 0);  // passos pares ligam, impares desligam
  patNextAt = millis() + d;
}

static void flash(logic::Flash kind) {
  flashKind = kind;
  flashUntil = millis() + FLASH_MS;
  screenDirty = true;
}

// ---------------------------------------------------------------------------
// Memoria permanente: NVS (configuracao) e LittleFS (diario da fila)
// ---------------------------------------------------------------------------
static void loadSettings() {
  prefs.begin("bastao", false);
  bootNo = (uint16_t)(prefs.getUShort("boot", 0) + 1);
  prefs.putUShort("boot", bootNo);
  triggerMode = prefs.getUChar("mode", TRIGGER_MODE_DEFAULT) ? 1 : 0;
  tzMinutes = prefs.getInt("tz", TZ_MINUTES_DEFAULT);
}

static void saveMode() { prefs.putUChar("mode", triggerMode); }

static void logAppend(const uint8_t* rec) {
  if (!flashOk) return;
  File f = LittleFS.open(QUEUE_LOG_PATH, FILE_APPEND);
  if (!f) { Serial.println("[FILA] erro ao gravar o diario"); return; }
  f.write(rec, plog::REC_SIZE);
  f.close();
  logBytes += plog::REC_SIZE;
}

static void compactLog() {
  if (!flashOk) return;
  File t = LittleFS.open(QUEUE_LOG_TMP, FILE_WRITE);
  if (!t) return;
  plog::compact(queue, seqNo, [&](const uint8_t* p, size_t n) { t.write(p, n); });
  t.close();
  LittleFS.remove(QUEUE_LOG_PATH);
  LittleFS.rename(QUEUE_LOG_TMP, QUEUE_LOG_PATH);
  logBytes = plog::compactSize(queue);
}

// Mantem o diario pequeno: com a fila limitada a QUEUE_CAPACITY, o arquivo compactado nunca passa de
// (QUEUE_CAPACITY + 1) * 48 bytes, entao ele sempre cabe na RAM ao religar.
static void maybeCompact() {
  if (logBytes > LOG_COMPACT_BYTES) compactLog();
}

static void loadQueue() {
  flashOk = LittleFS.begin(true);  // formata na primeira vez
  if (!flashOk) { Serial.println("[FILA] LittleFS indisponivel: fila so em RAM"); return; }
  File f = LittleFS.open(QUEUE_LOG_PATH, FILE_READ);
  if (!f) return;
  size_t len = f.size();
  uint8_t* buf = (uint8_t*)malloc(len ? len : 1);
  if (buf == nullptr) { f.close(); Serial.println("[FILA] memoria insuficiente para o diario"); return; }
  size_t got = f.read(buf, len);
  f.close();
  plog::ReplayResult r = plog::replay(buf, got, queue);
  free(buf);
  logBytes = got;
  seqNo = r.maxSeq;
  Serial.printf("[FILA] %u leituras pendentes recuperadas (%u registros, seq %lu)%s\n",
                (unsigned)queue.size(), (unsigned)r.records, (unsigned long)seqNo,
                r.corrupted ? ", diario reparado" : "");
  if (r.corrupted || len > LOG_COMPACT_BYTES) compactLog();
}

// ---------------------------------------------------------------------------
// Cartao microSD: historico em CSV
// ---------------------------------------------------------------------------
static void sdMount() {
#if SD_ENABLED
  SPI.begin(PIN_SD_SCK, PIN_SD_MISO, PIN_SD_MOSI, PIN_SD_CS);
  if (SD.begin(PIN_SD_CS, SPI, SD_SPI_HZ) && SD.cardType() != CARD_NONE) {
    if (!SD.exists("/leituras")) SD.mkdir("/leituras");
    sdState = logic::SD_OK;
    Serial.printf("[SD] cartao montado, %llu MB\n", SD.cardSize() / (1024ULL * 1024ULL));
  } else {
    SD.end();
    sdState = logic::SD_NONE;
  }
  sdRetryAt = millis() + SD_RETRY_MS;
  screenDirty = true;
#endif
}

static void sdWrite(const QueuedReading& r, bool online) {
#if SD_ENABLED
  if (sdState != logic::SD_OK) return;
  char name[48], line[128];
  logic::csvFileName(r.epoch, tzMinutes, r.boot, name, sizeof(name));
  bool isNew = !SD.exists(name);
  File f = SD.open(name, FILE_APPEND);
  if (!f) {
    sdState = logic::SD_ERROR;
    sdRetryAt = millis() + SD_RETRY_MS;
    screenDirty = true;
    Serial.println("[SD] erro ao gravar: cartao removido ou cheio?");
    return;
  }
  if (isNew) f.print(logic::csvHeader());
  logic::csvLine(r.seq, r.epoch, tzMinutes, r.id, r.boot, r.uptimeMs, online, line, sizeof(line));
  f.print(line);
  f.close();
#else
  (void)r; (void)online;
#endif
}

static void serviceSd() {
#if SD_ENABLED
  if (sdState != logic::SD_OK && (int32_t)(millis() - sdRetryAt) >= 0) sdMount();
#endif
}

// ---------------------------------------------------------------------------
// Display
// ---------------------------------------------------------------------------
static void drawScreen() {
#if DISPLAY_ENABLED
  uint32_t now = millis();
  if (flashKind != logic::FL_NONE && (int32_t)(now - flashUntil) >= 0) { flashKind = logic::FL_NONE; screenDirty = true; }
  if (!screenDirty || (now - lastDrawMs) < DISPLAY_REFRESH_MS) return;
  lastDrawMs = now;
  screenDirty = false;

  logic::ScreenState s;
  s.connected = streaming;
  s.battPct = battMv ? logic::batteryPercent(battMv) : -1;
  s.mode = triggerMode;
  s.sd = sdState;
  s.lastId = lastId;
  s.sessionCount = sessionCount;
  s.localCount = localCount;
  s.pending = queue.size();
  s.flash = flashKind;
  logic::Screen o;
  logic::buildScreen(s, o);

  oled.clearBuffer();
  oled.setFont(u8g2_font_6x10_tf);
  oled.drawStr(0, 9, o.status);
  oled.drawHLine(0, 12, 128);
  oled.drawStr(0, 25, o.country);
  oled.setFont(u8g2_font_9x15B_tf);
  oled.drawStr(0, 44, o.national);
  oled.setFont(u8g2_font_6x10_tf);
  oled.drawHLine(0, 51, 128);
  if (flashKind == logic::FL_DUP || flashKind == logic::FL_STORED) {
    oled.drawBox(0, 53, 128, 11);
    oled.setDrawColor(0);
    oled.drawStr(2, 62, o.footer);
    oled.setDrawColor(1);
  } else {
    oled.drawStr(0, 62, o.footer);
  }
  oled.sendBuffer();
#endif
}

// ---------------------------------------------------------------------------
// BLE
// ---------------------------------------------------------------------------
static bool parseUint(const uint8_t* d, size_t len, size_t& i, uint32_t& out) {
  while (i < len && (d[i] < '0' || d[i] > '9')) { if (d[i] == '-') break; i++; }
  bool any = false;
  uint32_t v = 0;
  while (i < len && d[i] >= '0' && d[i] <= '9') { v = v * 10 + (d[i] - '0'); i++; any = true; }
  if (any) out = v;
  return any;
}

static bool parseInt(const uint8_t* d, size_t len, size_t& i, int32_t& out) {
  while (i < len && d[i] != '-' && (d[i] < '0' || d[i] > '9')) i++;
  bool neg = (i < len && d[i] == '-');
  if (neg) i++;
  uint32_t v = 0;
  if (!parseUint(d, len, i, v)) return false;
  out = neg ? -(int32_t)v : (int32_t)v;
  return true;
}

class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer*) override {
    portENTER_CRITICAL(&bleMux);
    bleConnected = true;
    connEvent = true;
    portEXIT_CRITICAL(&bleMux);
  }
  void onDisconnect(BLEServer*) override {
    portENTER_CRITICAL(&bleMux);
    bleConnected = false;
    portEXIT_CRITICAL(&bleMux);
    BLEDevice::startAdvertising();
  }
};

// Comandos do app (texto ASCII):
//   H            app pronto: (re)enviar a fila
//   A;<seq>      confirma as leituras ate seq
//   B            bip de teste
//   M0 / M1      modo continuo / gatilho
//   T;<epoch>;<fuso_min>  data/hora UNIX (s) e fuso do celular
//   C;<n>        contagem da sessao no app (para o display)
//   D            a ultima leitura ja estava na sessao (bip de repetido)
class ControlCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* c) override {
    uint8_t* d = c->getData();
    size_t len = c->getLength();
    if (d == nullptr || len == 0) return;
    size_t i = 1;
    switch (d[0]) {
      case 'H': helloEvent = true; break;
      case 'B': beepEvent = true; break;
      case 'D': dupEvent = true; break;
      case 'M': if (len >= 2) modeRequest = (d[1] == '1') ? 1 : 0; break;
      case 'A': {
        uint32_t v;
        if (parseUint(d, len, i, v)) {
          portENTER_CRITICAL(&bleMux);
          if (!ackEvent || (int32_t)(v - ackSeq) > 0) ackSeq = v;
          ackEvent = true;
          portEXIT_CRITICAL(&bleMux);
        }
        break;
      }
      case 'T': {
        uint32_t ep;
        int32_t tz = TZ_MINUTES_DEFAULT;
        if (parseUint(d, len, i, ep)) {
          parseInt(d, len, i, tz);
          portENTER_CRITICAL(&bleMux);
          timeEpoch = ep;
          timeTz = tz;
          timeEvent = true;
          portEXIT_CRITICAL(&bleMux);
        }
        break;
      }
      case 'C': {
        uint32_t n;
        if (parseUint(d, len, i, n)) sessionCount = (int32_t)n;
        break;
      }
      default: break;
    }
  }
};

static void setupBle() {
  uint64_t mac = ESP.getEfuseMac();
  snprintf(deviceName, sizeof(deviceName), "%s-%02X%02X", DEVICE_NAME_PREFIX,
           (unsigned)((mac >> 32) & 0xFF), (unsigned)((mac >> 40) & 0xFF));
  BLEDevice::init(deviceName);
  BLEDevice::setMTU(BLE_MTU);

  BLEServer* server = BLEDevice::createServer();
  server->setCallbacks(new ServerCallbacks());
  BLEService* svc = server->createService(UUID_SERVICE);

  chReading = svc->createCharacteristic(UUID_READING, BLECharacteristic::PROPERTY_NOTIFY);
  chReading->addDescriptor(new BLE2902());
  chStatus = svc->createCharacteristic(UUID_STATUS, BLECharacteristic::PROPERTY_NOTIFY);
  chStatus->addDescriptor(new BLE2902());
  BLECharacteristic* chControl = svc->createCharacteristic(
      UUID_CONTROL, BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR);
  chControl->setCallbacks(new ControlCallbacks());
  svc->start();

  // Anuncio com o UUID do servico; o nome vai na resposta ao scan (os dois juntos nao cabem em 31 bytes)
  BLEAdvertising* adv = BLEDevice::getAdvertising();
  BLEAdvertisementData advData;
  advData.setFlags(0x06);  // general discoverable, sem BR/EDR
  advData.setCompleteServices(BLEUUID(UUID_SERVICE));
  BLEAdvertisementData scanData;
  scanData.setName(deviceName);
  adv->setAdvertisementData(advData);
  adv->setScanResponseData(scanData);
  BLEDevice::startAdvertising();
  Serial.printf("[BLE] anunciando como %s\n", deviceName);
}

static void notifyText(BLECharacteristic* ch, const char* msg, int len) {
  if (!bleConnected || ch == nullptr || len <= 0) return;
  ch->setValue((uint8_t*)msg, (size_t)len);
  ch->notify();
}

// R;<seq>;<uptime_ms>;<id>;<idade_ms>;<epoch>
//   idade_ms = -1 quando a leitura e de uma ligacao anterior do bastao
//   epoch    = 0 quando a data/hora e desconhecida
static void sendReading(const QueuedReading& r) {
  char msg[128];
  long age = (r.boot == bootNo) ? (long)(millis() - r.uptimeMs) : -1L;
  int len = snprintf(msg, sizeof(msg), "R;%lu;%lu;%s;%ld;%lu", (unsigned long)r.seq,
                     (unsigned long)r.uptimeMs, r.id, age, (unsigned long)r.epoch);
  notifyText(chReading, msg, len);
}

// S;<bateria_mV>;<modo>;<versao_fw>;<pendentes>;<descartadas>;<bateria_%>;<sd>;<ligacao>
static void sendStatus() {
  char msg[96];
  int len = snprintf(msg, sizeof(msg), "S;%lu;%u;%s;%u;%lu;%d;%u;%u", (unsigned long)battMv,
                     (unsigned)triggerMode, FW_VERSION, (unsigned)queue.size(),
                     (unsigned long)queue.dropped(), logic::batteryPercent(battMv),
                     (unsigned)sdState, (unsigned)bootNo);
  notifyText(chStatus, msg, len);
}

static void setMode(uint8_t m, bool announce) {
  if (m == triggerMode) return;
  triggerMode = m;
  saveMode();
  frameLen = 0;
  if (announce) { play(PAT_MODE, false); flash(logic::FL_MODE); }
  screenDirty = true;
  if (streaming) sendStatus();
  Serial.printf("[MODO] %s\n", triggerMode ? "gatilho" : "continuo");
}

static void serviceBle() {
  bool conn, gotConn, gotHello, gotAck, gotTime;
  uint32_t ack, ep;
  int32_t tz;
  portENTER_CRITICAL(&bleMux);
  conn = bleConnected;
  gotConn = connEvent;   connEvent = false;
  gotHello = helloEvent; helloEvent = false;
  gotAck = ackEvent;     ackEvent = false;
  ack = ackSeq;
  gotTime = timeEvent;   timeEvent = false;
  ep = timeEpoch;
  tz = timeTz;
  portEXIT_CRITICAL(&bleMux);

  uint32_t now = millis();

  if (beepEvent) { beepEvent = false; play(PAT_OK); }
  if (dupEvent) { dupEvent = false; play(PAT_DUP); flash(logic::FL_DUP); }
  if (modeRequest >= 0) { int m = modeRequest; modeRequest = -1; setMode((uint8_t)m, true); }

  if (gotTime) {
    epochAtBoot = ep - now / 1000;
    if (tz != tzMinutes) { tzMinutes = tz; prefs.putInt("tz", tzMinutes); }
    size_t n = queue.fillEpoch(bootNo, epochAtBoot);
    Serial.printf("[HORA] recebida do celular (fuso %ld min); %u leituras datadas\n", (long)tz, (unsigned)n);
  }

  if (gotConn) {
    streaming = false;
    connectedAtMs = now;
    sessionCount = -1;
    Serial.println("[BLE] conectado");
  }
  if (!conn) {
    if (streaming) { Serial.println("[BLE] desconectado"); screenDirty = true; }
    streaming = false;
    return;
  }

  if (gotAck) {
    size_t n = queue.ack(ack);
    if (n) {
      uint8_t rec[plog::REC_SIZE];
      plog::encodeAck(ack, rec);
      logAppend(rec);
      if (queue.size() == 0) compactLog(); else maybeCompact();
      screenDirty = true;
    }
  }

  if (!streaming && (gotHello || (now - connectedAtMs) >= HELLO_TIMEOUT_MS)) {
    queue.rewind();
    streaming = true;
    screenDirty = true;
    sendStatus();
    if (queue.size()) Serial.printf("[FILA] reenviando %u leituras\n", (unsigned)queue.size());
  } else if (streaming && gotHello) {
    queue.rewind();
  }

  if (streaming && (now - lastNotifyMs) >= NOTIFY_GAP_MS) {
    const QueuedReading* r = queue.nextToSend();
    if (r != nullptr) {
      sendReading(*r);
      queue.markSent();
      lastNotifyMs = now;
    }
  }
}

// ---------------------------------------------------------------------------
// Leitura do modulo RFID
// ---------------------------------------------------------------------------
static void handleFrame(const uint8_t* f, size_t n) {
#if DEBUG_RAW
  Serial.printf("[RFID] %u bytes:", (unsigned)n);
  for (size_t i = 0; i < n; i++) Serial.printf(" %02X", f[i]);
  Serial.print("  | ");
  for (size_t i = 0; i < n; i++) Serial.print((f[i] >= 32 && f[i] < 127) ? (char)f[i] : '.');
  Serial.println();
#endif

  char id[FRAME_MAX * 2 + 1];
  tagparser::Result res = tagparser::parse((tagparser::Mode)PARSER_MODE, f, n, id, sizeof(id),
                                           STRICT_CHECKSUM != 0);
  if (res != tagparser::OK) {
    Serial.printf("[RFID] frame descartado: %s\n", tagparser::resultName(res));
    return;
  }
  id[READING_ID_MAX - 1] = '\0';

  uint32_t now = millis();
  if (strcmp(id, lastId) == 0 && (now - lastIdMs) < DEDUP_MS) {
    lastIdMs = now;  // janela deslizante: enquanto o brinco estiver no campo, nao repete
    return;
  }
  strncpy(lastId, id, sizeof(lastId) - 1);
  lastId[sizeof(lastId) - 1] = '\0';
  lastIdMs = now;

  seqNo++;
  localCount++;
  uint32_t epoch = epochAtBoot ? epochAtBoot + now / 1000 : 0;
  bool kept = queue.push(seqNo, bootNo, now, epoch, id);
  const QueuedReading& r = queue.at(queue.size() - 1);

  uint8_t rec[plog::REC_SIZE];
  plog::encodePush(r, rec);
  logAppend(rec);
  maybeCompact();
  sdWrite(r, streaming);

  Serial.printf("R;%lu;%lu;%s%s\n", (unsigned long)seqNo, (unsigned long)now, id,
                kept ? "" : "  (fila cheia: a mais antiga foi descartada)");
  if (streaming) { play(PAT_OK); flash(logic::FL_NEW); }
  else { play(PAT_STORED); flash(logic::FL_STORED); }
  screenDirty = true;
}

static void pollRfid() {
  while (RfidSerial.available()) {
    int b = RfidSerial.read();
    if (b < 0) break;
    if (frameLen < sizeof(frameBuf)) frameBuf[frameLen++] = (uint8_t)b;
    lastByteMs = millis();
    if (tagparser::endsWithWl134(frameBuf, frameLen)) {
      handleFrame(frameBuf, frameLen);
      frameLen = 0;
    } else if (frameLen == sizeof(frameBuf)) {
      handleFrame(frameBuf, frameLen);
      frameLen = 0;
    }
  }
  if (frameLen > 0 && (millis() - lastByteMs) >= FRAME_GAP_MS) {
    handleFrame(frameBuf, frameLen);
    frameLen = 0;
  }
}

static void serviceTrigger() {
  logic::TriggerEvent ev = trigger.update(digitalRead(PIN_TRIGGER) == LOW, millis());
  if (ev == logic::EV_LONG_PRESS) setMode(triggerMode ? 0 : 1, true);
}

static void serviceStatus() {
  uint32_t now = millis();
  if (lastStatusMs != 0 && now - lastStatusMs < STATUS_PERIOD_MS) return;
  lastStatusMs = now;
  battMv = (uint32_t)analogReadMilliVolts(PIN_BATT_ADC) * BATT_DIV_FACTOR;
  screenDirty = true;
  if (streaming) sendStatus();
}

// ---------------------------------------------------------------------------
void setup() {
  Serial.begin(115200);
  delay(100);

  pinMode(PIN_TRIGGER, INPUT_PULLUP);
  pinMode(PIN_BUZZER, OUTPUT);
  pinMode(PIN_VIBRA, OUTPUT);
  pinMode(PIN_LED, OUTPUT);
  outputs(false);

  loadSettings();
  Serial.printf("\nBastao Aggilli fw %s | ligacao %u | parser %d | fila %u\n", FW_VERSION,
                (unsigned)bootNo, PARSER_MODE, (unsigned)QUEUE_CAPACITY);

#if DISPLAY_ENABLED
  Wire.begin(PIN_SDA, PIN_SCL);
  oled.begin();
  oled.setContrast(255);
#endif

  loadQueue();
  sdMount();
  RfidSerial.begin(RFID_BAUD, SERIAL_8N1, RFID_RX_PIN, RFID_TX_PIN);
  setupBle();

  play(PAT_BOOT);
  serviceStatus();
}

void loop() {
  servicePattern();
  serviceTrigger();

  if (triggerMode == 1 && !trigger.pressed()) {
    while (RfidSerial.available()) RfidSerial.read();  // descarta enquanto o gatilho esta solto
    frameLen = 0;
  } else {
    pollRfid();
  }

  serviceBle();
  serviceStatus();
  serviceSd();
  drawScreen();
  delay(1);
}
