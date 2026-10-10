// Bastao de coleta RFID - Aggilli
// Le brincos FDX-B por um modulo UART e envia os IDs ao app Android por BLE.

#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include "config.h"

static HardwareSerial RfidSerial(RFID_UART_NUM);

static BLECharacteristic* chReading = nullptr;
static BLECharacteristic* chStatus  = nullptr;
static volatile bool bleConnected   = false;

static uint32_t seqNo = 0;
static uint8_t  frameBuf[FRAME_MAX];
static size_t   frameLen = 0;
static uint32_t lastByteMs = 0;

static char     lastId[FRAME_MAX * 2 + 1] = {0};
static uint32_t lastIdMs = 0;

static uint8_t  triggerMode = TRIGGER_MODE;

static uint32_t buzzUntil = 0, ledUntil = 0, vibUntil = 0;
static uint32_t lastStatusMs = 0;

// ---------------------------------------------------------------------------
// Saidas (buzzer, LED, vibracao) sem bloquear o loop
// ---------------------------------------------------------------------------
static void pulse(uint32_t &until, uint8_t pin, uint32_t ms) {
  digitalWrite(pin, HIGH);
  until = millis() + ms;
}

static void feedbackOk() {
  pulse(buzzUntil, PIN_BUZZER, 80);
  pulse(ledUntil,  PIN_LED,    150);
  pulse(vibUntil,  PIN_VIBRA,  60);
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
  void onConnect(BLEServer*) override { bleConnected = true; Serial.println("[BLE] conectado"); }
  void onDisconnect(BLEServer*) override {
    bleConnected = false;
    Serial.println("[BLE] desconectado");
    BLEDevice::startAdvertising();
  }
};

class ControlCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* c) override {
    uint8_t* data = c->getData();
    size_t len = c->getLength();
    if (data == nullptr || len == 0) return;
    switch (data[0]) {
      case 'B': feedbackOk(); break;
      case 'M': if (len >= 2) triggerMode = (data[1] == '1') ? 1 : 0; break;
      default: break;
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
  if (!bleConnected || ch == nullptr) return;
  ch->setValue((uint8_t*)msg, (size_t)len);
  ch->notify();
}

static void sendReading(const char* id) {
  char msg[128];
  int len = snprintf(msg, sizeof(msg), "R;%lu;%lu;%s",
                     (unsigned long)seqNo, (unsigned long)millis(), id);
  Serial.println(msg);
  notifyText(chReading, msg, len);
}

static void sendStatus() {
  uint32_t mv = (uint32_t)analogReadMilliVolts(PIN_BATT_ADC) * BATT_DIV_FACTOR;
  char msg[48];
  int len = snprintf(msg, sizeof(msg), "S;%lu;%u;%s",
                     (unsigned long)mv, (unsigned)triggerMode, FW_VERSION);
  notifyText(chStatus, msg, len);
}

// ---------------------------------------------------------------------------
// Leitura do modulo RFID
// ---------------------------------------------------------------------------

// Converte um frame do modulo em um ID de texto.
// IMPORTANTE: o formato do frame depende do modulo. Valide em bancada com
// tools/serial_hexdump.py e ajuste esta funcao conforme o datasheet.
static bool parseFrame(const uint8_t* f, size_t n, char* out, size_t outSize) {
#if PARSER_MODE == 0
  (void)outSize;
  char digits[16];
  size_t k = 0;
  for (size_t i = 0; i < n && k < 15; i++) {
    if (f[i] >= '0' && f[i] <= '9') digits[k++] = (char)f[i];
  }
  if (k < 15) return false;
  memcpy(out, digits, 15);
  out[15] = '\0';
  return true;
#else
  if (outSize < n * 2 + 1) return false;
  for (size_t i = 0; i < n; i++) sprintf(out + i * 2, "%02X", f[i]);
  return true;
#endif
}

static void handleFrame(const uint8_t* f, size_t n) {
#if DEBUG_RAW
  Serial.printf("[RFID] %u bytes:", (unsigned)n);
  for (size_t i = 0; i < n; i++) Serial.printf(" %02X", f[i]);
  Serial.print("  | ");
  for (size_t i = 0; i < n; i++) Serial.print((f[i] >= 32 && f[i] < 127) ? (char)f[i] : '.');
  Serial.println();
#endif

  char id[FRAME_MAX * 2 + 1];
  if (!parseFrame(f, n, id, sizeof(id))) {
    Serial.println("[RFID] frame descartado pelo parser");
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
  feedbackOk();
  sendReading(id);
}

static void pollRfid() {
  while (RfidSerial.available()) {
    int b = RfidSerial.read();
    if (b < 0) break;
    if (frameLen < sizeof(frameBuf)) frameBuf[frameLen++] = (uint8_t)b;
    lastByteMs = millis();
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
  Serial.printf("\nBastao Aggilli fw %s\n", FW_VERSION);

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

  uint32_t now = millis();
  if (now - lastStatusMs >= STATUS_PERIOD_MS) {
    lastStatusMs = now;
    sendStatus();
  }

  delay(1);
}
