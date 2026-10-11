#pragma once
#include <Arduino.h>

// ---------------------------------------------------------------------------
// Identificacao
// ---------------------------------------------------------------------------
#define FW_VERSION   "0.3.0"
// O nome anunciado no Bluetooth ganha os 4 ultimos digitos do MAC: "Bastao-Aggilli-A1B2".
// Assim o app distingue varios bastoes na mesma fazenda.
#define DEVICE_NAME_PREFIX "Bastao-Aggilli"

// ---------------------------------------------------------------------------
// BLE (mesmos UUIDs de android/.../ble/BleUuids.kt)
// ---------------------------------------------------------------------------
#define UUID_SERVICE  "a91c0001-5f3e-4b8a-9d27-3c6e1b7f0a10"
#define UUID_READING  "a91c0002-5f3e-4b8a-9d27-3c6e1b7f0a10"
#define UUID_STATUS   "a91c0003-5f3e-4b8a-9d27-3c6e1b7f0a10"
#define UUID_CONTROL  "a91c0004-5f3e-4b8a-9d27-3c6e1b7f0a10"
#define BLE_MTU       185

// ---------------------------------------------------------------------------
// Modulo leitor RFID (UART2)
// ---------------------------------------------------------------------------
#define RFID_UART_NUM  2
#define RFID_RX_PIN    16      // TX do modulo -> GPIO16
#define RFID_TX_PIN    17      // RX do modulo -> GPIO17 (opcional)
#define RFID_BAUD      9600    // WL-134: 9600 baud (8N2 no envio; recebe bem como 8N1)

#define FRAME_GAP_MS   40      // silencio na UART que encerra um frame
#define FRAME_MAX      64      // tamanho maximo de um frame, em bytes

// Formato do frame do modulo (ver lib/bastao_core/tag_parser.h):
// 0 = ASCII15, 1 = RAW_HEX (diagnostico), 2 = WL134, 3 = AUTO (WL134 e depois ASCII15)
#define PARSER_MODE    3
// 1 = descarta pacote WL134 com checksum errado (recomendado: evita ID trocado no rebanho)
#define STRICT_CHECKSUM 1

// ---------------------------------------------------------------------------
// Comportamento
// ---------------------------------------------------------------------------
#define TRIGGER_MODE_DEFAULT 0 // 0 = le sempre, 1 = so com o gatilho apertado (salvo na memoria)
#define DEDUP_MS       3000    // janela deslizante anti-duplicata
#define STATUS_PERIOD_MS 5000
#define DEBOUNCE_MS    30      // filtro de ruido do gatilho
#define LONG_PRESS_MS  1500    // toque longo no gatilho troca o modo (continuo <-> gatilho)
#define FLASH_MS       1500    // tempo dos avisos no display (REPETIDO, GUARDADO, MODO)
#define TZ_MINUTES_DEFAULT (-180) // fuso para o cartao SD ate o celular informar (-03:00)
#define DEBUG_RAW      1       // imprime frames brutos na serial USB (115200)

// ---------------------------------------------------------------------------
// Fila offline (leituras feitas com o celular desconectado)
// ---------------------------------------------------------------------------
#define QUEUE_CAPACITY   500   // leituras guardadas ate o app confirmar (~20 KB de RAM)
#define NOTIFY_GAP_MS    15    // intervalo minimo entre notificacoes BLE (reenvio em lote)
#define HELLO_TIMEOUT_MS 2000  // sem "H" do app apos conectar: comeca a enviar assim mesmo
#define QUEUE_LOG_PATH   "/fila.log"     // diario da fila na memoria flash interna (LittleFS)
#define QUEUE_LOG_TMP    "/fila.tmp"
#define LOG_COMPACT_BYTES 32768          // acima disso o diario e reescrito so com a fila atual

// ---------------------------------------------------------------------------
// Display OLED 1,3" SH1106 (I2C)
// ---------------------------------------------------------------------------
#define DISPLAY_ENABLED 1
#define PIN_SDA        21
#define PIN_SCL        22
#define DISPLAY_REFRESH_MS 150

// ---------------------------------------------------------------------------
// Cartao microSD (SPI): historico de todas as leituras em CSV, um arquivo por dia
// ---------------------------------------------------------------------------
#define SD_ENABLED     1
#define PIN_SD_CS      5
#define PIN_SD_SCK     18
#define PIN_SD_MISO    19
#define PIN_SD_MOSI    23
#define SD_SPI_HZ      4000000
#define SD_RETRY_MS    10000   // cartao ausente ou com erro: tenta montar de novo a cada 10 s

// ---------------------------------------------------------------------------
// Pinos (veja hardware/pinout.csv)
// ---------------------------------------------------------------------------
#define PIN_TRIGGER    4
#define PIN_BUZZER     25
#define PIN_VIBRA      26
#define PIN_LED        2
#define PIN_BATT_ADC   34
#define BATT_DIV_FACTOR 2      // divisor 100k/100k
