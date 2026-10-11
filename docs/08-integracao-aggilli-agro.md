# 08 — Integração com o Aggilli Agro (sincronização automática)

O objetivo: o operador lê os brincos no curral e as leituras aparecem sozinhas no Aggilli Agro web,
sem exportar CSV nem copiar nada.

```
Brinco -> Bastão (ESP32) --BLE--> App Android --HTTPS--> Aggilli Agro (API) -> banco -> módulo web
                │                     │
         fila em flash até o    SQLite: guarda tudo offline e envia
         app confirmar (ACK)    quando houver internet (WorkManager)
```

## Como o app sincroniza

- Cada leitura recebe um **UUID** no celular e fica marcada como pendente no SQLite. O horário é o da
  leitura no curral (o bastão data as leituras com a hora enviada pelo celular), não o do envio.
- Após cada leitura nova, o app agenda um envio em ~10 s (agrupa as leituras em sequência).
- Sem internet (comum no curral), o envio espera e o Android repete sozinho quando a rede voltar,
  **mesmo com o app fechado** (WorkManager, com backoff exponencial).
- A cada 15 min há uma varredura de segurança das pendências.
- O servidor confirma pelos UUIDs; só então a leitura é marcada como sincronizada.
  Reenviar o mesmo lote não duplica nada (o UUID é a chave).
- Token recusado (401/403) ou URL errada (404) param as tentativas e aparecem em vermelho na tela,
  até a configuração ser corrigida.

Configuração no app: **Configurar**, no cartão "Aggilli Agro": endereço (https), ID da fazenda,
token do dispositivo, nome do bastão e "Enviar automaticamente".

## Contrato da API

```
POST {endereço}/api/v1/bastao/leituras
Authorization: Bearer <token do dispositivo>
Content-Type: application/json
```

```json
{
  "fazenda_id": "fazenda-sao-joao",
  "dispositivo": "Bastao 01",
  "app_versao": "0.3.0",
  "leituras": [
    {
      "uuid": "6b0d6a8e-2f7c-4d36-9c1e-0f4a8d2e7b51",
      "sessao": "20261011-114800",
      "brinco": "076000000001234",
      "data_hora": "2026-10-11T11:48:00-03:00",
      "recuperada": false
    }
  ]
}
```

Resposta `200`:

```json
{"aceitas": ["6b0d6a8e-..."], "rejeitadas": [{"uuid": "...", "motivo": "brinco deve ter 15 dígitos ISO 11784"}]}
```

| Campo | Regra |
|---|---|
| `uuid` | Gerado no celular; chave de idempotência (reenvio = aceita sem duplicar) |
| `brinco` | 15 dígitos ISO 11784 (3 de país + 12 nacional, nacional até 274877906943) |
| `data_hora` | ISO-8601 **com fuso**; é o horário da leitura no curral, não o do envio |
| `recuperada` | `true` quando a leitura veio da fila offline do bastão |
| `sessao` | Agrupa um manejo (ex.: um lote passando no tronco) |

Lotes de até 200 leituras (o servidor aceita até 1000). Códigos: `200` ok, `400` corpo inválido,
`401` token ausente/inválido ou de outra fazenda, `5xx` o app tenta de novo depois.

## Lado servidor (`integracao/`)

`integracao/bastao_api.py` implementa esse contrato:

- `gravar_lote(conn, payload)`: valida e grava (núcleo, só biblioteca padrão).
- `criar_router_fastapi(get_conn, tokens)`: rota pronta para a API REST do Aggilli Agro.
- `python integracao/bastao_api.py --porta 8080`: servidor de teste sem dependências.
- Testes: `python -m unittest integracao/test_bastao_api.py` (núcleo e HTTP ponta a ponta).

Tabela `bastao_leituras` (SQL em `SCHEMA`): uma linha por leitura, chave `uuid`. O Aggilli Agro
cruza `(fazenda_id, brinco)` com o cadastro de animais para mostrar a passagem no módulo web.

O Streamlit não expõe endpoints REST, então a rota precisa rodar num serviço de API ao lado do
Streamlit (FastAPI na mesma instância/AWS, atrás do mesmo domínio), gravando no mesmo banco que o
Streamlit lê.

## A definir com o Aggilli Agro

- [ ] Onde a API roda (mesmo servidor do Streamlit, path `/api`) e qual banco (PostgreSQL/RDS?).
- [ ] Emissão dos tokens por dispositivo (tela de admin no Aggilli Agro) e revogação.
- [ ] Brinco sem animal cadastrado: criar "animal pendente de cadastro" ou só registrar a leitura?
- [ ] Sentido inverso (Agro -> celular): baixar o cadastro para mostrar nome/lote/peso no bastão (v0.3).
