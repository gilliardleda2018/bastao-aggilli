# 07 — Roadmap

## v0.1

- [x] Documentação inicial, firmware ESP32 (UART, anti-duplicata, BLE, gatilho), app Android (BLE, SQLite, CSV)

## v0.2

- [x] Parser WL-134 com checksum + modo AUTO; fim de pacote 0x07
- [x] Fila offline com confirmação (ACK) e reenvio ao reconectar
- [x] Sincronização automática com o Aggilli Agro (WorkManager, UUID, lotes) e receptor de referência
- [x] Testes de host e simulador de brinco

## v0.3 (atual)

- [x] Diário da fila em flash (LittleFS): sobrevive a desligar o bastão, resistente a queda de energia
- [x] Cartão microSD com histórico completo em CSV, um arquivo por dia
- [x] Display OLED 1,3": status, último brinco, contagem da sessão, avisos
- [x] Toque longo no gatilho troca o modo; modo salvo na memória
- [x] Hora e fuso enviados pelo celular; leituras datadas mesmo feitas offline
- [x] Nome Bluetooth único por bastão; app escolhe o bastão pelo nome
- [x] App com serviço em primeiro plano (coleta com a tela apagada), voz, aviso de repetido no bastão
- [x] 125 verificações automáticas da lógica do firmware

## Antes de vender

- [ ] Primeiro build real (PlatformIO e Android Studio) e testes das etapas 1 a 6
- [ ] Publicar a rota `/api/v1/bastao/leituras` no Aggilli Agro e emitir tokens por bastão
- [ ] Carcaça própria em ASA (empunhadura + ponteira) e teste de queda/chuva
- [ ] Homologação Anatel do produto com rádio Bluetooth (ver `docs/10-producao-e-preco.md`)

## v0.4

- [ ] Baixar o cadastro do Aggilli Agro: mostrar lote/último peso e alertas (apartação) ao ler
- [ ] Tipos de manejo por sessão (contagem, pesagem, vacinação, apartação)
- [ ] Integração com balança (peso junto da leitura)
- [ ] Atualização do firmware pelo app (OTA por BLE)
- [ ] GPS por sessão

## Futuro

- [ ] Leitura HDX (outro módulo leitor)
- [ ] PCB própria (reduz custo e tamanho)
- [ ] Pareamento seguro e autenticação do bastão
