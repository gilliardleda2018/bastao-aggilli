# 03 — Ligações elétricas

Os pinos abaixo são os de `firmware/include/config.h` e `hardware/pinout.csv`. Mudou um pino, mude
nos dois lugares.

## Alimentação

```
2x 18650 em paralelo -> TP4056 (carga + proteção) -> Chave -+-> MT3608 A (9,0 V ou 5,0 V) -> Leitor (VCC), com 470 µF + 100 nF
                                                            |
                                                            +-> MT3608 B (5,0 V) ---------> ESP32 (VIN)
                                                            |
                                                            +-> divisor 100k/100k -------> GPIO34 (medição)
ESP32 3V3 -> display OLED (VCC) e módulo microSD (VCC, se for de 3,3 V)
```

- **Confirme a tensão do leitor antes de tudo.** WL-134 costuma pedir 9 V; há versões de 5 V.
- Ajuste os dois MT3608 **sem carga** e só depois ligue leitor e ESP32.
- GND de tudo comum.
- Mantenha conversores e baterias longe da bobina: ruído e metal reduzem o alcance.

## Pinagem

| Função | Pino ESP32 | Ligação |
|---|---|---|
| Leitor TX | GPIO16 (RX2) | Meça o TX antes. Até 3,6 V: direto. ~5 V: 10 kΩ série + 20 kΩ ao GND. ~9 V: 22 kΩ + 12 kΩ |
| Leitor RX (opcional) | GPIO17 | Normalmente sem ligação |
| Gatilho | GPIO4 | Micro switch entre GPIO4 e GND (pull-up interno) |
| Buzzer | GPIO25 | 1 kΩ → base do BC547; buzzer entre +5 V e coletor; emissor no GND |
| Vibração | GPIO26 | Igual ao buzzer; motor entre 3,3 V e coletor, 1N4148 em paralelo (catodo no +) |
| LED | GPIO2 | LED da placa; externo: 330 Ω → LED → GND |
| Bateria (ADC) | GPIO34 | Bateria+ (após a chave) → 100 kΩ → GPIO34 → 100 kΩ → GND, 100 nF ao GND |
| Display SDA | GPIO21 | I2C |
| Display SCL | GPIO22 | I2C |
| microSD CS | GPIO5 | SPI (VSPI) |
| microSD SCK | GPIO18 | SPI |
| microSD MISO | GPIO19 | SPI |
| microSD MOSI | GPIO23 | SPI |

## Cuidados

- Bateria sempre com proteção e carga pelo TP4056.
- Sem os 470 µF junto ao leitor, o pico de corrente da bobina pode reiniciar o ESP32.
- O GPIO5 é um pino de inicialização do ESP32; com o módulo SD ligado ele funciona normalmente,
  mas se o ESP32 não gravar pelo USB, desconecte o SD durante a gravação.
- Cartão microSD em FAT32. Para tirar o cartão, desligue o bastão antes.
