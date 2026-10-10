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
R;<seq>;<uptime_ms>;<id>
```

- `seq`: contador crescente desde o boot do bastão.
- `uptime_ms`: milissegundos desde o boot (o app usa o horário do celular para o registro).
- `id`: 15 dígitos (padrão ISO 11784: 3 dígitos de país + 12 do animal) com `PARSER_MODE 0`,
  ou o frame bruto em hexadecimal com `PARSER_MODE 1`.

Exemplo: `R;12;348120;982000123456789`

### `STATUS` (bastão -> app, a cada 5 s)

```
S;<bateria_mV>;<modo_gatilho>;<versao_fw>
```

Exemplo: `S;3987;0;0.1.0`

### `CONTROL` (app -> bastão)

| Comando | Efeito |
|---|---|
| `B` | Bip, LED e vibração do bastão |
| `M0` | Modo contínuo (lê sempre) |
| `M1` | Modo gatilho (lê só com o gatilho apertado) |

## MTU

O app solicita MTU 185 e o firmware também configura 185, para caber mensagens maiores que 20 bytes.
Se a negociação falhar, as mensagens de 15 dígitos (cerca de 30 bytes) ainda podem ser truncadas:
nesse caso, reduza o formato ou use `PARSER_MODE 0` com o MTU negociado.
