# 06 — Plano de testes

## Etapa 0 — No computador (sem hardware)

- [ ] Push no GitHub: a aba **Actions** mostra as 3 etapas em verde (firmware, app, receptor).
      Baixe o APK em **Artifacts** e instale no celular.

- [ ] `cd firmware/test_host && make`: deve terminar com 0 falhas (parser, fila, diário em flash,
      gatilho, bateria, datas, CSV do cartão, display).
- [ ] `python -m unittest integracao/test_bastao_api.py`: receptor da API.
- [ ] `python tools/simulate_tag.py --stdout --ids 076000000001234`: gera o pacote WL-134.

## Etapa 1 — Bancada sem leitor

- [ ] Gravar o firmware; o monitor serial mostra `Bastao Aggilli fw 0.3.0 | ligacao N`.
- [ ] Display acende e mostra `-- CONT` e "aguardando".
- [ ] Com cartão no SD: aparece `[SD] cartao montado` e "SD" no display.
- [ ] Simulador (TX do CP2102 no GPIO16): `--count 20` gera 20 leituras; `--repeat 20` gera uma;
      `--corrupt` gera `frame descartado: CHECKSUM_INVALIDO`.

## Etapa 2 — Leitor real

1. Confirmar com o fornecedor dos brincos: 134,2 kHz, FDX-B, ISO 11784/11785.
2. Medir a tensão do TX do leitor antes de ligar no ESP32.
3. Aproximar um brinco: frame de 30 bytes (02 ... 03) e o ID igual ao impresso no brinco.
4. Medir o alcance nas duas antenas e várias orientações.

| Brinco | Antena | Orientação | Distância máx. (cm) | Observações |
|---|---|---|---|---|
| | | | | |

## Etapa 3 — Bastão + app

- [ ] "Escolher bastão" lista o bastão pelo nome (`Bastao-Aggilli-XXXX`) e conecta.
- [ ] Notificação fixa "Bastão conectado"; com a tela apagada e o app em segundo plano, 20 leituras
      chegam normalmente.
- [ ] "Bip" faz o bastão apitar; modo contínuo/gatilho troca pelo app e pelo toque longo de 1,5 s.
- [ ] Brinco repetido na sessão: dois bipes no bastão, "REPETIDO" no display e aviso no app.
- [ ] Voz ligada: o celular fala os 4 últimos dígitos; "repetido" quando for o caso.
- [ ] Display mostra a contagem da sessão enviada pelo app.

## Etapa 4 — Nada se perde

- [ ] Bluetooth do celular desligado: 20 leituras (bip longo, "GUARDADO" no display); religar:
      as 20 chegam com o horário certo e o app avisa "20 leituras recuperadas".
- [ ] Bluetooth desligado, 10 leituras, **desligar e religar o bastão**, reconectar: as 10 chegam.
- [ ] Tirar a energia no meio de uma leitura (desligar a chave enquanto lê): ao religar, nada além da
      leitura em curso se perde.
- [ ] Mais de 500 leituras offline: o app avisa quantas foram descartadas da fila (o SD guarda todas).
- [ ] Cartão SD: arquivo `/leituras/AAAA-MM-DD.csv` com uma linha por leitura e a data/hora local.
      Leituras feitas antes de o celular informar a hora vão para `sem-data-ligacao-NNNN.csv`.
- [ ] Remover o cartão com o bastão ligado: display mostra "SD!" e o bastão continua lendo; ao
      recolocar, volta a gravar em até 10 s.

## Etapa 5 — App e sincronização

- [ ] Atualização do app 0.2 para 0.3 mantém as leituras e a configuração.
- [ ] Modo avião, leituras, app fechado, rede de volta: as leituras chegam ao Aggilli Agro.
- [ ] Token errado mostra erro e não fica tentando para sempre.
- [ ] CSV exportado abre certo na planilha.

## Etapa 6 — Campo

- [ ] Lote de contagem conhecida: taxa de leitura (lidos / presentes) dentro da meta.
- [ ] Tronco de metal: alcance e falhas anotados.
- [ ] Turno inteiro com uma carga.
- [ ] Display legível ao sol.
- [ ] Chuva, poeira e queda de 1 m.
