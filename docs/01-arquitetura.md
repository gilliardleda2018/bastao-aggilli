# 01 — Arquitetura

## Visão geral

O bastão é um leitor próprio de brincos FDX-B que transmite por Bluetooth para o celular do
operador, **que fica no bolso**. O celular grava, mostra, anuncia por voz e sincroniza com o
Aggilli Agro. O bastão tem display, cartão de memória e memória interna próprios, então nada se
perde se o celular estiver longe ou sem sinal.

```
 Brinco FDX-B --(134,2 kHz)--> Ponteira (antena + leitor) --UART--> Empunhadura (ESP32) --BLE--> Celular --HTTPS--> Aggilli Agro
                                                                     |                              |
                                                   display, gatilho, buzzer, vibração      SQLite, sessões, voz,
                                                   fila em flash, histórico em microSD     sincronização automática
```

## Blocos

| Bloco | Faz | Onde está no código |
|---|---|---|
| Ponteira | Bobina 134,2 kHz e módulo leitor WL-134 selados; entrega o pacote pela UART | — |
| Empunhadura (ESP32) | Extrai e confere o ID, remove repetições, bipa/vibra, mostra no display, guarda a fila em flash e o histórico no SD, envia por BLE | `firmware/` |
| Celular Android | Escolhe o bastão, recebe e confirma, grava em sessões, avisa repetidos, fala o número, exporta CSV, sincroniza | `android/` |
| Aggilli Agro | Recebe as leituras pela API e cruza com o cadastro | `integracao/` (receptor de referência) |

## Onde cada leitura fica guardada

| Lugar | Guarda | Até quando | Para quê |
|---|---|---|---|
| Fila em RAM + diário em flash (bastão) | Leituras ainda não confirmadas pelo celular (até 500) | Até o celular confirmar | Não perder nada com o celular longe ou o bastão desligado |
| Cartão microSD (bastão) | **Todas** as leituras, em CSV por dia | Até apagar o cartão | Histórico de auditoria; dá para abrir no computador sem o celular |
| SQLite (celular) | Todas as leituras, por sessão, com UUID | Permanente | Tela, exportação, sincronização |
| Aggilli Agro | Leituras sincronizadas | Permanente | Gestão do rebanho |

## Decisões de projeto

| Decisão | Escolha | Motivo |
|---|---|---|
| Frequência | LF 134,2 kHz (ISO 11784/11785, FDX-B) | Padrão dos brincos bovinos; HDX fica para uma versão futura |
| Microcontrolador | ESP32 (ecossistema Arduino, PlatformIO) | BLE integrado, 3 UARTs de hardware, barato, liga em menos de 1 s |
| Celular | Separado, no bolso | Bastão mais leve; celular protegido; serviço em primeiro plano mantém a coleta com a tela apagada |
| Interface no bastão | Display OLED 1,3", buzzer, vibração, LED | Operador sabe o que aconteceu sem tirar o celular do bolso |
| Fila offline | RAM + diário em flash (LittleFS) com ACK do app | Sobrevive a celular longe e a desligar o bastão |
| Histórico | microSD em CSV | Capacidade praticamente ilimitada; auditoria independente do celular |
| Hora | Enviada pelo celular ao conectar (`T`) | O ESP32 não tem relógio com bateria |
| Anti-duplicata | Firmware (janela de 3 s) + app (única por sessão) + aviso `D` | O brinco é lido várias vezes enquanto está no campo |
| Parser | Próprio (WL-134 com checksum, ASCII, AUTO), testado no PC | Formato confirmado contra a lib Rfid134; sem dependência LGPL |

## Arduino (ESP32) ou Raspberry Pi?

O ESP32 **é** a opção Arduino: programado no framework Arduino, com Bluetooth integrado. Arduino Uno
e Nano não têm Bluetooth. O Raspberry Pi só compensaria num bastão sem celular, com tela e 4G próprios.

| Critério | ESP32 + celular | Raspberry Pi Zero 2 W |
|---|---|---|
| Tempo para ligar | menos de 1 s | 20 a 40 s |
| Consumo | 0,1 a 0,25 W | 0,6 a 1,5 W |
| Desligar sem cuidado | sem risco | pode corromper o cartão |
| Custo | baixo | 3 a 5 vezes maior |

## Modos de operação

- **Contínuo:** o bastão lê o tempo todo.
- **Gatilho:** só lê com o gatilho apertado; economiza bateria e evita leituras acidentais.
- Troca: **toque longo de 1,5 s no gatilho**, ou pelo app. O modo fica salvo no bastão.

## Sinais do bastão

| Sinal | Significado |
|---|---|
| Bip curto | Leitura entregue ao celular |
| Bip longo | Leitura guardada no bastão (celular desconectado) |
| Dois bipes curtos | Brinco já lido nesta sessão (aviso do app) |
| Três bipes rápidos | Modo trocado |

## Limitações conhecidas da v0.3

- Lê só FDX-B (não HDX).
- Firmware e app ainda não compilados nos toolchains reais nem testados em hardware.
- Sem criptografia/autenticação além do pareamento BLE padrão.
- Fila offline limitada a 500 leituras pendentes (o histórico no SD não tem esse limite).
