#pragma once
#include <Arduino.h>

// ---------------------------------------------------------------------------
// Identificacao
// ---------------------------------------------------------------------------
#define FW_VERSION   "0.1.0"
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
#define FRAME_MAX      40      // tamanho maximo de um frame, em bytes

// 0 = extrai os 15 primeiros digitos ASCII do frame (ID ISO 11784)
// 1 = envia o frame bruto em hexadecimal (use enquanto o formato nao estiver validado)
#define PARSER_MODE    0

// ---------------------------------------------------------------------------
// Comportamento
// ---------------------------------------------------------------------------
#define TRIGGER_MODE   0       // 0 = le sempre, 1 = so com o gatilho apertado
#define DEDUP_MS       3000    // janela deslizante anti-duplicata
#define STATUS_PERIOD_MS 5000
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
