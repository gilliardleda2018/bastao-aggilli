# 06 — Plano de testes

## Etapa 1 — Bancada (obrigatória antes de qualquer outra)

Objetivo: confirmar que o módulo lê os brincos do rebanho e descobrir o formato exato do frame.

1. Confirme com o fornecedor dos brincos: frequência (134,2 kHz), tipo (FDX-B ou HDX) e norma (ISO 11784/11785).
2. Ligue o módulo leitor a um adaptador USB-UART 3,3 V (TX do módulo -> RX do adaptador; GND comum).
   Atenção ao nível lógico (ver `docs/03-ligacoes-eletricas.md`).
3. Rode `python tools/serial_hexdump.py <porta> <baud>` e aproxime um brinco.
4. Registre o frame recebido (bytes em hexadecimal e ASCII) e compare com o datasheet do módulo.
5. Ajuste o parser em `firmware/src/main.cpp` (`parseFrame`) ou use `PARSER_MODE 1` (hex bruto) até o formato estar claro.
6. Meça o alcance com a antena escolhida, em várias orientações do brinco.

Registre os resultados:

| Brinco | Orientação | Distância máx. de leitura (cm) | Observações |
|---|---|---|---|
| | | | |

### Sem módulo e sem brinco (antes da compra)

- Testes do parser e da fila no PC: `cd firmware/test_host && make`.
- ESP32 + simulador: TX de um adaptador USB-UART no GPIO16 e
  `python tools/simulate_tag.py <porta> --count 20`. Teste também `--repeat 20` (anti-duplicata)
  e `--corrupt` (deve aparecer `CHECKSUM_INVALIDO` na serial e nada no app).

## Etapa 2 — Firmware + BLE

- [ ] O ESP32 recebe o frame e imprime o ID na serial USB.
- [ ] Anti-duplicata: manter o brinco no campo gera uma única notificação em `DEDUP_MS`.
- [ ] Gatilho: no modo 1, só lê com o gatilho apertado.
- [ ] Reconexão BLE: desligar/ligar o bastão e o Bluetooth do celular.
- [ ] Distância BLE entre bastão e celular (alcance real no suporte).
- [ ] Fila offline: desligar o Bluetooth do celular, ler 20 brincos (bip longo), religar: as 20 chegam com o horário certo.
- [ ] Fila cheia: mais de `QUEUE_CAPACITY` leituras offline gera o aviso de descartadas no app.

## Etapa 3 — App

- [ ] Permissões Bluetooth em Android 8, 11 e 12 ou superior.
- [ ] Gravação no SQLite e persistência após fechar o app.
- [ ] Leituras repetidas na mesma sessão marcadas como duplicadas.
- [ ] Exportação CSV abre corretamente na planilha.
- [ ] Nova sessão zera a lista sem apagar os dados anteriores.
- [ ] Atualização da v0.1 para a v0.2 mantém as leituras antigas (migração do SQLite).
- [ ] Sincronização: modo avião, ler, fechar o app, voltar a rede: as leituras chegam ao servidor sozinhas.
- [ ] Token errado mostra erro na tela e não fica tentando para sempre.

## Etapa 4 — Campo

- [ ] Leitura no tronco/brete (metal por perto): alcance e taxa de falha.
- [ ] Taxa de leitura: animais lidos / animais presentes, em um lote de contagem conhecida.
- [ ] Autonomia da bateria em um turno completo (horas por carga).
- [ ] Sol forte: legibilidade da tela.
- [ ] Poeira, chuva e queda de cerca de 1 m (proteção da carcaça e do celular).
- [ ] Sem sinal de celular: modo offline e exportação.

Anote a meta de taxa de leitura aceitável com a Aggilli antes da etapa 4.
