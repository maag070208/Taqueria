#!/usr/bin/env python3
"""Registra un caso E2E en evidence/e2e/resultados.json (idempotente por id)."""
import argparse
import json
import os
import time

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))  # evidence/e2e
OUT = os.path.join(BASE, "resultados.json")


def load():
    if os.path.exists(OUT):
        with open(OUT) as f:
            return json.load(f)
    return {
        "generado": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        "build": {"apk_md5": "4ed325aa7bb20cd16ec9bd4c369c888b", "versionName": "1.0.1", "versionCode": 2,
                  "nota": "APK HEAD instalada con adb install -r el 2026-11-21 ~12:27"},
        "emuladores": {"cocina": "emulator-5554 (rol KITCHEN)", "mesera": "emulator-5556 (rol WAITER, host 10.0.2.2)"},
        "casos": [],
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--id", required=True)
    ap.add_argument("--title", required=True)
    ap.add_argument("--type", required=True)
    ap.add_argument("--device", default="ambos")
    ap.add_argument("--expected", required=True)
    ap.add_argument("--actual", required=True)
    ap.add_argument("--status", required=True, choices=["PASA", "FALLA", "BLOQUEADO", "OBSERVACION"])
    ap.add_argument("--evidence", default="")
    ap.add_argument("--data", default="")
    ap.add_argument("--notes", default="")
    a = ap.parse_args()
    doc = load()
    case = {
        "id": a.id, "titulo": a.title, "tipo": a.type, "dispositivo": a.device,
        "esperado": a.expected, "obtenido": a.actual, "estado": a.status,
        "evidencia": [e for e in a.evidence.split(",") if e],
        "datos": [d for d in a.data.split(",") if d],
        "notas": a.notes,
    }
    doc["casos"] = [c for c in doc["casos"] if c["id"] != a.id] + [case]
    with open(OUT, "w") as f:
        json.dump(doc, f, ensure_ascii=False, indent=2)
    print(f"{a.status} {a.id} -> {OUT} ({len(doc['casos'])} casos)")


if __name__ == "__main__":
    main()
