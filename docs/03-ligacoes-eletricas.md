# 03 — Ligações elétricas

Atenção: os pinos abaixo são os padrões de `firmware/include/config.h` e `hardware/pinout.csv`.
Se mudar um pino, mude nos dois lugares.

## Alimentação

```
2x 18650 em paralelo -> TP4056 (carga + proteção) -> Chave liga/desliga -+-> Step-up A (9,0 V ou 5,0 V) -> Módulo leitor (VCC)
                                                                         |                              filtro 470 µF + 100 nF
                                                                         +-> Step-up B (5,0 V) ---------> ESP32 (pino VIN)
```

- **Confirme a tensão do módulo leitor antes de tudo.** Os módulos da família WL-134 costumam pedir
  **9 V** (há versões de 5 V). Por isso são dois conversores: A na tensão do leitor e B a 5 V para o ESP32.
  Alimentar o ESP32 com 9 V no VIN aquece demais o regulador da placa.
- O GND de tudo é comum.
- Ruído do conversor step-up reduz o alcance de leitores LF. Mantenha o step-up **longe da antena**,
  use os capacitores de filtro e fios curtos.
- Confirme no datasheet do módulo leitor a corrente consumida (inclusive picos).

## Módulo leitor -> ESP32

| Módulo leitor | ESP32 | Observação |
|---|---|---|
| VCC | +5 V (ou 3,3 V, conforme datasheet) | Com filtro 470 µF + 100 nF perto do módulo |
| GND | GND | |
| TX | GPIO16 (RX2) | **Meça o TX antes de ligar.** Até 3,6 V: direto. ~5 V: divisor 10 kΩ (série) / 20 kΩ (GND). ~9 V: 22 kΩ / 12 kΩ |
| RX | GPIO17 (TX2) | Opcional, para configurar o módulo |
| Antena | Terminais da antena do módulo | Cabo curto e afastado de metal |

Divisor para TX de 5 V: TX do módulo -> 10 kΩ -> GPIO16, e GPIO16 -> 20 kΩ -> GND.

## Entradas e saídas

| Função | ESP32 | Circuito |
|---|---|---|
| Gatilho | GPIO4 | Microchave entre GPIO4 e GND (usa `INPUT_PULLUP`, ativo em nível baixo) |
| Buzzer ativo | GPIO25 | GPIO25 -> 1 kΩ -> base NPN; emissor no GND; buzzer entre +5 V e coletor |
| Motor de vibração | GPIO26 | Igual ao buzzer, com diodo 1N4148/1N4007 em paralelo ao motor (catodo no +) |
| LED | GPIO2 (LED da placa) | LED externo opcional em GPIO2 -> 330 Ω -> LED -> GND |
| Display (opcional) | GPIO21 SDA / GPIO22 SCL | OLED SH1106 ou LCD de memória em 3V3 (firmware ainda sem suporte) |
| Leitura da bateria | GPIO34 (ADC1) | Bateria + -> 100 kΩ -> GPIO34 -> 100 kΩ -> GND, com 100 nF em GPIO34/GND |

A leitura da bateria usa fator 2 (divisor 1:1) e dá apenas uma **estimativa** (o ADC do ESP32 não é linear).

## Segurança

- Use sempre bateria com proteção e carregue pelo TP4056 com proteção.
- Não deixe a bateria exposta a calor excessivo (sol dentro de carcaça fechada).
- Ajuste as saídas dos dois step-up (leitor e 5,0 V) **antes** de ligar o ESP32 e o módulo.
- Recomendado: módulo leitor dentro da ponteira, junto da bobina; pela haste descem só VCC, GND e TX
  (ver `docs/05-mecanica.md`).
