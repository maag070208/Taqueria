#!/usr/bin/env python3
"""Proxy TCP de diagnóstico entre la mesera (emulador 5556) y la cocina.

Escucha en 0.0.0.0:7777 y reenvía a 127.0.0.1:17777 (adb forward -> emulador-5554:7777).
Registra cada línea (JSON) en evidence/e2e/logs/proxy.log con dirección y hora.

Permite simular caídas: `--drop-after N` corta la conexión tras N líneas del mesero;
`--blackhole` acepta conexiones pero no reenvía nada (para probar timeout de 25 s/8 s).
"""
import argparse
import json
import socket
import threading
import time

LOG = "evidence/e2e/logs/proxy.log"
lock = threading.Lock()


def log(direction, text):
    with lock, open(LOG, "a") as f:
        f.write(f"{time.strftime('%H:%M:%S')} {direction} {text}\n")


def pipe(src, dst, tag, drop_after=None, blackhole=False):
    n = 0
    try:
        buf = b""
        while True:
            data = src.recv(4096)
            if not data:
                break
            n += 1
            if blackhole or (drop_after is not None and n > drop_after):
                log(tag, "<CORTADO>")
                break
            buf += data
            while b"\n" in buf:
                line, buf = buf.split(b"\n", 1)
                log(tag, line.decode("utf-8", "replace"))
            dst.sendall(data)
    except OSError as e:
        log(tag, f"<ERROR {e}>")
    finally:
        for s in (src, dst):
            try:
                s.close()
            except OSError:
                pass


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--listen", type=int, default=7777)
    ap.add_argument("--target", type=int, default=17777)
    ap.add_argument("--blackhole", action="store_true")
    ap.add_argument("--drop-after", type=int, default=None)
    a = ap.parse_args()
    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("0.0.0.0", a.listen))
    srv.listen(8)
    open(LOG, "a").write(f"=== proxy {a.listen}->{a.target} {time.strftime('%H:%M:%S')} ===\n")
    while True:
        client, addr = srv.accept()
        log("META", f"conexión de {addr}")
        try:
            upstream = socket.create_connection(("127.0.0.1", a.target), timeout=5)
            upstream.settimeout(None)
        except OSError as e:
            log("META", f"sin upstream: {e}")
            client.close()
            continue
        threading.Thread(target=pipe, args=(client, upstream, "C->K"), daemon=True).start()
        threading.Thread(target=pipe, args=(upstream, client, "K->C", a.drop_after, a.blackhole), daemon=True).start()


if __name__ == "__main__":
    main()
