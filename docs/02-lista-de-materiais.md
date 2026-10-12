# 02 — Lista de materiais (BOM)

A mesma lista, em CSV para planilha, está em `hardware/bom.csv`. Preços de referência (outubro de
2026) e lojas estão na página ilustrada do projeto; os preços mudam, cote antes de comprar.

Custo estimado de **um bastão: cerca de R$ 700 em peças** (lista completa com reservas: cerca de
R$ 1.050). Para começar na bancada: leitores, ESP32 e adaptador USB-TTL, cerca de R$ 490.

## Eletrônica

| # | Item | Qtd | Especificação / observação |
|---|---|---|---|
| 1 | Módulo leitor RFID FDX-B 134,2 kHz, saída TTL | 1 (+1 reserva) | WL-134/WL134A, 5 a 9 V, 9600 baud, pacote de 30 bytes; vem com antenas de 65 e 130 mm |
| 2 | ESP32 DevKit 38 pinos (ESP32-WROOM-32) | 1 (+1) | Não usar ESP32-C3/S2: o firmware é para o ESP32 clássico |
| 3 | Display OLED 1,3" SH1106 128×64 I2C | 1 | 4 pinos (VCC, GND, SCL, SDA) |
| 4 | Módulo leitor de cartão microSD, SPI, 3,3 V | 1 | Se for módulo de 5 V com regulador, alimentar em 5 V |
| 5 | Cartão microSD 8 a 32 GB, FAT32, classe 10 | 1 | Formatar em FAT32 antes de usar |
| 6 | Bateria Li-ion 18650 com proteção (Samsung 25R ou similar) | 2 | Em paralelo; mesma tensão antes de unir |
| 7 | Suporte 2× 18650 em paralelo | 1 | |
| 8 | Carregador TP4056 USB-C com proteção (DW01 + 8205A) | 1 | |
| 9 | Conversor step-up MT3608 | 2 | A: tensão do leitor (9,0 V ou 5,0 V). B: 5,0 V para o ESP32 |
| 10 | Chave liga/desliga mini 2 A | 1 | |
| 11 | Micro switch com alavanca (gatilho) | 1 | |
| 12 | Buzzer ativo 5 V | 1 | |
| 13 | Motor de vibração moeda 3 V | 1 | |
| 14 | LED 5 mm | 1 | Opcional (há LED na placa) |
| 15 | Transistor NPN BC547 | 2 | Buzzer e vibração |
| 16 | Diodo 1N4148 | 1 | Roda livre do motor |
| 17 | Resistores | 1 kit | 2× 100 kΩ, 10 kΩ, 20 kΩ (ou 22 k + 12 k), 2× 1 kΩ, 330 Ω |
| 18 | Capacitores | 1 kit | 2× 470 µF/16 V, 4× 100 nF |
| 19 | Placa perfurada, barra de pinos fêmea, fio 22 AWG, JST, termorretrátil | 1 kit | |

## Mecânica

| # | Item | Qtd | Observação |
|---|---|---|---|
| 20 | Caixa Patola PBL-150 IP65 (150 × 100 × 60 mm) | 1 | Empunhadura do protótipo |
| 21 | Haste: cano de PVC soldável marrom 25 mm (parede ~1,7 mm, ~21,6 mm por dentro) | 1,5 m | Protótipo; corte de 60 a 80 cm depois de testar com o operador. Produto: fibra de vidro. Nada de metal ou fibra de carbono |
| 22 | Ponteira: cano de esgoto 75 mm (pedaço de 8 a 10 cm) + 2 tampões 75 mm + adesivo de PVC + Durepoxi | 1 | A bobina de 65 mm cabe dentro; um tampão furado em 25 mm recebe a haste |
| 23 | Adaptador soldável curto 25 mm × ¾" + bucha e arruela ¾", silicone neutro, parafusos inox M3, janela de policarbonato | 1 kit | O adaptador prende a haste na caixa; prensa-cabo PG7 opcional |

## Bancada

