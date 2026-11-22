#!/usr/bin/env python3
"""Arnés mínimo para manejar la app Compose por accesibilidad (uiautomator) + adb.

Uso:
  ui.py dump <serial> [--json]
  ui.py tap <serial> <texto> [--contains] [--index N]
  ui.py exists <serial> <texto>
  ui.py wait <serial> <texto> [--timeout 10]
  ui.py text <serial> <campo_regex> <valor>      # limpia y escribe en un campo
  ui.py input <serial> <texto>                    # escribe en el campo ya enfocado
  ui.py swipe <serial> x1 y1 x2 y2 [--ms 300]
  ui.py key <serial> <keycode>
  ui.py shot <serial> <archivo>
  ui.py texts <serial>
  ui.py state <serial>                            # imprime files/taqueria.json
  ui.py hist <serial>                             # imprime files/historial.jsonl
"""
import argparse
import json
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ADB = ["adb"]


def sh(args, timeout=120):
    p = subprocess.run(ADB + args, capture_output=True, text=True, timeout=timeout)
    return p.returncode, p.stdout, p.stderr


def dump(serial):
    """Devuelve la lista de nodos del uiautomator actual."""
    # reintenta: a veces el dump falla si la UI está animando
    for _ in range(4):
        rc, out, err = sh(["-s", serial, "shell", "uiautomator", "dump", "/sdcard/ui.xml"])
        if rc == 0 and "dumped" in (out + err):
            break
        time.sleep(0.4)
    rc, out, err = sh(["-s", serial, "exec-out", "cat", "/sdcard/ui.xml"])
    if rc != 0 or not out.strip():
        raise RuntimeError(f"no se pudo leer ui.xml: {err}")
    root = ET.fromstring(out)
    nodes = []
    parents = {}
    for parent in root.iter():
        for child in parent:
            parents[id(child)] = parent

    def rec(el):
        b = el.get("bounds", "")
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", b)
        bounds = tuple(map(int, m.groups())) if m else None
        node = {
            "text": el.get("text", ""),
            "desc": el.get("content-desc", ""),
            "class": el.get("class", ""),
            "clickable": el.get("clickable") == "true",
            "enabled": el.get("enabled") != "false",
            "scrollable": el.get("scrollable") == "true",
            "bounds": bounds,
        }
        nodes.append(node)
        for c in el:
            rec(c)

    rec(root)
    return nodes


def center(node, nodes, parents_unused=None):
    x1, y1, x2, y2 = node["bounds"]
    return (x1 + x2) // 2, (y1 + y2) // 2


def find(nodes, text, contains=False, index=0):
    """Busca por texto exacto y luego por content-desc."""
    cands = []
    if text is None:
        return None
    for n in nodes:
        if n["text"] == text or n["desc"] == text:
            cands.append(n)
    if not cands:
        for n in nodes:
            if contains and (text.lower() in (n["text"] + " " + n["desc"]).lower()):
                cands.append(n)
    if not cands:
        return None
    try:
        return cands[index]
    except IndexError:
        return None


def nearest_clickable(serial, node):
    """Toca el nodo; si no es clickable, busca el ancestro clickable más cercano.

    Re-parsea jerarquía para encontrar ancestros por bounds contenidos.
    """
    if node["clickable"] and node["enabled"]:
        return center(node, None)
    # Estrategia por contención de bounds: el ancestro clickable que contiene al nodo,
    # eligiendo el de menor área (el más cercano).
    nodes = dump(serial)
    x, y = center(node, None)
    best = None
    for n in nodes:
        if not n["clickable"] or not n["enabled"] or not n["bounds"]:
            continue
        x1, y1, x2, y2 = n["bounds"]
        if x1 <= x <= x2 and y1 <= y <= y2:
            area = (x2 - x1) * (y2 - y1)
            if best is None or area < best[0]:
                best = (area, n)
    if best:
        return center(best[1], None)
    return x, y


