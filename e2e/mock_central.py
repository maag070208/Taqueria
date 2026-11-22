"""Central de licencias falsa para las pruebas de Maestro: acepta cualquier código.

Responde el mismo contrato que AxzyCentral (`POST /activate` y `GET /license`) para que la
cocina de prueba se active sin tocar la Central real. Escucha solo en 127.0.0.1; el emulador
llega por 10.0.2.2.

    python3 e2e/mock_central.py [puerto]      # 4999 por defecto

Un código que empiece con "revocado" responde 401, para probar la pantalla de bloqueo.

`POST /sales` guarda en memoria las cuentas que reporta la cocina; `GET /_sales` dice
cuántas van, con qué versión de la app y si llegó el respaldo, para que el runner lo verifique.
`PUT /backup` guarda el respaldo de la cocina y `GET /backup` lo devuelve.
"""
import json
import sys
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


def body() -> bytes:
    now = datetime.now(timezone.utc)
    return json.dumps({
        "status": "ACTIVE",
        "tenant": {"name": "Taquería de pruebas"},
        "product": {"key": "taqueria", "name": "Tacomanda"},
        "plan": {"name": "Taquería"},
        "nextDueDate": None,
        "validUntil": (now + timedelta(days=30)).isoformat().replace("+00:00", "Z"),
        "serverTime": now.isoformat().replace("+00:00", "Z"),
    }).encode()


SALES: dict = {}
VERSION = {"app": None}
BACKUP: dict = {}


class Handler(BaseHTTPRequestHandler):
    def _reply(self, code: int, payload: bytes) -> None:
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def _unauthorized(self) -> None:
        self._reply(401, b'{"code":"INVALID_DEVICE_CODE","message":"Codigo invalido o revocado"}')

    def do_POST(self) -> None:  # noqa: N802
        if self.path.rstrip("/").endswith("/sales"):
            if self.headers.get("Authorization", "").startswith("Bearer revocado"):
                return self._unauthorized()
            raw = self.rfile.read(int(self.headers.get("Content-Length", 0) or 0))
            upload = json.loads(raw or b"{}")
            VERSION["app"] = upload.get("appVersion")
            for t in upload.get("tickets", []):
                SALES[t["id"]] = t
            return self._reply(200, json.dumps({"received": len(upload.get("tickets", []))}).encode())
        if not self.path.rstrip("/").endswith("/activate"):
            return self._reply(404, b"{}")
        raw = self.rfile.read(int(self.headers.get("Content-Length", 0) or 0))
        code = json.loads(raw or b"{}").get("code", "")
        if code.startswith("revocado"):
            return self._unauthorized()
        self._reply(200, body())

    def do_PUT(self) -> None:  # noqa: N802
        if not self.path.rstrip("/").endswith("/backup"):
            return self._reply(404, b"{}")
        if self.headers.get("Authorization", "").startswith("Bearer revocado"):
            return self._unauthorized()
        upload = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0) or 0)) or b"{}")
        BACKUP.update(savedAt=upload.get("savedAt"), appVersion=upload.get("appVersion"), state=upload.get("state", {}),
                      deviceName="Cocina de pruebas", fromThisDevice=True)
        self._reply(200, b'{"saved":true}')

    def do_GET(self) -> None:  # noqa: N802
        if self.path.rstrip("/") == "/_sales":
            sold = sum(t["totalCents"] for t in SALES.values() if not t["cancelled"])
            return self._reply(200, json.dumps({
                "tickets": len(SALES), "soldCents": sold, "appVersion": VERSION["app"], "backupAt": BACKUP.get("savedAt"),
            }).encode())
        if self.path.rstrip("/").endswith("/backup"):
            if not BACKUP:
                return self._reply(404, b'{"code":"NO_BACKUP"}')
            return self._reply(200, json.dumps(BACKUP).encode())
        if not self.path.rstrip("/").endswith("/license"):
            return self._reply(404, b"{}")
        if self.headers.get("Authorization", "").startswith("Bearer revocado"):
            return self._unauthorized()
        self._reply(200, body())

    def log_message(self, fmt: str, *args) -> None:
        sys.stderr.write("central-falsa: " + fmt % args + "\n")


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 4999
    print(f"Central falsa en http://127.0.0.1:{port}/api/v1", flush=True)
    ThreadingHTTPServer(("127.0.0.1", port), Handler).serve_forever()
