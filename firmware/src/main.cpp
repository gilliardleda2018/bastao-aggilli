// Bastao de coleta RFID - Aggilli
// Le brincos FDX-B por um modulo UART e envia os IDs ao app Android por BLE.
// v0.2: parser WL134 com checksum, fila offline com confirmacao (ACK) do app.

#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include "config.h"
#include "tag_parser.h"
#include "reading_queue.h"

static HardwareSerial RfidSerial(RFID_UART_NUM);

static BLECharacteristic* chReading = nullptr;
static BLECharacteristic* chStatus  = nullptr;

// Estado compartilhado com os callbacks do BLE (rodam em outra tarefa do FreeRTOS)
static portMUX_TYPE bleMux = portMUX_INITIALIZER_UNLOCKED;
static volatile bool     bleConnected = false;
static volatile bool     connEvent    = false;  // houve conexao nova
static volatile bool     helloEvent   = false;  // app pediu o reenvio ("H")
static volatile bool     ackEvent     = false;
static volatile uint32_t ackSeq       = 0;
static volatile bool     beepEvent    = false;
static volatile uint8_t  triggerMode  = TRIGGER_MODE;

static bool     streaming = false;   // conexao pronta para receber leituras
static uint32_t connectedAtMs = 0;
static uint32_t lastNotifyMs = 0;

static ReadingQueue<QUEUE_CAPACITY> queue;
static uint32_t seqNo = 0;

static uint8_t  frameBuf[FRAME_MAX];
static size_t   frameLen = 0;
static uint32_t lastByteMs = 0;

static char     lastId[64] = {0};
static uint32_t lastIdMs = 0;

static uint32_t buzzUntil = 0, ledUntil = 0, vibUntil = 0;
static uint32_t lastStatusMs = 0;

// ---------------------------------------------------------------------------
// Saidas (buzzer, LED, vibracao) sem bloquear o loop
// ---------------------------------------------------------------------------
static void pulse(uint32_t &until, uint8_t pin, uint32_t ms) {
  digitalWrite(pin, HIGH);
  until = millis() + ms;
}

// Leitura nova com o celular conectado: bip curto
static void feedbackOk() {
  pulse(buzzUntil, PIN_BUZZER, 80);
  pulse(ledUntil,  PIN_LED,    150);
  pulse(vibUntil,  PIN_VIBRA,  60);
}

// Leitura guardada na fila (celular desconectado): bip longo, o operador percebe
static void feedbackStored() {
  pulse(buzzUntil, PIN_BUZZER, 350);
  pulse(ledUntil,  PIN_LED,    600);
  pulse(vibUntil,  PIN_VIBRA,  200);
}

static void serviceOutputs() {
  uint32_t now = millis();
  if (buzzUntil && (int32_t)(now - buzzUntil) >= 0) { digitalWrite(PIN_BUZZER, LOW); buzzUntil = 0; }
  if (ledUntil  && (int32_t)(now - ledUntil)  >= 0) { digitalWrite(PIN_LED,    LOW); ledUntil  = 0; }
  if (vibUntil  && (int32_t)(now - vibUntil)  >= 0) { digitalWrite(PIN_VIBRA,  LOW); vibUntil  = 0; }
}

// ---------------------------------------------------------------------------
// BLE
// ---------------------------------------------------------------------------
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

class ControlCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* c) override {
    uint8_t* data = c->getData();
    size_t len = c->getLength();
    if (data == nullptr || len == 0) return;
    switch (data[0]) {
      case 'B':
        beepEvent = true;
        break;
      case 'M':
        if (len >= 2) triggerMode = (data[1] == '1') ? 1 : 0;
        break;
      case 'H':  // app pronto (notificacoes ativas): reenvia o que nao foi confirmado
        helloEvent = true;
        break;
      case 'A': {  // "A;<seq>": confirma todas as leituras ate seq
        uint32_t v = 0;
        bool any = false;
        for (size_t i = 1; i < len; i++) {
          if (data[i] >= '0' && data[i] <= '9') { v = v * 10 + (data[i] - '0'); any = true; }
          else if (any) break;
        }
        if (any) {
          portENTER_CRITICAL(&bleMux);
          if (!ackEvent || (int32_t)(v - ackSeq) > 0) ackSeq = v;
          ackEvent = true;
          portEXIT_CRITICAL(&bleMux);
        }
        break;
      }
      default:
        break;
    }
  }
};

static void setupBle() {
  BLEDevice::init(DEVICE_NAME);
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

  BLEAdvertising* adv = BLEDevice::getAdvertising();
  adv->addServiceUUID(UUID_SERVICE);
  adv->setScanResponse(true);
  BLEDevice::startAdvertising();
}

static void notifyText(BLECharacteristic* ch, const char* msg, int len) {
  if (!bleConnected || ch == nullptr || len <= 0) return;
  ch->setValue((uint8_t*)msg, (size_t)len);
  ch->notify();
}

// R;<seq>;<uptime_ms>;<id>;<idade_ms>
static void sendReading(const QueuedReading& r) {
  char msg[128];
  uint32_t age = millis() - r.uptimeMs;
  int len = snprintf(msg, sizeof(msg), "R;%lu;%lu;%s;%lu",
                     (unsigned long)r.seq, (unsigned long)r.uptimeMs, r.id, (unsigned long)age);
  notifyText(chReading, msg, len);
}

