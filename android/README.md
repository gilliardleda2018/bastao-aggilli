# App Android — Bastão Aggilli

Kotlin + Jetpack Compose. Conecta ao bastão por BLE, registra as leituras em SQLite e exporta CSV.

## Requisitos

- Android Studio recente (Koala ou superior, com JDK 17)
- Celular com Android 8.0 (API 26) ou superior e Bluetooth LE

## Rodar

1. Abra a pasta `android/` no Android Studio (ele baixa o Gradle e gera o wrapper).
2. Conecte o celular com depuração USB e execute o app (`app`).
3. Toque em **Conectar**, aceite as permissões de Bluetooth e ligue o bastão.

## Estrutura

| Arquivo | Função |
|---|---|
| `MainActivity.kt` | Tela (Compose) e pedido de permissões |
| `MainViewModel.kt` | Estado da tela, sessões e regras de duplicata |
| `ble/BleClient.kt` | Scan, conexão, notificações e reconexão automática |
| `ble/BleUuids.kt` | UUIDs do serviço (iguais aos do firmware) |
| `data/ReadingDb.kt` | SQLite: leituras por sessão, CSV, pendências de sincronização |
| `sync/SyncConfig.kt` | Endereço, token e fazenda do Aggilli Agro |
| `sync/SyncService.kt` | Envio HTTPS das leituras (contrato em `docs/08-integracao-aggilli-agro.md`) |
| `sync/SyncWorker.kt` | Envio em segundo plano com WorkManager (funciona com o app fechado) |
| `Feedback.kt` | Bip e vibração do celular |

## Observações

- As versões de Gradle/AGP/Kotlin/Compose estão em `build.gradle.kts` e `app/build.gradle.kts`.
  Se o Android Studio sugerir atualização, atualize mantendo a compatibilidade Kotlin x Compose Compiler.
- Código ainda **não compilado nem testado** em aparelho; espere pequenos ajustes no primeiro build.
- Uma mesma tag só é registrada uma vez por sessão; novas leituras iguais aparecem como "Já lido nesta sessão".
- Leituras feitas com o celular longe chegam ao reconectar, com o horário em que foram lidas.
- A sincronização só aceita endereço `https://`.
