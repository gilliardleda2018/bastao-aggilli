# 01 — Arquitetura

## Visão geral

O sistema tem três blocos:

1. **Bastão (hardware próprio):** antena LF, módulo leitor FDX-B 134,2 kHz, ESP32, gatilho, bateria,
   buzzer/LED/motor de vibração.
2. **Celular Android acoplado:** display, armazenamento, GPS (futuro), conectividade com a internet.
3. **Sistema Aggilli (nuvem):** destino final das leituras (a definir).

```
 Brinco FDX-B --(campo LF 134,2 kHz)--> Antena --> Módulo leitor --UART--> ESP32 --BLE--> App Android
                                                                              |                |
                                                                       Gatilho/Buzzer/LED   SQLite local
                                                                                                |
                                                                                    (futuro) API Aggilli
```

## Decisões de projeto

| Decisão | Escolha | Motivo |
|---|---|---|
| Frequência | LF 134,2 kHz (ISO 11784/11785, FDX-B) | Padrão usual dos brincos de bovinos; **a confirmar** com os brincos do rebanho |
| Microcontrolador | ESP32 | BLE e Wi-Fi integrados, barato, bem documentado |
| Ligação ao celular | BLE na v1 (USB OTG como alternativa futura) | Celular removível, porta de carga livre, sem cabo exposto |
| Dados no celular | SQLite local, exportação CSV | Funciona offline no curral |
| Anti-duplicata | Firmware (janela deslizante) e app (única por sessão) | Brincos são lidos várias vezes seguidas |
| Interface | Jetpack Compose | Código curto e fácil de evoluir |

## Fluxo de dados

1. O módulo leitor emite um frame na UART quando detecta uma tag.
2. O firmware agrupa os bytes em um frame (silêncio de `FRAME_GAP_MS`), extrai o ID (15 dígitos)
   e descarta repetições dentro de `DEDUP_MS`.
3. O firmware notifica a característica BLE `READING` com `R;<seq>;<uptime_ms>;<id>`.
4. O app grava `(sessão, id, timestamp)`. Se o par `(sessão, id)` já existe, a leitura é marcada como duplicada.
5. O app mostra o último ID, o contador da sessão e a lista de leituras, com bip e vibração.

## Modos de operação

- **Modo contínuo (`TRIGGER_MODE 0`):** o bastão lê o tempo todo.
- **Modo gatilho (`TRIGGER_MODE 1`):** só lê enquanto o gatilho está apertado (economiza bateria e evita leituras acidentais).
  Pode ser trocado em tempo de execução pelo comando BLE `M0`/`M1`.

## Limitações conhecidas da v0.1

- Se o celular estiver desconectado, as leituras do bastão não são guardadas no firmware (ver roadmap).
- Sem sincronização com a nuvem (apenas interface prevista).
- Sem criptografia/autenticação além do pareamento BLE padrão.
