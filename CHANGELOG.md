# Histórico de versões

## 0.3.0 — 2026-10-11

Bastão independente do celular na tela, com memória própria.

**Firmware**
- Diário da fila em flash (LittleFS): leituras pendentes sobrevivem a desligar o bastão; registro
  cortado por falta de energia é descartado sem perder os anteriores; compactação automática.
- Cartão microSD (SPI): histórico de todas as leituras em CSV, um arquivo por dia
  (`/leituras/AAAA-MM-DD.csv`); remonta sozinho se o cartão for trocado.
- Display OLED 1,3" SH1106: Bluetooth, modo, SD, bateria, último brinco, contagem da sessão, avisos
  (GUARDADO, REPETIDO, MODO).
- Gatilho com filtro de ruído; toque longo (1,5 s) troca contínuo/gatilho; modo salvo na NVS.
- Padrões de sinal: curto (entregue), longo (guardado), duplo (repetido), triplo (modo).
- Hora e fuso recebidos do celular (`T`); leituras datadas, inclusive as feitas antes da conexão.
- Comandos `C` (contagem no display) e `D` (aviso de repetido).
- Nome Bluetooth único por bastão (`Bastao-Aggilli-XXXX`), nome na resposta ao scan.
- `READING` com `epoch`; `STATUS` com bateria em %, estado do SD e número da ligação.
- Partição `min_spiffs` e dependência U8g2.

**App**
- `BastaoController`: lógica fora da tela; serviço em primeiro plano com notificação fixa mantém a
  coleta com a tela apagada; botão "Desconectar" na notificação.
- Escolha do bastão pelo nome, com intensidade do sinal; lembra o último bastão.
- Voz (TextToSpeech pt-BR): últimos 4 dígitos e "repetido".
- Envia hora/fuso, contagem da sessão e aviso de repetido ao bastão.
- Mostra estado do cartão SD e bateria em % vinda do bastão.

**Testes e documentação**
- Novos testes de host: diário em flash, gatilho, bateria, datas, CSV, display (125 verificações).
- Protocolo BLE v3, manual do operador, produção e preço, documentação revisada.

## 0.2.0 — 2026-10-11

- Parser WL-134 com checksum e modo AUTO; fim de pacote 0x07.
- Fila offline com confirmação (ACK) e reenvio; idade da leitura.
- Sincronização automática com o Aggilli Agro; receptor de referência da API.
- Testes de host, simulador de brinco; alimentação do leitor em 9 V com dois conversores.

## 0.1.0

- Estrutura inicial: firmware ESP32, app Android, documentação.