def tap_node(serial, node):
    x, y = nearest_clickable(serial, node)
    rc, out, err = sh(["-s", serial, "shell", "input", "tap", str(x), str(y)])
    time.sleep(0.8)
    return rc == 0, (x, y)


def wait_text(serial, text, timeout=12.0, contains=True):
    end = time.time() + timeout
    while time.time() < end:
        try:
            nodes = dump(serial)
        except Exception:
            time.sleep(0.5)
            continue
        if find(nodes, text, contains=contains):
            return True
        time.sleep(0.5)
    return False


def long_press(serial, node, ms=900):
    x, y = center(node, None)
    rc, out, err = sh(["-s", serial, "shell", "input", "swipe", str(x), str(y), str(x), str(y), str(ms)])
    time.sleep(0.8)
    return rc == 0, (x, y)


def dismiss_stylus(serial):
    """Cierra el tutorial flotante de stylus del emulador Android 17 si aparece."""
    for _ in range(2):
        nodes = dump(serial)
        if not any("Try out your stylus" in (n["text"] + n["desc"]) for n in nodes):
            return False
        cancel = find(nodes, "Cancel")
        if cancel:
            x, y = center(cancel, None)
            sh(["-s", serial, "shell", "input", "tap", str(x), str(y)])
        else:
            sh(["-s", serial, "shell", "input", "keyevent", "4"])
        time.sleep(0.8)
    return True


def set_text(serial, field_regex, value):
    nodes = dump(serial)
    node = None
    for n in nodes:
        if re.search(field_regex, n["text"] + " " + n["desc"]):
            node = n
            break
    if node is None:
        raise SystemExit(f"campo no encontrado: {field_regex}")
    tap_node(serial, node)
    time.sleep(0.8)
    dismiss_stylus(serial)
    # Ir al final y borrar contenido previo.
    sh(["-s", serial, "shell", "input", "keyevent", "123"])
    for _ in range(24):
        sh(["-s", serial, "shell", "input", "keyevent", "67"])
    esc = value.replace(" ", "%s")
    rc, out, err = sh(["-s", serial, "shell", "input", "text", esc])
    time.sleep(0.5)
    return rc == 0


def state(serial):
    rc, out, err = sh(["-s", serial, "exec-out", "run-as", "com.axzy.taqueria", "cat", "files/taqueria.json"])
    if rc != 0 or not out.strip():
        return None
    return json.loads(out)