| # | Item | Qtd | Observação |
|---|---|---|---|
| 24 | Adaptador USB-TTL CP2102 3,3 V | 1 | Simulador de brinco e captura do leitor |
| 25 | Brincos FDX-B do rebanho | 10 | Pedir à fazenda |
| 26 | Celular Android 8.0+ com BLE | 1 | |
| 27 | Multímetro, ferro de solda, fonte de bancada | 1 | |

O suporte de celular na haste saiu do projeto na v0.3: o celular fica no bolso.

## Onde comprar

Links pesquisados em outubro de 2026 em lojas brasileiras (também na coluna `link_compra` de
`hardware/bom.csv`). Estoque e preço mudam: confira antes de fechar. Concentrar a compra na
Eletrogate e na Usinainfo economiza frete.

### Etapa 1: bancada

| Item | Onde | Atenção |
|---|---|---|
| ESP32 DevKit 38 pinos (×2) | [Usinainfo: DevKitC V4 WROOM-32D](https://www.usinainfo.com.br/esp32/esp32-devkitc-v4-wroom-32d-38-pinos-cp2102-micro-usb-8682.html) · [WROOM-32E](https://www.usinainfo.com.br/esp32/esp32-wroom-32e-wifi-e-bluetooth-38-pinos-com-cp2102-9109.html) | Placa com USB e duas fileiras de pinos. **Não** o módulo WROOM avulso (sem USB), nem C3/S2/S3/C6 |
| Leitor WL-134 + antenas (×2) | [Eletrônica da China: WL134A 5–9 V](https://eletronicadachina.com.br/produto/universal-1342khz-leitor-rfid-modulo-fdx-b-hdx-iso1784-85-tag-tubo-de-vidro-antena-sem-fio-modulo-wl134a-5v-9v) | Confirmar que vem com as antenas e saída TTL |
| Adaptador USB-TTL CP2102 | [Usinainfo: CP2102 3,3 V e 5 V](https://www.usinainfo.com.br/conversores-de-sinal/modulo-conversor-usb-para-ttl-cp2102--4470.html) | |
| Protoboard 830 + jumpers | [MakerHero: Kit Protoboard Intermediate](https://www.makerhero.com/produto/kit-protoboard-intermediate/) | Fora da BOM original; vem com jumpers macho-fêmea. Para testar antes de soldar |

### Etapa 2: eletrônica

| Item | Onde | Atenção |
|---|---|---|
| Display OLED 1,3" SH1106 | [Mercado Livre: 1.3" SH1106 I2C](https://produto.mercadolivre.com.br/MLB-1562271568-13-branco-sh1106-iic-i2c-serial-128x64-oled-lcd-display-scr-_JM) · [Curto Circuito](https://curtocircuito.com.br/display-oled-128x64-px-1-3-4-pin-azul.html) | Tem que ser 1,3" SH1106. O de 0,96" (SSD1306) não serve sem mudar o firmware |
| Módulo microSD 3,3 V | [Usinainfo: mini leitor microSD SPI 3,3 V](https://www.usinainfo.com.br/memorias-e-armazenamento/mini-sd-card-esp32-mini-leitor-micro-sd-card-esp32-5051.html) | |
| Cartão microSD 8–32 GB | Qualquer SanDisk ou Kingston classe 10 | Formatar em FAT32 |
| Bateria 18650 Samsung 25R (×2) | [Mercado Livre: Samsung 25R](https://produto.mercadolivre.com.br/MLB-1874762222-bateria-samsung-18650-25r-36v-2500mah-inr18650-original-_JM) | Muita falsificação: vendedor com boa reputação |
| Suporte 2× 18650 | [Usinainfo: suporte 2× 18650](https://www.usinainfo.com.br/suporte-para-pilhas/suporte-para-2-baterias-18650-litio-5073.html) | Quase todo suporte duplo vem **em série** (7,4 V). Medir; o projeto usa **paralelo** (3,7 V) |
| Carregador TP4056 USB-C | [Curto Circuito: TP4056 USB-C com proteção](https://curtocircuito.com.br/carregador-bateria-lithium-1a-com-protecao-usb-c-tp4056.html) | Na foto, além do TP4056, os chips DW01A e 8205A |
| Step-up MT3608 (×2) | [Eletrogate: MT3608](https://www.eletrogate.com/regulador-de-tensao-ajustavel-mt3608-auto-boost-step-up) | |
| Chave liga/desliga | [Eletrogate: mini gangorra KCD11](https://www.eletrogate.com/mini-chave-gangorra-2-terminais) | |
| Gatilho (micro switch) | [Baú da Eletrônica: KW10-B com haste](https://www.baudaeletronica.com.br/produto/chave-micro-switch-kw10-b-com-haste.html) | |
| Buzzer ativo 5 V | [Eletrogate: buzzer ativo 5 V](https://www.eletrogate.com/buzzer-ativo-5v) | Ativo, não passivo |
| Motor de vibração moeda | [Usinainfo: vibracall redondo MV30](https://www.usinainfo.com.br/motor-dc/motor-vibracall-redondo-para-projetos-maker-diy-mv30-5593.html) | |
| BC547 (×2) | [Eletrogate: BC547](https://www.eletrogate.com/transistor-npn-bc547) | |
| 1N4148 | [Eletrogate: 1N4148](https://www.eletrogate.com/diodo-1n4148) | |
| Resistores | [Eletrogate: kit 525 resistores](https://www.eletrogate.com/kit-525-resistores-17-valores) | Conferir se tem 100k, 22k, 12k, 10k, 1k e 330 Ω |
| Capacitor 470 µF 16 V | [Usinainfo: 470 µF 16 V](https://www.usinainfo.com.br/capacitor-eletrolitico/capacitor-eletrolitico-470uf-16v-kit-com-10-unidades-3054.html) | |
| Capacitor 100 nF | [Eletrogate: cerâmico 100 nF](https://www.eletrogate.com/capacitor-disco-ceramico-100nf-x-50v) | Marcação "104" |
| Placa perfurada | [Eletrogate: fenolite 10×10 cm](https://www.eletrogate.com/placa-fenolite-perfurada-10x10cm) | |
| Barra de pinos fêmea | [Eletrogate: barra fêmea 1×40](https://www.eletrogate.com/barra-de-pinos-femea-1x40-180) | Duas, para encaixar o ESP32 |
| Termorretrátil | [Eletrogate: kit 328 peças](https://www.eletrogate.com/kit-tubo-termo-retratil-com-328-unidades-em-5-cores) | |
| Fio 22 ou 20 AWG | Loja de material elétrico | Vermelho e preto, flexível |

### Etapa 3: mecânica

| Item | Onde | Atenção |
|---|---|---|
| Caixa da empunhadura | [Mercado Livre: caixa IP65 140×100×60 mm](https://www.mercadolivre.com.br/kit-com-10-caixas-ip65-branca-140x100x60mm-proteco-externa/p/MLB42124685) | Patola PBL-150 não encontrada à venda; esta tem medida próxima e borracha na tampa (kit de 10) |
| Haste | Cano de PVC soldável marrom 25 mm (1,5 m), loja de material de construção | Sem alumínio na ponteira e nos 15 cm finais (ver `docs/11-guia-de-montagem.pdf`) |
| Ponteira | Cano de esgoto 75 mm (pedaço de 10 cm), 2 tampões 75 mm, adesivo de PVC, Durepoxi | Furar um tampão com serra-copo de 25 mm |
| Fixação na caixa | Adaptador soldável curto 25 mm × ¾", bucha e arruela de eletroduto ¾", silicone neutro | Furo de 27 mm na parede da caixa |
| Prensa-cabo PG7 (opcional) | [Eletrogate: PG7](https://www.eletrogate.com/prensa-cabo-pg7-branco) | Só se os fios vierem por cabo separado |
| Parafusos M3 inox, policarbonato | Ferragem | |
