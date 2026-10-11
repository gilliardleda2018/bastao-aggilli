# 07 — Roadmap

## v0.1 (atual)

- [x] Documentação inicial e lista de materiais
- [x] Firmware do ESP32 (leitura UART, anti-duplicata, BLE, gatilho, feedback)
- [x] App Android (conexão BLE, lista de leituras, SQLite, exportação CSV)
- [x] Utilitário de bancada para capturar o frame do módulo leitor
- [ ] Validar em bancada com os brincos reais
- [ ] Compilar e testar o app e o firmware

## v0.2 (atual)

- [x] Parser WL-134 (pacote binário de 30 bytes com checksum) + modo AUTO; ASCII15 mais rígido
- [x] Fila offline no firmware com confirmação (ACK) do app e reenvio ao reconectar
- [x] Testes de host do parser e da fila (`firmware/test_host`, 55 verificações)
- [x] Simulador de brinco por serial (`tools/simulate_tag.py`) para bancada sem módulo
- [x] App: sincronização automática com o Aggilli Agro (WorkManager, UUID, lotes, tela de configuração)
- [x] Receptor de referência da API com testes (`integracao/`)
- [ ] Ajustar o parser ao formato real do módulo comprado (bancada)
- [ ] Compilar firmware e app e testar em hardware
- [ ] Publicar a rota `/api/v1/bastao/leituras` no Aggilli Agro e emitir tokens por dispositivo
- [ ] Primeiro modelo 3D da empunhadura e do suporte do celular

## v0.3

- [ ] Fila offline gravada em flash (sobrevive a desligar o bastão)
- [ ] Baixar o cadastro do Aggilli Agro: mostrar nome/lote/último peso ao ler o brinco
- [ ] Tipos de manejo na sessão (contagem, pesagem, vacinação, apartação)
- [ ] Registro de GPS por sessão
- [ ] Tela de configuração do bastão no app (janela anti-duplicata)

## Futuro

- [ ] Modo USB OTG como alternativa ao BLE
- [ ] PCB própria
- [ ] Atualização de firmware via OTA
- [ ] Pareamento seguro e autenticação do bastão