def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    for name in ["dump", "texts", "state", "hist"]:
        p = sub.add_parser(name)
        p.add_argument("serial")
        if name == "dump":
            p.add_argument("--json", action="store_true")
    p = sub.add_parser("tap")
    p.add_argument("serial")
    p.add_argument("texto")
    p.add_argument("--contains", action="store_true")
    p.add_argument("--index", type=int, default=0)
    for name in ["exists", "wait"]:
        p = sub.add_parser(name)
        p.add_argument("serial")
        p.add_argument("texto")
        if name == "wait":
            p.add_argument("--timeout", type=float, default=12.0)
    p = sub.add_parser("text")
    p.add_argument("serial")
    p.add_argument("campo")
    p.add_argument("valor")
    p = sub.add_parser("input")
    p.add_argument("serial")
    p.add_argument("texto")
    p = sub.add_parser("swipe")
    p.add_argument("serial")
    p.add_argument("x1", type=int)
    p.add_argument("y1", type=int)
    p.add_argument("x2", type=int)
    p.add_argument("y2", type=int)
    p.add_argument("--ms", type=int, default=300)
    p = sub.add_parser("longpress")
    p.add_argument("serial")
    p.add_argument("texto")
    p.add_argument("--index", type=int, default=0)
    p = sub.add_parser("home")
    p.add_argument("serial")
    p = sub.add_parser("key")
    p.add_argument("serial")
    p.add_argument("keycode")
    p = sub.add_parser("shot")
    p.add_argument("serial")
    p.add_argument("archivo")
    a = ap.parse_args()

    if a.cmd == "dump":
        nodes = dump(a.serial)
        if a.json:
            print(json.dumps(nodes, ensure_ascii=False))
        else:
            for n in nodes:
                if n["text"] or n["desc"]:
                    print(f"{n['bounds']} click={n['clickable']} {n['class'].split('.')[-1]} "
                          f"text={n['text']!r} desc={n['desc']!r}")
    elif a.cmd == "texts":
        for n in dump(a.serial):
            t = n["text"] or n["desc"]
            if t:
                print(t)
    elif a.cmd == "tap":
        node = None
        for attempt in range(3):
            nodes = dump(a.serial)
            node = find(nodes, a.texto, contains=a.contains, index=a.index)
            if node:
                break
            time.sleep(1.0)
        if not node:
            print(f"NO ENCONTRADO: {a.texto}", file=sys.stderr)
            sys.exit(2)
        ok, pos = tap_node(a.serial, node)
        print(f"tap {a.texto!r} en {pos} ok={ok}")
    elif a.cmd == "exists":
        print("si" if find(dump(a.serial), a.texto, contains=True) else "no")
    elif a.cmd == "wait":
        ok = wait_text(a.serial, a.texto, timeout=a.timeout)
        print("si" if ok else "no")
        sys.exit(0 if ok else 1)
    elif a.cmd == "text":
        print("ok" if set_text(a.serial, a.campo, a.valor) else "fallo")
    elif a.cmd == "input":
        esc = a.texto.replace(" ", "%s")
        rc, out, err = sh(["-s", a.serial, "shell", "input", "text", esc])
        print("ok" if rc == 0 else err)
    elif a.cmd == "swipe":
        rc, out, err = sh(["-s", a.serial, "shell", "input", "swipe",
                           str(a.x1), str(a.y1), str(a.x2), str(a.y2), str(a.ms)])
        time.sleep(0.6)
        print("ok" if rc == 0 else err)
    elif a.cmd == "longpress":
        nodes = dump(a.serial)
        node = find(nodes, a.texto, contains=True, index=a.index)
        if not node:
            print(f"NO ENCONTRADO: {a.texto}", file=sys.stderr); sys.exit(2)
        ok, pos = long_press(a.serial, node)
        print(f"longpress {a.texto!r} en {pos} ok={ok}")
    elif a.cmd == "home":
        for _ in range(6):
            texts = "\n".join(n["text"] or n["desc"] for n in dump(a.serial))
            if "Cuentas abiertas" in texts or "Cocina ·" in texts:
                print("home"); break
            sh(["-s", a.serial, "shell", "input", "keyevent", "4"]); time.sleep(1)
        else:
            print("no se llegó a inicio", file=sys.stderr); sys.exit(1)
    elif a.cmd == "key":
        rc, out, err = sh(["-s", a.serial, "shell", "input", "keyevent", a.keycode])
        time.sleep(0.4)
        print("ok" if rc == 0 else err)
    elif a.cmd == "shot":
        p2 = subprocess.run(ADB + ["-s", a.serial, "exec-out", "screencap", "-p"], capture_output=True)
        if p2.returncode != 0:
            print(p2.stderr.decode(errors="replace"), file=sys.stderr)
            sys.exit(1)
        with open(a.archivo, "wb") as f:
            f.write(p2.stdout)
        print(a.archivo)
    elif a.cmd == "state":
        st = state(a.serial)
        print(json.dumps(st, ensure_ascii=False, indent=1) if st else "SIN ESTADO")
    elif a.cmd == "hist":
        rc, out, err = sh(["-s", a.serial, "exec-out", "run-as", "com.axzy.taqueria", "cat", "files/historial.jsonl"])
        print(out if out.strip() else "SIN HISTORIAL")


if __name__ == "__main__":
    main()
