# Bastão de Coleta RFID — Aggilli

Bastão próprio para leitura de brincos eletrônicos FDX-B (134,2 kHz) de bovinos. O bastão lê o
brinco e transmite por Bluetooth para o celular do operador, que fica no bolso. O celular grava as
leituras por sessão e as envia sozinho ao **Aggilli Agro**. O bastão tem display, cartão de memória
e memória interna próprios: nada se perde com o celular longe, sem sinal ou com o bastão desligado.

**Proprietário do projeto:** Aggilli Intelligence · **Versão:** 0.3.0

## Como funciona

```
Brinco -> [Ponteira: antena + leitor FDX-B] -UART-> [Empunhadura: ESP32] -BLE-> [Celular no bolso] -HTTPS-> [Aggilli Agro]
                                                       display · gatilho · bip/vibração
                                                       fila em flash · histórico no microSD
```

1. O operador aproxima o bastão do brinco (no modo gatilho, aperta o gatilho).
2. O ESP32 confere o pacote (checksum), remove repetições, bipa, mostra no display, guarda na fila
   em flash e no cartão SD, e envia ao celular.
3. O app grava, confirma ao bastão, avisa repetidos (no bastão e por voz) e mostra a contagem.
4. Com internet, o app envia as leituras ao Aggilli Agro, mesmo com a tela apagada ou o app fechado.

## Estrutura do repositório

| Pasta | Conteúdo |
|---|---|
| `docs/` | Arquitetura, materiais, ligações, protocolo BLE, mecânica, testes, roadmap, integração, manual do operador, produção e preço |
| `hardware/` | Lista de materiais (`bom.csv`) e pinagem (`pinout.csv`) |
| `firmware/` | Firmware do ESP32 (PlatformIO, Arduino); lógica testável em `lib/bastao_core`, testes em `test_host` |
| `android/` | App Android (Kotlin + Jetpack Compose) |
| `integracao/` | Receptor de referência da API no Aggilli Agro + testes |
| `tools/` | Bancada: captura da serial do leitor e simulador de brinco |

## Documentação

| Documento | Para quem |
|---|---|
| [01 Arquitetura](docs/01-arquitetura.md) | Todos |
| [02 Lista de materiais](docs/02-lista-de-materiais.md) | Compras |
| [03 Ligações elétricas](docs/03-ligacoes-eletricas.md) | Montagem |
| [04 Protocolo BLE v3](docs/04-protocolo-ble.md) | Firmware e app |
| [05 Mecânica](docs/05-mecanica.md) | Carcaça |
| [06 Plano de testes](docs/06-plano-de-testes.md) | Bancada e campo |
| [07 Roadmap](docs/07-roadmap.md) | Gestão |
| [08 Integração com o Aggilli Agro](docs/08-integracao-aggilli-agro.md) | Backend |
| [09 Manual do operador](docs/09-manual-do-operador.md) | Quem usa no curral |
| [10 Produção, custo e preço](docs/10-producao-e-preco.md) | Comercial |
| [11 Guia de montagem ilustrado (PDF)](docs/11-guia-de-montagem.pdf) | Primeira montagem, passo a passo |
| [Firmware](firmware/README.md) · [App](android/README.md) · [Histórico de versões](CHANGELOG.md) | Desenvolvimento |

## Por onde começar

1. Compre primeiro os leitores, os ESP32 e o adaptador USB-TTL (`docs/02`).
2. Rode os testes no computador: `cd firmware/test_host && make`.
3. Grave o firmware (`firmware/README.md`) e teste com o simulador de brinco (`docs/06`, etapa 1).
4. Com o leitor em mãos, confirme tensão, nível do TX e formato do frame (`docs/03` e `docs/06`).
5. Abra `android/` no Android Studio e rode o app (`android/README.md`).

## Testes automáticos (GitHub Actions)

A cada push, o GitHub compila e testa tudo (`.github/workflows/ci.yml`):

| Etapa | O que faz | O que gera |
|---|---|---|
| Firmware ESP32 | Testes da lógica no PC + compilação com PlatformIO | `firmware-esp32` (firmware.bin, bootloader.bin, partitions.bin) |
| App Android | Compilação com Gradle | `app-bastao-debug` (APK para instalar no celular) |
| Receptor da API | Testes do receptor e do simulador | — |

Os arquivos ficam na aba **Actions** do repositório, dentro de cada execução, em **Artifacts**.

## Testes no Docker (sem GitHub)

As mesmas etapas rodam no computador com Docker (`docker-compose.yml`). No Windows:

```powershell
.\testar-tudo.ps1                    # todas as etapas, com resumo no final
.\testar-tudo.ps1 firmware-testes    # uma etapa só
docker compose --profile servidor up api   # receptor da API em http://localhost:8080
```

O firmware (`.bin`) e o APK saem em `saida/`. A primeira execução demora (baixa o compilador do
ESP32 e o SDK do Android); as seguintes usam o cache do Docker.

## Status (v0.3.0)

- Lógica do firmware testada no computador: **125 verificações, 0 falhas** (parser, fila, diário em
  flash com queda de energia simulada, gatilho, bateria, datas, CSV do cartão, display).
- Receptor da API testado (`integracao/`).
- Firmware verificado em sintaxe contra as APIs do ESP32; app verificado em sintaxe com o kotlinc.
- **Ainda não compilados nos toolchains reais** (PlatformIO e Android Studio) nem testados em hardware:
  espere pequenos ajustes no primeiro build.

## Licença

Copyright (c) 2026 Aggilli Intelligence. Todos os direitos reservados. (Texto provisório: revisar com o jurídico.)
