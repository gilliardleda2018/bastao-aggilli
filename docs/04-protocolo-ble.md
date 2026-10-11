# 04 — Protocolo BLE (bastão <-> app)

O bastão anuncia o nome `Bastao-Aggilli` e o serviço abaixo. O app procura pelo UUID do serviço.

## UUIDs

| Item | UUID |
|---|---|
| Serviço | `a91c0001-5f3e-4b8a-9d27-3c6e1b7f0a10` |
| `READING` (notify) | `a91c0002-5f3e-4b8a-9d27-3c6e1b7f0a10` |
| `STATUS` (notify) | `a91c0003-5f3e-4b8a-9d27-3c6e1b7f0a10` |
| `CONTROL` (write) | `a91c0004-5f3e-4b8a-9d27-3c6e1b7f0a10` |

Os mesmos valores estão em `firmware/include/config.h` e `android/.../ble/BleUuids.kt`.
Para trocar, gere novos UUIDs e atualize os dois lados.

## Mensagens (texto ASCII, campos separados por `;`)

### `READING` (bastão -> app)

```
R;<seq>;<uptime_ms>;<id>;<idade_ms>
```

- `seq`: contador crescente desde o boot do bastão.
- `uptime_ms`: milissegundos desde o boot, no momento da leitura.
- `id`: 15 dígitos (padrão ISO 11784: 3 dígitos de país + 12 do animal), ou o frame bruto em
  hexadecimal com `PARSER_MODE 1`.
- `idade_ms` (fw 0.2.0+): há quanto tempo a leitura foi feita. O app grava
  `horário do celular - idade_ms`, então leituras feitas offline ficam com o horário certo.
  Acima de 3 s o app trata como **recuperada** da fila (sem bip por leitura).

Exemplo: `R;12;348120;982000123456789;35`

### `STATUS` (bastão -> app, a cada 5 s)

```
S;<bateria_mV>;<modo_gatilho>;<versao_fw>;<pendentes>;<descartadas>
```

- `pendentes`: leituras na fila do bastão ainda não confirmadas pelo app.
- `descartadas`: leituras perdidas porque a fila encheu (`QUEUE_CAPACITY`) desde o boot.

Exemplo: `S;3987;0;0.2.0;0;0`

## Fila offline e confirmação (fw 0.2.0+)

Toda leitura entra numa fila em RAM no bastão e só sai quando o app confirma:

1. O app conecta, ativa as notificações e escreve `H` no `CONTROL`.
2. O bastão (re)envia, em ordem, tudo o que está na fila, uma notificação a cada `NOTIFY_GAP_MS`.
3. Depois de gravar no SQLite, o app escreve `A;<seq>`: o bastão remove as leituras até `seq`.
4. Se a conexão cair, o que não foi confirmado é reenviado na próxima conexão. O app ignora
   duplicatas (mesma tag na sessão), então o reenvio não gera registros repetidos.

Com o celular desconectado, o bastão continua lendo e dá um **bip longo** (leitura guardada).
Com o celular conectado, o bip é curto.

A fila fica em RAM: se o bastão for **desligado** antes de reconectar, as pendentes se perdem
(gravação em flash está no roadmap).

### `CONTROL` (app -> bastão)

| Comando | Efeito |
|---|---|
| `B` | Bip, LED e vibração do bastão |
| `M0` | Modo contínuo (lê sempre) |
| `M1` | Modo gatilho (lê só com o gatilho apertado) |
| `H` | App pronto: (re)enviar a fila (fw 0.2.0+) |
| `A;<seq>` | Confirma as leituras até `seq` (fw 0.2.0+) |

Compatibilidade: um app antigo não manda `H`; o firmware começa a enviar mesmo assim após
`HELLO_TIMEOUT_MS` (2 s). Sem `A`, as leituras ficam na fila e são reenviadas a cada conexão.

## MTU

O app solicita MTU 185 e o firmware também configura 185, para caber mensagens maiores que 20 bytes.
Se a negociação falhar, as mensagens de 15 dígitos (cerca de 30 bytes) ainda podem ser truncadas:
nesse caso, reduza o formato ou use `PARSER_MODE 0` com o MTU negociado.
