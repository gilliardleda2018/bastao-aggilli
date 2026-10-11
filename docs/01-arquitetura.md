# 01 — Arquitetura

## Visão geral

O sistema tem três blocos:

1. **Bastão (hardware próprio):** antena LF, módulo leitor FDX-B 134,2 kHz, ESP32, gatilho, bateria,
   buzzer/LED/motor de vibração.
2. **Celular Android acoplado:** display, armazenamento, GPS (futuro), conectividade com a internet.
3. **Aggilli Agro (nuvem):** destino final das leituras, sincronizadas automaticamente pelo app
   (ver `docs/08-integracao-aggilli-agro.md`).

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
| Microcontrolador | ESP32 programado no ecossistema **Arduino** | BLE e Wi-Fi integrados, barato, liga em < 1 s, consumo baixo (ver abaixo) |
| Ligação ao celular | BLE na v1 (USB OTG como alternativa futura) | Celular removível, porta de carga livre, sem cabo exposto |
| Dados no celular | SQLite local, exportação CSV | Funciona offline no curral |
| Anti-duplicata | Firmware (janela deslizante) e app (única por sessão) | Brincos são lidos várias vezes seguidas |
| Interface | Jetpack Compose | Código curto e fácil de evoluir |

## Arduino (ESP32) ou Raspberry Pi?

O ESP32 **é** a opção Arduino: é programado na IDE/framework Arduino (PlatformIO) e custa menos que
um Arduino Uno, já com Bluetooth e Wi-Fi. Arduino Uno/Nano clássicos não têm Bluetooth.

| Critério | ESP32 (Arduino) + Android | Raspberry Pi (Zero 2 W) |
|---|---|---|
| Tempo para ligar | < 1 s | 20 a 40 s (Linux) |
| Consumo | ~0,1 a 0,25 W | ~0,6 a 1,5 W (bateria 4 a 6x maior) |
| Desligar sem cuidado | Sem risco | Pode corromper o cartão SD |
| Tela, GPS, internet | Do celular | Precisa de tela, modem 4G e GPS próprios |
| Custo do hardware próprio | Baixo | 3 a 5x maior |

O Raspberry Pi só compensa num bastão **sem celular** (tela própria e chip 4G). Com o celular
acoplado, ele já faz o papel de computador, e o ESP32 fica só com a leitura e o Bluetooth.

## Fluxo de dados

1. O módulo leitor emite um frame na UART quando detecta uma tag.
2. O firmware agrupa os bytes em um frame (silêncio de `FRAME_GAP_MS`), extrai o ID (15 dígitos)
   e descarta repetições dentro de `DEDUP_MS`.
3. A leitura entra na fila do bastão e é notificada no `READING` com `R;<seq>;<uptime_ms>;<id>;<idade_ms>`.
4. O app grava `(sessão, id, timestamp)` e confirma com `A;<seq>`. Se o par `(sessão, id)` já existe, é duplicada.
5. O app mostra o último ID, o contador da sessão e a lista de leituras, com bip e vibração.
6. Em segundo plano, o app envia as pendentes ao Aggilli Agro quando houver internet.

## Modos de operação

- **Modo contínuo (`TRIGGER_MODE 0`):** o bastão lê o tempo todo.
- **Modo gatilho (`TRIGGER_MODE 1`):** só lê enquanto o gatilho está apertado (economiza bateria e evita leituras acidentais).
  Pode ser trocado em tempo de execução pelo comando BLE `M0`/`M1`.

## Limitações conhecidas da v0.2

- A fila offline do bastão fica em RAM (500 leituras): desligar o bastão antes de reconectar perde as pendentes.
- Sincronização pronta no app; a rota no Aggilli Agro ainda precisa ser publicada (`integracao/`).
- Sem criptografia/autenticação além do pareamento BLE padrão.
