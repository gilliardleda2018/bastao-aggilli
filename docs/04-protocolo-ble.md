# 04 — Protocolo BLE (bastão <-> app), versão 3

O bastão anuncia o serviço abaixo. O nome vem na resposta ao scan, com os 4 últimos dígitos do MAC
(`Bastao-Aggilli-A1B2`), para distinguir vários bastões na mesma fazenda.

## UUIDs

| Item | UUID |
|---|---|
| Serviço | `a91c0001-5f3e-4b8a-9d27-3c6e1b7f0a10` |
| `READING` (notify) | `a91c0002-5f3e-4b8a-9d27-3c6e1b7f0a10` |
| `STATUS` (notify) | `a91c0003-5f3e-4b8a-9d27-3c6e1b7f0a10` |
| `CONTROL` (write) | `a91c0004-5f3e-4b8a-9d27-3c6e1b7f0a10` |

Os mesmos valores estão em `firmware/include/config.h` e `android/.../ble/BleUuids.kt`.

Todas as mensagens são texto ASCII com campos separados por `;`. Campos novos sempre entram no fim,
e os dois lados aceitam mensagens com campos a menos (compatibilidade entre versões).

## `READING` (bastão -> app)

```
R;<seq>;<uptime_ms>;<id>;<idade_ms>;<epoch>
```

| Campo | Desde | Significado |
|---|---|---|
| `seq` | 0.1 | Número da leitura. Continua depois de desligar e religar (fica no diário da flash). |
| `uptime_ms` | 0.1 | `millis()` do bastão no momento da leitura. |
| `id` | 0.1 | 15 dígitos ISO 11784 (3 de país + 12 do animal), ou hex bruto com `PARSER_MODE 1`. |
| `idade_ms` | 0.2 | Há quanto tempo a leitura foi feita. `-1` se ela é de uma ligação anterior do bastão. |
| `epoch` | 0.3 | Data/hora UNIX (s) da leitura. `0` se o celular ainda não tinha informado a hora. |

O app calcula o horário assim: `epoch` se for maior que 0; senão `agora - idade_ms`; senão a hora de
chegada (leitura antiga, sem hora: marcada como recuperada).

Exemplo: `R;212;348120;076000000001234;35;1791740600`

## `STATUS` (bastão -> app, a cada 5 s e ao conectar)

```
S;<bateria_mV>;<modo>;<versao_fw>;<pendentes>;<descartadas>;<bateria_%>;<sd>;<ligacao>
```

| Campo | Significado |
|---|---|
| `bateria_mV` | Tensão estimada da bateria (ADC do ESP32 × divisor) |
| `modo` | `0` contínuo, `1` gatilho |
| `versao_fw` | Ex.: `0.3.0` |
| `pendentes` | Leituras na fila do bastão esperando confirmação |
| `descartadas` | Leituras perdidas porque a fila encheu (desde que ligou) |
| `bateria_%` | Carga estimada pela curva Li-ion (desde 0.3) |
| `sd` | `0` sem cartão, `1` ok, `2` erro (desde 0.3) |
| `ligacao` | Quantas vezes o bastão foi ligado (desde 0.3) |

Exemplo: `S;3987;0;0.3.0;0;0;73;1;42`

## `CONTROL` (app -> bastão)

| Comando | Desde | Efeito |
|---|---|---|
| `H` | 0.2 | App pronto: o bastão (re)envia a fila, da mais antiga para a mais nova |
| `A;<seq>` | 0.2 | Confirma as leituras até `seq`; o bastão tira da fila e registra no diário |
| `B` | 0.1 | Bip de teste no bastão |
| `M0` / `M1` | 0.1 | Modo contínuo / gatilho (fica salvo na memória do bastão) |
| `T;<epoch>;<fuso_min>` | 0.3 | Data/hora UNIX (s) e fuso do celular em minutos (ex.: `-180`). O bastão data as leituras e os arquivos do cartão SD |
| `C;<n>` | 0.3 | Contagem da sessão no app, mostrada no display do bastão |
| `D` | 0.3 | A última leitura já estava na sessão: bip duplo e "REPETIDO" no display |

Sequência ao conectar (app 0.3):

1. App ativa as notificações e escreve `H`.
2. App escreve `T;<epoch>;<fuso>` e `C;<contagem>`.
3. Bastão manda `STATUS` e começa a reenviar a fila (uma notificação a cada `NOTIFY_GAP_MS`).
4. Para cada `READING`, o app grava no SQLite, responde `A;<seq>`, e `D` se for repetido, mais `C;<n>`.

## Fila offline e diário em flash

Toda leitura entra numa fila em RAM e num diário em flash (`/fila.log` no LittleFS). Ela só sai quando
o app confirma (`A`). Se o celular estiver longe, o bastão continua lendo e dá um **bip longo**.
Se o bastão for **desligado**, ao religar ele refaz a fila a partir do diário e reenvia tudo.

Um registro cortado por falta de energia é descartado sem perder os anteriores. O diário é
compactado quando a fila esvazia ou quando passa de `LOG_COMPACT_BYTES`.

## Compatibilidade

| App \ Firmware | 0.1 | 0.2 | 0.3 |
|---|---|---|---|
| 0.1 | ok | ok (sem fila confirmada) | ok (sem fila confirmada) |
| 0.2 | ok | ok | ok (sem hora, display sem contagem) |
| 0.3 | ok | ok (comandos T, C, D ignorados) | completo |

## MTU

App e firmware pedem MTU 185. Uma `READING` típica tem cerca de 50 bytes.
