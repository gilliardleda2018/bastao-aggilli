# App Android — Bastão Aggilli

Kotlin + Jetpack Compose. Escolhe e conecta ao bastão por BLE, mantém a coleta com a tela apagada,
grava as leituras por sessão (SQLite), avisa repetidos (no celular, no bastão e por voz), exporta
CSV e sincroniza com o Aggilli Agro.

## Requisitos

- Android Studio recente (Koala ou superior, JDK 17)
- Celular com Android 8.0 (API 26) ou superior e Bluetooth LE

## Rodar

1. Abra a pasta `android/` no Android Studio (ele baixa o Gradle e gera o wrapper).
2. Conecte o celular com depuração USB e execute o módulo `app`.
3. Toque em **Escolher bastão**, aceite as permissões e escolha o bastão na lista.
4. Toque em **Conectar**.

Para distribuir: **Build > Generate Signed Bundle / APK**.

## Estrutura

| Arquivo | Função |
|---|---|
| `BastaoController.kt` | Lógica do app: conexão, sessão, ACK, avisos, voz, hora para o bastão, sincronização. Objeto único do processo |
| `BleForegroundService.kt` | Serviço em primeiro plano: mantém a conexão com a tela apagada; notificação com contagem e "Desconectar" |
| `MainActivity.kt` | Tela (Compose), permissões, escolha do bastão, configuração da sincronização |
| `MainViewModel.kt` | Ponte entre a tela e o controlador |
| `ble/BleClient.kt` | Busca, conexão, notificações, fila de escritas, reconexão |
| `ble/BleUuids.kt` | UUIDs (iguais aos do firmware) |
| `data/ReadingDb.kt` | SQLite: leituras por sessão com UUID, CSV, pendências de sincronização |
| `sync/` | Configuração, envio HTTPS e WorkManager (ver `docs/08`) |
| `Feedback.kt` | Bip e vibração do celular |
| `Speaker.kt` | Voz: últimos 4 dígitos e "repetido" |

## Permissões

| Permissão | Para quê |
|---|---|
| Dispositivos por perto (Android 12+) / Localização (8 a 11) | Buscar e conectar ao bastão |
| Notificações (Android 13+) | Mostrar a notificação de coleta ativa (opcional) |
| Serviço em primeiro plano (dispositivo conectado) | Manter a coleta com a tela apagada |
| Internet | Sincronizar com o Aggilli Agro |

## Observações

- Uma tag só é registrada uma vez por sessão; repetidas geram aviso e o comando `D` ao bastão.
- Leituras guardadas no bastão chegam ao reconectar com o horário em que foram lidas.
- A sincronização só aceita endereço `https://`.
- Código verificado em sintaxe, **ainda não compilado** no Android Studio: espere pequenos ajustes de
  versões de dependências no primeiro build.
