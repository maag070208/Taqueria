#!/usr/bin/env python3
"""Mock mínimo de Axzy Central para pruebas de licencia en los emuladores.

- POST /api/v1/activate  y  GET /api/v1/license
- El comportamiento se controla editando `evidence/e2e/mock/mode.json`:
  {"mode": "ok" | "suspended" | "expired" | "soon" | "invalid" | "error500" | "badjson"}
- Registra cada petición en `evidence/e2e/logs/mock-<puerto>.log`.
"""
import json
import os
import sys
import time
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODE_FILE = os.path.join(BASE, "mock", "mode.json")
os.makedirs(os.path.dirname(MODE_FILE), exist_ok=True)
if not os.path.exists(MODE_FILE):
    json.dump({"mode": "ok"}, open(MODE_FILE, "w"))


def mode():
    try:
        return json.load(open(MODE_FILE)).get("mode", "ok")
    except Exception:
        return "ok"


def iso(dt):
    return dt.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def _log(self, path):
        line = f"{time.strftime('%H:%M:%S')} {self.server.port} {self.command} {path} auth={self.headers.get('Authorization','-')} mode={mode()}\n"
        with open(os.path.join(BASE, "logs", f"mock-{self.server.port}.log"), "a") as f:
            f.write(line)

    def _send(self, code, body, ctype="application/json"):
        data = body.encode()
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _license_body(self):
        m = mode()
        now = datetime.now(timezone.utc)
        status = "SUSPENDED" if m == "suspended" else "ACTIVE"
        valid = now + (timedelta(days=2) if m == "soon" else timedelta(days=15))
        if m == "expired":
            valid = now - timedelta(days=1)
        body = {
            "status": status,
            "tenant": {"name": "Taquería El Fogón"},
            "plan": {"name": "Taquería"},
            "nextDueDate": (now + timedelta(days=15)).date().isoformat(),
            "validUntil": iso(valid),
            "serverTime": iso(now),
        }
        return body

    def do_POST(self):
        n = int(self.headers.get("Content-Length", "0"))
        raw = self.rfile.read(n).decode("utf-8", "replace")
        self._log(self.path)
        m = mode()
        if m == "invalid":
            return self._send(401, '{"error":"invalid code"}')
        if m == "error500":
            return self._send(500, '{"error":"boom"}')
        if m == "badjson":
            return self._send(200, "esto no es json")
        self._send(200, json.dumps(self._license_body(), ensure_ascii=False))

    def do_GET(self):
        self._log(self.path)
        m = mode()
        if m == "invalid":
            return self._send(401, '{"error":"unauthorized"}')
        if m == "error500":
            return self._send(500, '{"error":"boom"}')
        if m == "badjson":
            return self._send(200, "esto no es json")
        self._send(200, json.dumps(self._license_body(), ensure_ascii=False))


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 4100
    os.makedirs(os.path.join(BASE, "logs"), exist_ok=True)
    srv = ThreadingHTTPServer(("0.0.0.0", port), Handler)
    srv.port = port
    print(f"mock Axzy Central escuchando en :{port}", flush=True)
    srv.serve_forever()


if __name__ == "__main__":
    main()
