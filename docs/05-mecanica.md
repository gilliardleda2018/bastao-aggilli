# 05 — Mecânica

## Decisão de material

- **Empunhadura e ponteira em plástico.** Protótipo: caixa Patola PBL-150 IP65 adaptada. Piloto:
  impressão 3D em **ASA** (ou PETG). Produto: injeção quando houver volume. PLA não (deforma ao sol).
- **Fibra de vidro só na haste**, em tubo pultrudado pronto. Fibra laminada à mão na caixa não
  compensa: artesanal, vedação difícil e sem ganho de leitura.
- **Fibra de carbono é proibida:** conduz e bloqueia o campo de 134,2 kHz.
- **Sem suporte de celular** (v0.3): o celular fica no bolso e recebe por Bluetooth.

## Partes

1. **Ponteira:** bobina + módulo leitor juntos, selados com epóxi ou silicone. Pela haste descem
   4 fios: VCC do leitor, GND, TX e um reserva.
2. **Haste:** tubo de fibra de vidro de 20 a 25 mm. Comprimento a definir com o operador (alcançar a
   orelha no tronco sem entrar nele). Nada de metal nos 15 cm finais.
3. **Empunhadura:** ESP32, baterias, TP4056, conversores, placa auxiliar (buzzer, transistores,
   divisores), módulo microSD, motor de vibração, gatilho sob capa de silicone, chave, USB-C com
   tampa de borracha, display atrás de janela de policarbonato na tampa.

## Disposição na caixa PBL-150 (vista de cima)

| Região | Peças |
|---|---|
| Metade esquerda, em cima | Suporte 2× 18650 |
| Metade esquerda, embaixo | MT3608 A e B lado a lado; TP4056 com o USB-C na parede |
| Metade direita, em cima | ESP32 em barra de pinos fêmea |
| Metade direita, embaixo | Placa auxiliar, módulo microSD com a fenda acessível por uma tampa vedada |
| Tampa | Display na janela; LED visível |
| Parede inferior | Gatilho e chave |
| Parede lateral | Prensa-cabo PG7 para a haste |

O motor de vibração fica colado na parede onde a mão segura.

## Requisitos

- Vedação alvo IP65: o-ring na tampa, PG7 na haste, furo do buzzer com membrana, tampa do SD vedada.
- Paredes de 3 mm, 4 perímetros na impressão, insertos de latão M3.
- Peso total abaixo de 1 kg (meta a validar), centro de gravidade na empunhadura.
- Teste de queda de 1 m sobre concreto.

## A definir

- Comprimento da haste e diâmetro da bobina (testes de alcance).
- Primeiro modelo 3D da empunhadura e da ponteira (STEP/STL vão para `hardware/`).
