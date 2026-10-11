"""Receptor das leituras do bastão no Aggilli Agro (lado servidor).

Contrato: docs/08-integracao-aggilli-agro.md

    POST /api/v1/bastao/leituras
    Authorization: Bearer <token do dispositivo>
    {"fazenda_id": "...", "dispositivo": "...", "app_versao": "0.2.0",
     "leituras": [{"uuid": "...", "sessao": "...", "brinco": "076000000001234",
                   "data_hora": "2026-10-11T11:48:00-03:00", "recuperada": false}]}
    -> 200 {"aceitas": ["uuid", ...], "rejeitadas": [{"uuid": "...", "motivo": "..."}]}

O núcleo (validar_lote / gravar_lote) usa só a biblioteca padrão e qualquer conexão DB-API
com paramstyle "?" (sqlite3). Para PostgreSQL troque PLACEHOLDER por "%s".

Três formas de uso:
  1. Importar gravar_lote() dentro do backend do Aggilli Agro.
  2. FastAPI: app.include_router(criar_router_fastapi(get_conn, tokens))
  3. Servidor de teste sem dependências: python integracao/bastao_api.py --porta 8080
"""
from __future__ import annotations

import hmac
import json
import re
import sqlite3
from datetime import datetime
from typing import Callable, Dict, List, Tuple

PLACEHOLDER = "?"
MAX_LEITURAS_POR_LOTE = 1000
ISO_NATIONAL_MAX = 274877906943
_RE_UUID = re.compile(r"^[0-9a-fA-F-]{8,64}$")

SCHEMA = """
CREATE TABLE IF NOT EXISTS bastao_leituras (
    uuid        TEXT PRIMARY KEY,           -- gerado no celular: torna o envio idempotente
    fazenda_id  TEXT NOT NULL,
    dispositivo TEXT,
    sessao      TEXT,
    brinco      TEXT NOT NULL,              -- 15 dígitos ISO 11784 (país + nacional)
    data_hora   TEXT NOT NULL,              -- ISO-8601 com fuso, horário da leitura no curral
    recuperada  INTEGER NOT NULL DEFAULT 0, -- 1 = veio da fila offline do bastão
    recebido_em TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_bastao_fazenda_brinco ON bastao_leituras (fazenda_id, brinco);
CREATE INDEX IF NOT EXISTS idx_bastao_fazenda_data ON bastao_leituras (fazenda_id, data_hora);
"""


class ErroAutorizacao(Exception):
    pass


class ErroPayload(Exception):
    pass


# ---------------------------------------------------------------- validação

def brinco_valido(brinco: str) -> bool:
    if not (isinstance(brinco, str) and len(brinco) == 15 and brinco.isdigit()):
        return False
    return int(brinco[3:]) <= ISO_NATIONAL_MAX


def _data_valida(texto: str) -> bool:
    try:
        dt = datetime.fromisoformat(texto)
    except (TypeError, ValueError):
        return False
    return dt.tzinfo is not None


def autorizar(cabecalho_auth: str | None, fazenda_id: str, tokens: Dict[str, str]) -> None:
    """tokens: {token: fazenda_id}. Cada bastão/celular recebe um token ligado a uma fazenda."""
    if not cabecalho_auth or not cabecalho_auth.startswith("Bearer "):
        raise ErroAutorizacao("token ausente")
    recebido = cabecalho_auth[len("Bearer "):].strip()
    for token, fazenda in tokens.items():
        if hmac.compare_digest(recebido, token):
            if fazenda != fazenda_id:
                raise ErroAutorizacao("token não pertence a esta fazenda")
            return
    raise ErroAutorizacao("token inválido")


def validar_lote(payload: dict) -> Tuple[List[dict], List[dict]]:
    """Separa as leituras válidas das rejeitadas. Lança ErroPayload se o lote for inválido."""
    if not isinstance(payload, dict):
        raise ErroPayload("corpo deve ser um objeto JSON")
    if not str(payload.get("fazenda_id") or "").strip():
        raise ErroPayload("fazenda_id obrigatório")
    leituras = payload.get("leituras")
    if not isinstance(leituras, list):
        raise ErroPayload("leituras deve ser uma lista")
    if len(leituras) > MAX_LEITURAS_POR_LOTE:
        raise ErroPayload(f"máximo de {MAX_LEITURAS_POR_LOTE} leituras por lote")

    validas, rejeitadas = [], []
    for item in leituras:
        uid = str(item.get("uuid", "")) if isinstance(item, dict) else ""
        if not _RE_UUID.match(uid):
            rejeitadas.append({"uuid": uid, "motivo": "uuid inválido"})
        elif not brinco_valido(item.get("brinco")):
            rejeitadas.append({"uuid": uid, "motivo": "brinco deve ter 15 dígitos ISO 11784"})
        elif not _data_valida(item.get("data_hora")):
            rejeitadas.append({"uuid": uid, "motivo": "data_hora deve ser ISO-8601 com fuso"})
        else:
            validas.append(item)
    return validas, rejeitadas