// S;<bateria_mV>;<modo_gatilho>;<versao_fw>;<pendentes>;<descartadas>
static void sendStatus() {
  uint32_t mv = (uint32_t)analogReadMilliVolts(PIN_BATT_ADC) * BATT_DIV_FACTOR;
  char msg[64];
  int len = snprintf(msg, sizeof(msg), "S;%lu;%u;%s;%u;%lu",
                     (unsigned long)mv, (unsigned)triggerMode, FW_VERSION,
                     (unsigned)queue.size(), (unsigned long)queue.dropped());
  notifyText(chStatus, msg, len);
}

// Trata os eventos vindos dos callbacks e envia a fila, uma notificacao por vez.
static void serviceBle() {
  bool conn, gotConn, gotHello, gotAck;
  uint32_t ack;
  portENTER_CRITICAL(&bleMux);
  conn = bleConnected;
  gotConn = connEvent;   connEvent = false;
  gotHello = helloEvent; helloEvent = false;
  gotAck = ackEvent;     ackEvent = false;
  ack = ackSeq;
  portEXIT_CRITICAL(&bleMux);

  uint32_t now = millis();

  if (beepEvent) { beepEvent = false; feedbackOk(); }

  if (gotConn) {
    streaming = false;
    connectedAtMs = now;
    Serial.println("[BLE] conectado");
  }
  if (!conn) {
    if (streaming) Serial.println("[BLE] desconectado");
    streaming = false;
    return;
  }

  if (gotAck) {
    size_t n = queue.ack(ack);
    if (DEBUG_RAW && n) Serial.printf("[FILA] ACK %lu: %u confirmadas, %u na fila\n",
                                      (unsigned long)ack, (unsigned)n, (unsigned)queue.size());
  }

  // Comeca a enviar quando o app avisa (H) ou, por compatibilidade, apos um tempo
  if (!streaming && (gotHello || (now - connectedAtMs) >= HELLO_TIMEOUT_MS)) {
    queue.rewind();
    streaming = true;
    sendStatus();
    if (queue.size()) Serial.printf("[FILA] reenviando %u leituras\n", (unsigned)queue.size());
  } else if (streaming && gotHello) {
    queue.rewind();  // app reiniciou a sessao BLE: reenvia o que falta
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
  tagparser::Result r = tagparser::parse((tagparser::Mode)PARSER_MODE, f, n, id, sizeof(id),
                                         STRICT_CHECKSUM != 0);
  if (r != tagparser::OK) {
    Serial.printf("[RFID] frame descartado: %s\n", tagparser::resultName(r));
    return;
  }

  uint32_t now = millis();
  if (strcmp(id, lastId) == 0 && (now - lastIdMs) < DEDUP_MS) {
    lastIdMs = now;  // janela deslizante: enquanto o brinco estiver no campo, nao repete
    return;
  }
  strncpy(lastId, id, sizeof(lastId) - 1);
  lastId[sizeof(lastId) - 1] = '\0';
  lastIdMs = now;

  seqNo++;
  bool kept = queue.push(seqNo, now, id);
  Serial.printf("R;%lu;%lu;%s%s\n", (unsigned long)seqNo, (unsigned long)now, id,
                kept ? "" : "  (fila cheia: a mais antiga foi descartada)");
  if (streaming) feedbackOk(); else feedbackStored();
}

static void pollRfid() {
  while (RfidSerial.available()) {
    int b = RfidSerial.read();
    if (b < 0) break;
    if (frameLen < sizeof(frameBuf)) frameBuf[frameLen++] = (uint8_t)b;
    lastByteMs = millis();

    // Pacote WL134 completo: processa ja (modulos que repetem sem pausa nunca dariam o silencio)
    if (tagparser::endsWithWl134(frameBuf, frameLen)) {
      handleFrame(frameBuf, frameLen);
      frameLen = 0;
    } else if (frameLen == sizeof(frameBuf)) {
      handleFrame(frameBuf, frameLen);  // buffer cheio: processa e recomeca
      frameLen = 0;
    }
  }
  if (frameLen > 0 && (millis() - lastByteMs) >= FRAME_GAP_MS) {
    handleFrame(frameBuf, frameLen);
    frameLen = 0;
  }
}

static bool triggerPressed() { return digitalRead(PIN_TRIGGER) == LOW; }

// ---------------------------------------------------------------------------
void setup() {
  Serial.begin(115200);
  delay(100);
  Serial.printf("\nBastao Aggilli fw %s (parser %d, fila %u)\n", FW_VERSION, PARSER_MODE,
                (unsigned)QUEUE_CAPACITY);

  pinMode(PIN_TRIGGER, INPUT_PULLUP);
  pinMode(PIN_BUZZER, OUTPUT);
  pinMode(PIN_VIBRA, OUTPUT);
  pinMode(PIN_LED, OUTPUT);
  digitalWrite(PIN_BUZZER, LOW);
  digitalWrite(PIN_VIBRA, LOW);
  digitalWrite(PIN_LED, LOW);

  RfidSerial.begin(RFID_BAUD, SERIAL_8N1, RFID_RX_PIN, RFID_TX_PIN);
  setupBle();

  feedbackOk();  // bip de inicializacao
}

void loop() {
  serviceOutputs();

  if (triggerMode == 1 && !triggerPressed()) {
    while (RfidSerial.available()) RfidSerial.read();  // descarta enquanto o gatilho esta solto
    frameLen = 0;
  } else {
    pollRfid();
  }

  serviceBle();

  uint32_t now = millis();
  if (now - lastStatusMs >= STATUS_PERIOD_MS) {
    lastStatusMs = now;
    if (streaming) sendStatus();
  }

  delay(1);
}
