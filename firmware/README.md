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

O formato do frame enviado pelo módulo leitor varia conforme o fabricante. O `parseFrame()` em
`src/main.cpp` tem dois modos:

- `PARSER_MODE 0`: pega os 15 primeiros dígitos ASCII do frame (ID ISO 11784). Só funciona se o
  módulo enviar o ID em ASCII no início do frame.
- `PARSER_MODE 1`: envia o frame bruto em hexadecimal. Use para validar o formato e ver o que
  o módulo realmente envia.

Com `DEBUG_RAW 1`, cada frame bruto é impresso na serial USB (hex e ASCII), o que ajuda a ajustar o parser.

## Notas

- O código foi escrito para o Arduino core 2.0.x do ESP32 (o `platformio.ini` fixa
  `espressif32@6.9.0`). Em core 3.x a API de BLE muda e o código precisaria de ajustes.
- Se o celular não estiver conectado, as leituras não são guardadas (ver `docs/07-roadmap.md`).