# ---------------------------------------------------------------- gravação

def criar_tabela(conn) -> None:
    cur = conn.cursor()
    for stmt in SCHEMA.strip().split(";"):
        if stmt.strip():
            cur.execute(stmt)
    conn.commit()


def gravar_lote(conn, payload: dict) -> dict:
    """Grava as leituras válidas. Reenvios (mesmo uuid) são aceitos sem duplicar."""
    validas, rejeitadas = validar_lote(payload)
    agora = datetime.now().astimezone().isoformat(timespec="seconds")
    p = PLACEHOLDER
    sql = (
        "INSERT INTO bastao_leituras (uuid, fazenda_id, dispositivo, sessao, brinco, data_hora, "
        f"recuperada, recebido_em) VALUES ({p},{p},{p},{p},{p},{p},{p},{p}) "
        "ON CONFLICT (uuid) DO NOTHING"
    )
    cur = conn.cursor()
    for it in validas:
        cur.execute(sql, (
            it["uuid"], str(payload["fazenda_id"]), str(payload.get("dispositivo") or "")[:120],
            str(it.get("sessao") or "")[:40], it["brinco"], it["data_hora"],
            1 if it.get("recuperada") else 0, agora,
        ))
    conn.commit()
    return {"aceitas": [it["uuid"] for it in validas], "rejeitadas": rejeitadas}


def processar(conn, cabecalho_auth: str | None, payload: dict, tokens: Dict[str, str]) -> Tuple[int, dict]:
    """Fluxo completo: autoriza, valida e grava. Retorna (status_http, corpo)."""
    try:
        if not isinstance(payload, dict):
            raise ErroPayload("corpo deve ser um objeto JSON")
        autorizar(cabecalho_auth, str(payload.get("fazenda_id") or ""), tokens)
        return 200, gravar_lote(conn, payload)
    except ErroAutorizacao as e:
        return 401, {"erro": str(e)}
    except ErroPayload as e:
        return 400, {"erro": str(e)}


# ---------------------------------------------------------------- FastAPI

def criar_router_fastapi(get_conn: Callable, tokens: Dict[str, str]):
    """Router para a API REST do Aggilli Agro:  app.include_router(criar_router_fastapi(...))"""
    from fastapi import APIRouter, Header, Request
    from fastapi.responses import JSONResponse

    router = APIRouter()

    @router.post("/api/v1/bastao/leituras")
    async def receber(request: Request, authorization: str | None = Header(default=None)):
        try:
            payload = await request.json()
        except Exception:
            return JSONResponse({"erro": "JSON inválido"}, status_code=400)
        status, corpo = processar(get_conn(), authorization, payload, tokens)
        return JSONResponse(corpo, status_code=status)

    return router


# ---------------------------------------------------------------- servidor de teste

def servidor_teste(porta: int, banco: str, tokens: Dict[str, str]):
    """Servidor HTTP só com a biblioteca padrão, para testes de bancada (sem HTTPS)."""
    from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
    import threading

    conn = sqlite3.connect(banco, check_same_thread=False)
    criar_tabela(conn)
    trava = threading.Lock()

    class Handler(BaseHTTPRequestHandler):
        def _responder(self, status: int, corpo: dict) -> None:
            dados = json.dumps(corpo, ensure_ascii=False).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(dados)))
            self.end_headers()
            self.wfile.write(dados)

        def do_POST(self):  # noqa: N802
            if self.path != "/api/v1/bastao/leituras":
                return self._responder(404, {"erro": "não encontrado"})
            tamanho = int(self.headers.get("Content-Length") or 0)
            if tamanho > 2_000_000:
                return self._responder(413, {"erro": "lote grande demais"})
            try:
                payload = json.loads(self.rfile.read(tamanho) or b"null")
            except ValueError:
                return self._responder(400, {"erro": "JSON inválido"})
            with trava:
                status, corpo = processar(conn, self.headers.get("Authorization"), payload, tokens)
            self._responder(status, corpo)

        def log_message(self, fmt, *args):
            pass

    return ThreadingHTTPServer(("0.0.0.0", porta), Handler)


if __name__ == "__main__":
    import argparse

    ap = argparse.ArgumentParser(description="Servidor de teste do receptor de leituras do bastão")
    ap.add_argument("--porta", type=int, default=8080)
    ap.add_argument("--banco", default="bastao_teste.db")
    ap.add_argument("--token", default="token-de-teste")
    ap.add_argument("--fazenda", default="fazenda-teste")
    a = ap.parse_args()
    srv = servidor_teste(a.porta, a.banco, {a.token: a.fazenda})
    print(f"Ouvindo em http://0.0.0.0:{a.porta}/api/v1/bastao/leituras  (fazenda={a.fazenda})")
    srv.serve_forever()
