# 03 — Ligações elétricas

Atenção: os pinos abaixo são os padrões de `firmware/include/config.h` e `hardware/pinout.csv`.
Se mudar um pino, mude nos dois lugares.

## Alimentação

```
Bateria 18650 -> TP4056 (carga + proteção) -> Chave liga/desliga -> Step-up 5 V -> +5 V
                                                                       |
                         ESP32 (pino VIN) <-----------------------------+
                         Módulo leitor (VCC) <--- filtro 470 µF + 100 nF
```

- O ESP32 aceita 5 V em VIN (regulador da placa). O GND de tudo é comum.
- Ruído do conversor step-up reduz o alcance de leitores LF. Mantenha o step-up **longe da antena**,
  use os capacitores de filtro e fios curtos.
- Confirme no datasheet do módulo leitor a corrente consumida (inclusive picos).

## Módulo leitor -> ESP32

| Módulo leitor | ESP32 | Observação |
|---|---|---|
| VCC | +5 V (ou 3,3 V, conforme datasheet) | Com filtro 470 µF + 100 nF perto do módulo |
| GND | GND | |
| TX | GPIO16 (RX2) | **Se o TX do módulo for 5 V, use divisor 10 kΩ / 20 kΩ (ou conversor de nível)** |
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
| Leitura da bateria | GPIO34 (ADC1) | Bateria + -> 100 kΩ -> GPIO34 -> 100 kΩ -> GND, com 100 nF em GPIO34/GND |

A leitura da bateria usa fator 2 (divisor 1:1) e dá apenas uma **estimativa** (o ADC do ESP32 não é linear).

## Segurança

- Use sempre bateria com proteção e carregue pelo TP4056 com proteção.
- Não deixe a bateria exposta a calor excessivo (sol dentro de carcaça fechada).
- Ajuste a saída do step-up para 5,0 V **antes** de ligar o ESP32 e o módulo.
