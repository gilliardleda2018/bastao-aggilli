# Firmware do bastão (ESP32)

Framework Arduino com PlatformIO. Lê o módulo FDX-B pela UART2, confere e remove repetições, avisa o
operador (bip, vibração, LED, display), guarda a fila em flash e o histórico no microSD, e envia ao
app por BLE.

## Estrutura

| Arquivo | Função |
|---|---|
| `src/main.cpp` | Integração com o hardware: UART, BLE, LittleFS, SD, display, gatilho, sinais |
| `include/config.h` | Pinos, tempos, modos, UUIDs: tudo o que se ajusta |
| `lib/bastao_core/tag_parser.*` | Parser dos frames (WL-134 com checksum, ASCII, AUTO, RAW) |
| `lib/bastao_core/reading_queue.h` | Fila de leituras não confirmadas |
| `lib/bastao_core/persist_log.h` | Diário da fila em flash (registro, replay, compactação) |
| `lib/bastao_core/bastao_logic.h` | Gatilho, bateria, datas, CSV do SD, conteúdo do display |
| `test_host/` | Testes no computador (g++ com AddressSanitizer) |

Tudo em `lib/bastao_core` é C++ puro, sem Arduino, e é testado no PC.

## Requisitos

- [PlatformIO](https://platformio.org/) (extensão do VS Code ou CLI)
- ESP32 DevKit (ESP32-WROOM-32). A primeira compilação baixa a biblioteca U8g2 sozinha.

## Compilar, gravar e testar

```bash
cd firmware
pio run                 # compila
pio run -t upload       # grava (segure BOOT se não entrar em gravação)
pio device monitor      # serial a 115200 baud

cd test_host && make    # testes da lógica no computador
```

Se a gravação falhar com o módulo SD ligado, desconecte o SD durante a gravação (o GPIO5 é pino de
inicialização).

## Configuração (`include/config.h`)

| Parâmetro | Padrão | Quando mudar |
|---|---|---|
| `DEVICE_NAME_PREFIX` | `Bastao-Aggilli` | O nome final ganha `-XXXX` do MAC |
| `RFID_BAUD` | 9600 | Só se o leitor usar outra taxa |
| `PARSER_MODE` | 3 (AUTO) | 1 (RAW_HEX) para diagnosticar um leitor desconhecido |
| `STRICT_CHECKSUM` | 1 | Nunca desligar em campo |
| `TRIGGER_MODE_DEFAULT` | 0 | Modo da primeira vez; depois vale o salvo na memória |
| `DEDUP_MS` | 3000 | Janela anti-duplicata do firmware |
| `LONG_PRESS_MS` | 1500 | Toque longo que troca o modo |
| `TZ_MINUTES_DEFAULT` | -180 | Fuso até o celular informar o dele |
| `QUEUE_CAPACITY` | 500 | Leituras pendentes guardadas (~20 KB de RAM) |
| `LOG_COMPACT_BYTES` | 32768 | Tamanho que dispara a compactação do diário |
| `DISPLAY_ENABLED` | 1 | 0 para montar sem display |
| `SD_ENABLED` | 1 | 0 para montar sem cartão |
| `DEBUG_RAW` | 1 | Imprime cada frame bruto na serial |

## Parser do frame

Modos: `0` ASCII15, `1` RAW_HEX, `2` WL134, `3` AUTO (padrão). O pacote WL-134 tem 30 bytes:
STX, ID (10 hex, dígito menos significativo primeiro), país (4 hex), flags, reservados, XOR,
~XOR, ETX (0x03 ou 0x07). Layout conferido com a biblioteca Rfid134 (Makuna) e o componente WL-134
do ESPHome.

## Memória

| Onde | O quê | Formato |
|---|---|---|
| NVS (Preferences, `bastao`) | Número da ligação, modo, fuso | chaves `boot`, `mode`, `tz` |
| LittleFS `/fila.log` | Diário da fila pendente | registros de 48 bytes com CRC-8 (`persist_log.h`) |
| microSD `/leituras/*.csv` | Histórico completo | `seq;data_hora;brinco;ligacao;uptime_ms;origem` |

Para apagar a fila de um bastão de testes: `pio run -t erase` (apaga também a configuração).

## Serial (115200)

```
Bastao Aggilli fw 0.3.0 | ligacao 42 | parser 3 | fila 500
[FILA] 3 leituras pendentes recuperadas (5 registros, seq 210)
[SD] cartao montado, 15193 MB
[BLE] anunciando como Bastao-Aggilli-A1B2
[RFID] 30 bytes: 02 32 44 ...
R;211;348120;076000000001234
```
