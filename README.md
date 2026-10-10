# Bastão de Coleta RFID — Aggilli

Bastão portátil para leitura de brincos eletrônicos (RFID) de rebanhos.
Um **celular Android é acoplado ao bastão** e funciona como display, processador, armazenamento e
conexão com o sistema. O hardware próprio faz apenas a leitura da tag e a comunicação com o celular.

**Proprietário do projeto:** Aggilli

## Como funciona

```
[Antena LF 134,2 kHz] -> [Módulo leitor FDX-B] -> [ESP32] --BLE--> [Celular Android] -> [Sistema Aggilli]
                                                    ^
                                      [Gatilho] [Bateria] [Buzzer/LED/Vibra]
```

1. O operador aproxima o bastão do brinco (e aperta o gatilho, se o modo gatilho estiver ativo).
2. O módulo leitor entrega o ID por UART; o ESP32 remove repetições e envia por BLE.
3. O app grava a leitura (SQLite local), mostra na tela e dá retorno sonoro/vibratório.
4. As leituras podem ser exportadas em CSV e, no futuro, sincronizadas com o sistema Aggilli.

## Estrutura do repositório

| Pasta | Conteúdo |
|---|---|
| `docs/` | Arquitetura, lista de materiais, ligações elétricas, protocolo BLE, mecânica, plano de testes, roadmap |
| `hardware/` | Lista de materiais em CSV (`bom.csv`) e tabela de pinos (`pinout.csv`) |
| `firmware/` | Firmware do ESP32 (PlatformIO, framework Arduino) |
| `android/` | App Android (Kotlin + Jetpack Compose) |
| `tools/` | Utilitários de bancada (captura bruta da serial do módulo leitor) |

## Por onde começar

1. Leia `docs/01-arquitetura.md` e `docs/02-lista-de-materiais.md`.
2. **Etapa de bancada (obrigatória):** confirme a frequência/padrão dos brincos do rebanho e valide o
   formato de saída do módulo leitor com `tools/serial_hexdump.py` (veja `docs/06-plano-de-testes.md`).
3. Ajuste `firmware/include/config.h` (pinos, baud rate, parser) e grave o ESP32 (`firmware/README.md`).
4. Abra a pasta `android/` no Android Studio e rode o app (`android/README.md`).

## Status (v0.1.0)

Esqueleto inicial completo, **ainda não compilado nem testado em hardware**. Pontos que dependem
da validação em bancada:

- Formato do frame UART do módulo leitor escolhido (o parser em `firmware/src/main.cpp` é configurável).
- Alcance real da antena com os brincos do rebanho.
- Versões de dependências (Gradle/AGP/Compose) podem precisar de ajuste conforme o Android Studio instalado.
- A sincronização com o sistema Aggilli é apenas uma interface (`android/.../sync/SyncService.kt`); a API ainda não foi definida.

## Licença

Copyright (c) 2026 Aggilli. Todos os direitos reservados. (Texto provisório: revisar com o jurídico da Aggilli.)
