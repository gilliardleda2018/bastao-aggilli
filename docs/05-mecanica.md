# 05 — Mecânica

Este documento reúne os requisitos e as recomendações para o corpo do bastão. Ainda não há modelos 3D
no repositório; a pasta `hardware/` pode receber os arquivos (STEP/STL/OpenSCAD) quando existirem.

## Decisão de material (v0.2)

- **Empunhadura e ponteira em plástico:** caixa ABS/PC IP65 comercial adaptada no protótipo de campo;
  impressão 3D em **ASA** (ou PETG) no piloto; injeção só com volume. PLA não (deforma ao sol).
- **Fibra de vidro só na haste**, em tubo pultrudado pronto. Fibra laminada à mão na caixa não compensa
  (artesanal, vedação difícil, sem ganho de leitura). **Fibra de carbono é proibida**: bloqueia a antena.
- **Módulo leitor na ponteira**, junto da bobina; ESP32, baterias e conversores na empunhadura.
- **Display pequeno na empunhadura** (OLED no protótipo, LCD de memória no produto) para usar o bastão
  com o celular no bolso; suporte do celular removível. Detalhes no guia de montagem (doc compartilhado).

## Partes

1. **Haste:** tubo de PVC ou fibra, comprimento a definir conforme o manejo (alcance até o brinco sem
   o operador entrar no tronco). Sem metal ao longo da haste.
2. **Cabeça da antena (ponta):** bobina LF em alojamento **não metálico**, vedado contra água e poeira.
3. **Empunhadura:** carcaça com o gatilho, a bateria, o ESP32, o leitor e a chave liga/desliga.
4. **Suporte do celular:** garra articulada e ajustável, fixada na empunhadura ou na haste, com cordão de segurança.

## Recomendações

- **Metal:** afaste parafusos, bateria, conversor e qualquer peça metálica da bobina (distância a validar em bancada).
  Metal próximo reduz o alcance dos leitores LF.
- **Vedação:** alvo mínimo IP65 para a empunhadura (poeira e jato de água); passagem de cabos com prensa-cabo ou vedação em silicone.
- **Material:** ASA ou PETG para peças impressas em ambiente externo (sol e umidade).
- **Ergonomia:** gatilho com o dedo indicador, celular na altura dos olhos, peso equilibrado
  (meta inicial: abaixo de cerca de 1 kg, a validar).
- **Tela ao sol:** suporte com ângulo ajustável; considerar película antirreflexo e brilho máximo no app.
- **Proteção do celular:** capa reforçada e cordão de segurança (queda no curral é provável).
- **Acesso à bateria:** tampa com vedação ou carga via USB-C externo.

## A definir

- Comprimento da haste e diâmetro da bobina (testes de alcance).
- Fixação do celular (garra universal comprada ou peça própria).
- Primeiro modelo 3D da empunhadura.
