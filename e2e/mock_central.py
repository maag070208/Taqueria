"""Central de licencias falsa para las pruebas de Maestro: acepta cualquier código.

Responde el mismo contrato que AxzyCentral (`POST /activate` y `GET /license`) para que la
cocina de prueba se active sin tocar la Central real. Escucha solo en 127.0.0.1; el emulador
llega por adb reverse (127.0.0.1 del teléfono).

    python3 e2e/mock_central.py [puerto]      # 4999 por defecto

Un código que empiece con "revocado" responde 401, para probar la pantalla de bloqueo.
Un código que empiece con "sushi" es de un cliente del giro Sushi: la licencia trae sus palabras y
partes, y la Central ya tiene su menú (rollos), que la cocina adopta al sincronizar.

`GET /catalog` y `PUT /catalog` siguen la regla de la Central real: versiones con `baseVersion`, 409
`CATALOG_CONFLICT` si no coincide y, la primera vez, se adopta el menú de la cocina.

`POST /sales` guarda en memoria las cuentas que reporta la cocina; `GET /_sales` dice
cuántas van, con qué versión de la app y si llegó el respaldo, para que el runner lo verifique.
`PUT /backup` guarda el respaldo de la cocina y `GET /backup` lo devuelve.
"""
import json
import sys
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


SUSHI_MENU = {
    "categories": [{"name": "Rollos", "singular": "Rollo"}, {"name": "Bebidas"}],
    "products": [
        {"id": "rollos-california", "name": "California", "priceCents": 13500, "category": "Rollos"},
        {"id": "rollos-dragon", "name": "Dragón", "priceCents": 16500, "category": "Rollos"},
        {"id": "bebidas-te-verde", "name": "Té verde", "priceCents": 3000, "category": "Bebidas"},
    ],
    "modifierGroups": [],
    "freeExtras": [],
}

# Menú vigente por código de dispositivo (cada código es un cliente distinto en estas pruebas).
CATALOGS: dict = {}


def is_sushi(code: str) -> bool:
    return code.startswith("sushi")


def catalog_of(code: str):
    if code not in CATALOGS and is_sushi(code):
        CATALOGS[code] = {"version": 1, "source": "central", "author": "plantilla", "updatedAt": "", "data": SUSHI_MENU}
    return CATALOGS.get(code)


def body(code: str = "") -> bytes:
    now = datetime.now(timezone.utc)
    sushi = is_sushi(code)
    current = catalog_of(code)
    return json.dumps({
        "status": "ACTIVE",
        "tenant": {"name": "Sushi de pruebas" if sushi else "Taquería de pruebas"},
        "product": {
            "key": "SUSHI", "name": "Sushi",
            "terms": {"kitchenRole": "Sushero", "item": "Rollo", "kitchen": "Barra"},
            "features": {"mixes": False, "meatColors": False, "freeExtras": True},
        } if sushi else {"key": "TAQUERIA", "name": "Taquería"},
        "catalog": {"version": current["version"]} if current else None,
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
        self._reply(200, body(code))

    def _code(self) -> str:
        return self.headers.get("Authorization", "").removeprefix("Bearer ").strip()

    def do_PUT(self) -> None:  # noqa: N802
        if self.path.rstrip("/").endswith("/catalog"):
            code = self._code()
            if code.startswith("revocado"):
                return self._unauthorized()
            save = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0) or 0)) or b"{}")
            current = catalog_of(code)
            if save.get("baseVersion", 0) != (current["version"] if current else 0):
                return self._reply(409, json.dumps({"code": "CATALOG_CONFLICT", "current": current}).encode())
            if current and current["data"] == save.get("data"):
                return self._reply(200, json.dumps({"saved": False, "catalog": current}).encode())
            CATALOGS[code] = {"version": (current["version"] if current else 0) + 1, "source": "cocina",
                              "author": "Cocina de pruebas", "updatedAt": "", "data": save.get("data", {})}
            return self._reply(200, json.dumps({"saved": True, "catalog": CATALOGS[code]}).encode())
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
            return self._reply(200, json.dumps({"backups": [{"deviceId": "prueba", **BACKUP}]}).encode())
        if self.path.rstrip("/").endswith("/catalog"):
            code = self._code()
            if code.startswith("revocado"):
                return self._unauthorized()
            current = catalog_of(code)
            if not current:
                return self._reply(404, b'{"code":"NO_CATALOG"}')
            etag = f'"v{current["version"]}"'
            if self.headers.get("If-None-Match") == etag:
                self.send_response(304)
                self.send_header("ETag", etag)
                self.end_headers()
                return
            payload = json.dumps(current).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("ETag", etag)
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)
            return
        if not self.path.rstrip("/").endswith("/license"):
            return self._reply(404, b"{}")
        if self.headers.get("Authorization", "").startswith("Bearer revocado"):
            return self._unauthorized()
        self._reply(200, body(self._code()))

    def log_message(self, fmt: str, *args) -> None:
        sys.stderr.write("central-falsa: " + fmt % args + "\n")


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 4999
    print(f"Central falsa en http://127.0.0.1:{port}/api/v1", flush=True)
    ThreadingHTTPServer(("127.0.0.1", port), Handler).serve_forever()
