# Firmware do bastão (ESP32)

Framework Arduino com PlatformIO. O firmware lê o módulo leitor FDX-B pela UART2, remove leituras
repetidas e envia o ID por BLE ao app Android.

## Requisitos

- [PlatformIO](https://platformio.org/) (extensão do VS Code ou CLI)
- Placa ESP32 DevKit (ESP32-WROOM-32)

## Compilar e gravar

```bash
cd firmware
pio run                 # compila
pio run -t upload       # grava no ESP32
pio device monitor      # serial a 115200 baud
```

## Configuração

Tudo em `include/config.h`: pinos, baud rate do módulo, modo do gatilho, janela anti-duplicata e
UUIDs do BLE. Os UUIDs precisam ser iguais aos do app (`android/.../ble/BleUuids.kt`).

## Parser do frame (ponto crítico)

O formato do frame varia conforme o fabricante. O parser fica em `lib/bastao_core/tag_parser.cpp`
(C++ puro, testado no PC) e tem quatro modos (`PARSER_MODE` em `config.h`):

- `0` ASCII15: ID já em decimal ("982000123456789", aceita "982 000123456789"). Exige uma sequência
  isolada de exatamente 15 dígitos.
- `1` RAW_HEX: envia o frame bruto em hexadecimal. Use para descobrir o formato do módulo.
- `2` WL134: pacote binário de 30 bytes da família WL-134 (STX, ID em hex com o dígito menos
  significativo primeiro, país, flags, XOR, ~XOR, ETX). Checksum errado é descartado
  (`STRICT_CHECKSUM 1`) para não gravar um ID trocado.
- `3` AUTO (padrão): tenta WL134 e depois ASCII15.

O layout WL-134 segue o que é publicado para esses módulos; **confirme com o datasheet do módulo
comprado** na etapa de bancada. Se aparecer `CHECKSUM_INVALIDO` com brincos bons, capture os
frames (`DEBUG_RAW 1`) e ajuste `checkPacket()`.

## Testes no PC

```bash
cd firmware/test_host
make            # compila com g++ (AddressSanitizer) e roda os testes do parser e da fila
```

## Fila offline

As leituras ficam numa fila em RAM (`QUEUE_CAPACITY`, padrão 500) até o app confirmar com
`A;<seq>`. Ver `docs/04-protocolo-ble.md`.

Com `DEBUG_RAW 1`, cada frame bruto é impresso na serial USB (hex e ASCII), o que ajuda a ajustar o parser.

## Notas

- O código foi escrito para o Arduino core 2.0.x do ESP32 (o `platformio.ini` fixa
  `espressif32@6.9.0`). Em core 3.x a API de BLE muda e o código precisaria de ajustes.
- Com o celular desconectado, as leituras ficam na fila em RAM (bip longo). Desligar o bastão
  antes de reconectar perde as pendentes (gravação em flash no roadmap v0.3).
