"""Testes do receptor (só biblioteca padrão):  python -m unittest integracao/test_bastao_api.py"""
import json
import os
import sqlite3
import sys
import threading
import unittest
import urllib.error
import urllib.request
import uuid

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bastao_api as api  # noqa: E402

TOKENS = {"tok-fazenda-a": "fazenda-a", "tok-fazenda-b": "fazenda-b"}


def leitura(brinco="076000000001234", **kw):
    d = {"uuid": str(uuid.uuid4()), "sessao": "20261011-114800", "brinco": brinco,
         "data_hora": "2026-10-11T11:48:00-03:00", "recuperada": False}
    d.update(kw)
    return d


def lote(leituras, fazenda="fazenda-a"):
    return {"fazenda_id": fazenda, "dispositivo": "Bastao teste", "app_versao": "0.2.0", "leituras": leituras}


class TestNucleo(unittest.TestCase):
    def setUp(self):
        self.conn = sqlite3.connect(":memory:")
        api.criar_tabela(self.conn)

    def tearDown(self):
        self.conn.close()

    def total(self):
        return self.conn.execute("SELECT COUNT(*) FROM bastao_leituras").fetchone()[0]

    def test_grava_e_e_idempotente(self):
        ls = [leitura(), leitura("982000123456789", recuperada=True)]
        st, r = api.processar(self.conn, "Bearer tok-fazenda-a", lote(ls), TOKENS)
        self.assertEqual(st, 200)
        self.assertEqual(set(r["aceitas"]), {l["uuid"] for l in ls})
        self.assertEqual(self.total(), 2)
        # Reenvio (queda de internet no meio): aceito de novo, sem duplicar
        st, r = api.processar(self.conn, "Bearer tok-fazenda-a", lote(ls), TOKENS)
        self.assertEqual(st, 200)
        self.assertEqual(len(r["aceitas"]), 2)
        self.assertEqual(self.total(), 2)
        rec = self.conn.execute("SELECT recuperada FROM bastao_leituras WHERE brinco='982000123456789'").fetchone()[0]
        self.assertEqual(rec, 1)

    def test_rejeita_itens_invalidos_sem_perder_os_validos(self):
        ok = leitura()
        ruins = [leitura("12345"), leitura("076999999999999"), leitura(data_hora="2026-10-11 11:48"),
                 leitura(uuid="x")]
        st, r = api.processar(self.conn, "Bearer tok-fazenda-a", lote([ok] + ruins), TOKENS)
        self.assertEqual(st, 200)
        self.assertEqual(r["aceitas"], [ok["uuid"]])
        self.assertEqual(len(r["rejeitadas"]), 4)
        self.assertEqual(self.total(), 1)

    def test_autorizacao(self):
        st, _ = api.processar(self.conn, None, lote([leitura()]), TOKENS)
        self.assertEqual(st, 401)
        st, _ = api.processar(self.conn, "Bearer errado", lote([leitura()]), TOKENS)
        self.assertEqual(st, 401)
        # Token da fazenda B não grava na fazenda A
        st, r = api.processar(self.conn, "Bearer tok-fazenda-b", lote([leitura()], "fazenda-a"), TOKENS)
        self.assertEqual(st, 401)
        self.assertEqual(self.total(), 0)

    def test_payload_invalido(self):
        st, _ = api.processar(self.conn, "Bearer tok-fazenda-a", {"fazenda_id": "fazenda-a"}, TOKENS)
        self.assertEqual(st, 400)
        st, _ = api.processar(self.conn, "Bearer tok-fazenda-a", [1, 2], TOKENS)
        self.assertEqual(st, 400)
        grande = lote([leitura() for _ in range(api.MAX_LEITURAS_POR_LOTE + 1)])
        st, _ = api.processar(self.conn, "Bearer tok-fazenda-a", grande, TOKENS)
        self.assertEqual(st, 400)


class TestServidorHttp(unittest.TestCase):
    """Ponta a ponta: o mesmo JSON que o app Android envia (HttpSyncService)."""

    @classmethod
    def setUpClass(cls):
        cls.db = os.path.join(os.environ.get("TMPDIR", "/tmp"), f"bastao_{uuid.uuid4().hex}.db")
        cls.srv = api.servidor_teste(0, cls.db, TOKENS)
        cls.url = f"http://127.0.0.1:{cls.srv.server_address[1]}/api/v1/bastao/leituras"
        threading.Thread(target=cls.srv.serve_forever, daemon=True).start()

    @classmethod
    def tearDownClass(cls):
        cls.srv.shutdown()
        os.remove(cls.db)

    def post(self, corpo, token="tok-fazenda-a"):
        req = urllib.request.Request(self.url, data=json.dumps(corpo).encode(), method="POST",
                                     headers={"Content-Type": "application/json",
                                              "Authorization": f"Bearer {token}"})
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        try:
            with opener.open(req, timeout=5) as r:
                return r.status, json.loads(r.read())
        except urllib.error.HTTPError as e:
            return e.code, json.loads(e.read())

    def test_lote_de_200(self):
        ls = [leitura(f"076{i:012d}") for i in range(1, 201)]
        st, r = self.post(lote(ls))
        self.assertEqual(st, 200)
        self.assertEqual(len(r["aceitas"]), 200)

    def test_token_errado(self):
        st, r = self.post(lote([leitura()]), token="nada")
        self.assertEqual(st, 401)
        self.assertIn("erro", r)


if __name__ == "__main__":
    unittest.main()
