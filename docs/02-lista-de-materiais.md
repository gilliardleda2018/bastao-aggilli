# 02 — Lista de materiais (BOM)

A mesma lista, em CSV para planilha, está em `hardware/bom.csv`. Preços e fornecedores ficam em branco
para preenchimento pela equipe.

## Eletrônica

| # | Item | Qtd | Especificação / observação |
|---|---|---|---|
| 1 | Placa ESP32 DevKit (ESP32-WROOM-32) | 1 | Com USB para gravação |
| 2 | Módulo leitor RFID FDX-B 134,2 kHz com saída UART | 1 | Confirmar no datasheet do fornecedor: tensão de alimentação, nível lógico (3,3 V ou 5 V), baud rate e formato do frame |
| 3 | Antena/bobina LF 134,2 kHz | 1 | Se não vier com o módulo; tamanho da bobina define o alcance |
| 4 | Bateria Li-ion 18650 protegida, 3,7 V, 2500 mAh ou mais | 1 | Mais suporte/holder |
| 5 | Módulo carregador TP4056 com proteção (USB-C) | 1 | Com DW01 + 8205A para proteção da bateria |
| 6 | Conversor step-up para 5 V (MT3608 ou equivalente) | 1 | Ajustar a saída para 5,0 V antes de ligar |
| 7 | Capacitor eletrolítico 470 µF / 10 V | 2 | Filtro da alimentação do módulo leitor |
| 8 | Capacitor cerâmico 100 nF | 4 | Desacoplamento |
| 9 | Resistores 100 kΩ | 2 | Divisor de tensão para medir a bateria |
| 10 | Resistores 10 kΩ e 20 kΩ | 1 cada | Divisor 5 V -> 3,3 V, só se o TX do módulo for 5 V |
| 11 | Resistores 1 kΩ | 2 | Base dos transistores |
| 12 | Resistor 330 Ω | 1 | LED externo |
| 13 | Transistor NPN BC547 ou 2N2222 | 2 | Aciona buzzer e motor de vibração |
| 14 | Diodo 1N4148 ou 1N4007 | 1 | Diodo de roda livre no motor de vibração |
| 15 | Buzzer ativo 5 V | 1 | |
| 16 | Motor de vibração 3 V (opcional) | 1 | |
| 17 | LED 5 mm | 1 | |
| 18 | Microchave com alavanca (gatilho) | 1 | |
| 19 | Chave liga/desliga | 1 | |
| 20 | Placa perfurada ou PCB, conectores e fios | 1 | |
| 21 | Adaptador USB-UART 3,3 V (CP2102 ou CH340) | 1 | Para a etapa de bancada |

## Mecânica

| # | Item | Qtd | Observação |
|---|---|---|---|
| 22 | Tubo de PVC ou fibra para a haste | 1 | Não usar metal perto da antena |
| 23 | Carcaça da empunhadura (impressa em 3D, PETG ou ASA) | 1 | ASA ou PETG resistem melhor a sol e umidade |
| 24 | Alojamento da antena na ponta (não metálico) | 1 | Resina epóxi ou carcaça impressa vedada |
| 25 | Suporte articulado para celular, com garra ajustável | 1 | Ângulo regulável para leitura sob sol |
| 26 | Cordão de segurança para o celular | 1 | |
| 27 | Parafusos de nylon ou inox (afastados da antena) | 1 kit | Metal próximo à bobina reduz o alcance |
| 28 | Termorretrátil, abraçadeiras, vedação (anéis o-ring, silicone) | 1 kit | |

## Para testes

| # | Item | Qtd | Observação |
|---|---|---|---|
| 29 | Brincos eletrônicos do rebanho (amostra) | 10 ou mais | Fundamental para validar o alcance |
| 30 | Celular Android (Android 8.0 ou superior, BLE) | 1 | |
| 31 | Multímetro e fonte de bancada | 1 | |
