# 02 — Lista de materiais (BOM)

A mesma lista, em CSV para planilha, está em `hardware/bom.csv`. Preços de referência (outubro de
2026) e lojas estão na página ilustrada do projeto; os preços mudam, cote antes de comprar.

Custo estimado de **um bastão: cerca de R$ 700 em peças** (lista completa com reservas: cerca de
R$ 1.050). Para começar na bancada: leitores, ESP32 e adaptador USB-TTL, cerca de R$ 490.

## Eletrônica

| # | Item | Qtd | Especificação / observação |
|---|---|---|---|
| 1 | Módulo leitor RFID FDX-B 134,2 kHz, saída TTL | 1 (+1 reserva) | WL-134/WL134A, 5 a 9 V, 9600 baud, pacote de 30 bytes; vem com antenas de 65 e 130 mm |
| 2 | ESP32 DevKit 38 pinos (ESP32-WROOM-32) | 1 (+1) | Não usar ESP32-C3/S2: o firmware é para o ESP32 clássico |
| 3 | Display OLED 1,3" SH1106 128×64 I2C | 1 | 4 pinos (VCC, GND, SCL, SDA) |
| 4 | Módulo leitor de cartão microSD, SPI, 3,3 V | 1 | Se for módulo de 5 V com regulador, alimentar em 5 V |
| 5 | Cartão microSD 8 a 32 GB, FAT32, classe 10 | 1 | Formatar em FAT32 antes de usar |
| 6 | Bateria Li-ion 18650 com proteção (Samsung 25R ou similar) | 2 | Em paralelo; mesma tensão antes de unir |
| 7 | Suporte 2× 18650 em paralelo | 1 | |
| 8 | Carregador TP4056 USB-C com proteção (DW01 + 8205A) | 1 | |
| 9 | Conversor step-up MT3608 | 2 | A: tensão do leitor (9,0 V ou 5,0 V). B: 5,0 V para o ESP32 |
| 10 | Chave liga/desliga mini 2 A | 1 | |
| 11 | Micro switch com alavanca (gatilho) | 1 | |
| 12 | Buzzer ativo 5 V | 1 | |
| 13 | Motor de vibração moeda 3 V | 1 | |
| 14 | LED 5 mm | 1 | Opcional (há LED na placa) |
| 15 | Transistor NPN BC547 | 2 | Buzzer e vibração |
| 16 | Diodo 1N4148 | 1 | Roda livre do motor |
| 17 | Resistores | 1 kit | 2× 100 kΩ, 10 kΩ, 20 kΩ (ou 22 k + 12 k), 2× 1 kΩ, 330 Ω |
| 18 | Capacitores | 1 kit | 2× 470 µF/16 V, 4× 100 nF |
| 19 | Placa perfurada, barra de pinos fêmea, fio 22 AWG, JST, termorretrátil | 1 kit | |

## Mecânica

| # | Item | Qtd | Observação |
|---|---|---|---|
| 20 | Caixa Patola PBL-150 IP65 (150 × 100 × 60 mm) | 1 | Empunhadura do protótipo |
| 21 | Tubo de fibra de vidro 20 a 25 mm, 1 m | 1 | PVC serve no protótipo. Nada de metal ou fibra de carbono |
| 22 | Ponteira não metálica selada (impressa em ASA ou tampa de PVC + epóxi) | 1 | Leitor + bobina |
| 23 | Prensa-cabo PG7, parafusos inox M3, silicone neutro, janela de policarbonato | 1 kit | |

## Bancada

| # | Item | Qtd | Observação |
|---|---|---|---|
| 24 | Adaptador USB-TTL CP2102 3,3 V | 1 | Simulador de brinco e captura do leitor |
| 25 | Brincos FDX-B do rebanho | 10 | Pedir à fazenda |
| 26 | Celular Android 8.0+ com BLE | 1 | |
| 27 | Multímetro, ferro de solda, fonte de bancada | 1 | |

O suporte de celular na haste saiu do projeto na v0.3: o celular fica no bolso.
