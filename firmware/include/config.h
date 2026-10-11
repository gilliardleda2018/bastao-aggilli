#pragma once
#include <Arduino.h>

// ---------------------------------------------------------------------------
// Identificacao
// ---------------------------------------------------------------------------
#define FW_VERSION   "0.2.0"
#define DEVICE_NAME  "Bastao-Aggilli"

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
#define RFID_BAUD      9600    // CONFIRMAR no datasheet do modulo

#define FRAME_GAP_MS   40      // silencio na UART que encerra um frame
#define FRAME_MAX      64      // tamanho maximo de um frame, em bytes

// Formato do frame do modulo (ver lib/bastao_core/tag_parser.h):
// 0 = ASCII15  -> ID ja em decimal ASCII ("982000123456789")
// 1 = RAW_HEX  -> envia o frame bruto em hexadecimal (diagnostico de bancada)
// 2 = WL134    -> pacote binario de 30 bytes (STX, ID hex invertido, checksum, ETX)
// 3 = AUTO     -> tenta WL134 e depois ASCII15
#define PARSER_MODE    3
// 1 = descarta pacote WL134 com checksum errado (recomendado: evita ID trocado no rebanho)
#define STRICT_CHECKSUM 1

// ---------------------------------------------------------------------------
// Comportamento
// ---------------------------------------------------------------------------
#define TRIGGER_MODE   0       // 0 = le sempre, 1 = so com o gatilho apertado
#define DEDUP_MS       3000    // janela deslizante anti-duplicata
#define STATUS_PERIOD_MS 5000

// ---------------------------------------------------------------------------
// Fila offline (leituras feitas com o celular desconectado)
// ---------------------------------------------------------------------------
#define QUEUE_CAPACITY   500   // leituras guardadas em RAM ate o app confirmar (~36 KB)
#define NOTIFY_GAP_MS    15    // intervalo minimo entre notificacoes BLE (reenvio em lote)
#define HELLO_TIMEOUT_MS 2000  // sem "H" do app apos conectar: comeca a enviar assim mesmo
#define DEBUG_RAW      1       // imprime frames brutos na serial USB (115200)

// ---------------------------------------------------------------------------
// Pinos (veja hardware/pinout.csv)
// ---------------------------------------------------------------------------
#define PIN_TRIGGER    4
#define PIN_BUZZER     25
#define PIN_VIBRA      26
#define PIN_LED        2
#define PIN_BATT_ADC   34
#define BATT_DIV_FACTOR 2      // divisor 100k/100k
