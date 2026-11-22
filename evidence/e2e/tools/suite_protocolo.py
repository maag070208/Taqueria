#!/usr/bin/env python3
"""Suite E2E de protocolo: habla TCP con el servidor de cocina real (emulador-5554)
a través de `adb forward tcp:7777 tcp:7777`.

Cubre casos VAL/IDEM/UND/ERR/BOR/PER/SEG/FUN de la matriz que no necesitan UI.
Cada caso se ejecuta contra el store real y se verifica leyendo files/taqueria.json.
"""
import json
import os
import socket
import subprocess
import sys
import threading
import time
import uuid

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
K = "emulator-5554"          # cocina
W = "emulator-5556"          # mesera
PKG = "com.axzy.taqueria"
HOST, PORT = "127.0.0.1", 7777
OUT = os.path.join(BASE, "protocolo.json")

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ui  # noqa: E402


# ----------------------------- infraestructura -----------------------------

def adb(*args, serial=K, timeout=60):
    return subprocess.run(["adb", "-s", serial] + list(args), capture_output=True, text=True, timeout=timeout)


def restart_kitchen(reset=False):
    adb("shell", "am", "force-stop", PKG)
    if reset:
        adb("shell", "run-as", PKG, "rm", "-f", "files/taqueria.json", "files/taqueria.json.tmp", "files/historial.jsonl")
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
    # esperar a que el servidor acepte conexiones
    end = time.time() + 25
    while time.time() < end:
        try:
            s = socket.create_connection((HOST, PORT), timeout=1)
            s.close()
            time.sleep(0.8)
            return
        except OSError:
            time.sleep(0.5)
    raise RuntimeError("el servidor de cocina no abrió el puerto 7777")


def kitchen_state(retries=8):
    for _ in range(retries):
        p = subprocess.run(["adb", "-s", K, "exec-out", "run-as", PKG, "cat", "files/taqueria.json"],
                           capture_output=True, text=True)
        if p.returncode == 0 and p.stdout.strip():
            try:
                return json.loads(p.stdout)
            except json.JSONDecodeError:
                pass
        time.sleep(0.3)
    raise RuntimeError("no se pudo leer taqueria.json")


def kitchen_history():
    p = subprocess.run(["adb", "-s", K, "exec-out", "run-as", PKG, "cat", "files/historial.jsonl"],
                       capture_output=True, text=True)
    if p.returncode != 0 or not p.stdout.strip():
        return []
    return [json.loads(l) for l in p.stdout.splitlines() if l.strip()]


def ui_texts(serial=K):
    try:
        return "\n".join(ui.dump(serial)[i]["text"] for i in range(len(ui.dump(serial))))
    except Exception:
        return ""


def kitchen_ui_has(text, serial=K):
    try:
        return any(text in (n["text"] + " " + n["desc"]) for n in ui.dump(serial))
    except Exception:
        return False


class Client:
    def __init__(self):
        self.sock = socket.create_connection((HOST, PORT), timeout=5)
        self.sock.settimeout(10)
        self.f = self.sock.makefile("r", encoding="utf-8")
        # Los mensajes State/Ping son difusión: no son respuesta a una petición.
        first = self.f.readline()
        if first:
            try:
                m = json.loads(first)
                if m.get("type") != "state":
                    raise RuntimeError(f"se esperaba state inicial, llegó {m.get('type')}")
            except json.JSONDecodeError:
                raise RuntimeError("estado inicial ilegible")

    def send_raw(self, text):
        self.sock.sendall(text.encode("utf-8"))

    def recv(self, timeout=8.0):
        self.sock.settimeout(timeout)
        line = self.f.readline()
        if not line:
            raise EOFError("conexión cerrada por cocina")
        return json.loads(line)

    def request(self, msg, timeout=8.0):
        self.send_raw(json.dumps(msg, ensure_ascii=False) + "\n")
        end = time.time() + timeout
        while time.time() < end:
            m = self.recv(max(0.5, end - time.time()))
            if m.get("type") == "reply" and m.get("reqId") == msg.get("reqId"):
                return m
        raise TimeoutError(f"sin reply para reqId {msg.get('reqId')}")

    def close(self):
        try:
            self.f.close()
        except Exception:
            pass
        try:
            self.sock.close()
        except Exception:
            pass


class Case:
    def __init__(self, cid, title, typ, device="cocina"):
        self.id, self.title, self.type, self.device = cid, title, typ, device


RESULTS = []
_req = [10_000]
_state_cache = {"t": 0, "v": None}


def reqid():
    _req[0] += 1
    return _req[0]


def fresh_state():
    st = kitchen_state()
    _state_cache["v"] = st
    return st


def pid(name, st=None):
    st = st or kitchen_state()
    return next(p for p in st["products"] if p["name"] == name)["id"]


def lines_(product_id, qty=1, mods=None, note=""):
    return {"productId": product_id, "qty": qty, "mods": mods or [], "note": note}


def create(c, table="T01", lines=None, account=None, key=None, req=None):
    msg = {"type": "create", "reqId": req or reqid(), "clientKey": key if key is not None else ("k-" + uuid.uuid4().hex),
           "table": table, "accountId": account, "lines": lines or []}
    return c.request(msg)


def pay(c, account_id, cents, method="CASH", received=None, deliver=False, key=None):
    msg = {"type": "pay", "reqId": reqid(), "clientKey": key or ("p-" + uuid.uuid4().hex),
           "accountId": account_id, "amountCents": cents, "method": method}
    if received is not None:
        msg["receivedCents"] = received
    if deliver:
        msg["deliver"] = True
    return c.request(msg)


def status(c, order_id, st):
    return c.request({"type": "orderStatus", "reqId": reqid(), "orderId": order_id, "status": st})


def edit(c, order_id, lines):
    return c.request({"type": "editOrder", "reqId": reqid(), "orderId": order_id, "lines": lines})


def cancel(c, account_id):
    return c.request({"type": "cancelAccount", "reqId": reqid(), "accountId": account_id})


def restore(c, account_id):
    return c.request({"type": "restoreAccount", "reqId": reqid(), "accountId": account_id})


def void(c, account_id, payment_id):
    return c.request({"type": "voidPayment", "reqId": reqid(), "accountId": account_id, "paymentId": payment_id})


def new_account_order(c, table, product="Asada", qty=1, mods=None, note=""):
    """Crea una cuenta nueva con una orden; devuelve (account_id, order, reply)."""
    st = kitchen_state()
    r = create(c, table=table, lines=[lines_(pid(product, st), qty, mods, note)])
    if not r.get("order"):
        raise AssertionError(f"no se creó la orden: {r}")
    o = r["order"]
    return o["accountId"], o, r


def ok(r):
    if r.get("error"):
        raise AssertionError(f"se esperaba éxito, llegó error: {r['error']}")
    return r


def expect_error(r, fragment):
    e = r.get("error") or ""
    assert fragment.lower() in e.lower(), f"se esperaba error con '{fragment}', llegó: {r}"
    return e


def check_order(st, order_id):
    return next(o for o in st["orders"] if o["id"] == order_id)


def check_account(st, account_id):
    return next(a for a in st["accounts"] if a["id"] == account_id)


def run(cid, title, typ, device, fn):
    c = Case(cid, title, typ, device)
    t0 = time.time()
    try:
        detail = fn()
        RESULTS.append({"id": cid, "titulo": title, "tipo": typ, "dispositivo": device,
                        "esperado": detail.get("esperado", ""), "obtenido": detail.get("obtenido", ""),
                        "estado": detail.get("estado", "PASA"), "ms": int((time.time() - t0) * 1000),
                        "notas": detail.get("notas", "")})
        print(f"[PASA] {cid} {title} ({RESULTS[-1]['ms']} ms)")
    except Exception as e:
        RESULTS.append({"id": cid, "titulo": title, "tipo": typ, "dispositivo": device,
                        "esperado": "ver pasos del caso", "obtenido": f"FALLA: {e}",
                        "estado": "FALLA", "ms": int((time.time() - t0) * 1000), "notas": str(e)})
        print(f"[FALLA] {cid} {title}: {e}")


# ----------------------------- casos -----------------------------

def main():
    restart_kitchen(reset=True)
    st = fresh_state()
    c = Client()

    # ---------- VAL captura ----------
    def val_captura_01():
        a = pid("Asada", st)
        r = create(c, "TV01", [lines_(a, 1, ["Con salsa", "Sin salsa"])])
        e1 = expect_error(r, "elige solo una")
        r2 = create(c, "TV01", [lines_(a, 1, ["Con cebolla", "Sin cebolla"])])
        e2 = expect_error(r2, "elige solo una")
        return {"esperado": "Dos opciones del mismo tipo exclusivo (Salsa/Cebolla) se rechazan",
                "obtenido": f"ambos rechazados: '{e1}' / '{e2}'"}
    run("VAL-CAPTURA-01", "Dos opciones exclusivas contradictorias se rechazan", "VAL", "cocina", val_captura_01)

    def val_captura_02():
        a = pid("Asada", st)
        r = create(c, "TV02", [lines_(a, 1, ["Sin hielo"])])
        return {"esperado": "Modificación de otro producto se rechaza",
                "obtenido": expect_error(r, "no aplica")}
    run("VAL-CAPTURA-02", "Modificación ajena al producto se rechaza", "VAL", "cocina", val_captura_02)

    def val_captura_03():
        a = pid("Asada", st)
        e0 = expect_error(create(c, "TV03", [lines_(a, 0)]), "Cantidad inválida")
        e100 = expect_error(create(c, "TV03", [lines_(a, 100)]), "Cantidad inválida")
        return {"esperado": "qty 0 y 100 se rechazan para Asada",
                "obtenido": f"qty 0 → '{e0}'; qty 100 → '{e100}'"}
    run("VAL-CAPTURA-03", "Cantidad 0 o fuera de rango se rechaza", "VAL", "cocina", val_captura_03)

    def val_captura_04():
        e = expect_error(create(c, "TV04", []), "no tiene productos")
        return {"esperado": "Orden sin líneas rechazada", "obtenido": e}
    run("VAL-CAPTURA-04", "Orden sin productos se rechaza", "VAL", "cocina", val_captura_04)

    def val_captura_05():
        e1 = expect_error(create(c, "TV05", [lines_("no-existe", 1)]), "no está disponible")
        return {"esperado": "Producto inexistente se rechaza", "obtenido": e1}
    run("VAL-CAPTURA-05", "Producto inexistente o agotado sin pedido previo se rechaza", "VAL", "cocina", val_captura_05)

    def val_captura_06():
        a = pid("Asada", st)
        e1 = expect_error(create(c, "TV06", [lines_(a, 1)], key=""), "Solicitud inválida")
        e2 = expect_error(create(c, "TV06", [lines_(a, 1)], key="x" * 65), "Solicitud inválida")
        return {"esperado": "clientKey vacío y de 65 caracteres rechazados",
                "obtenido": f"vacío → '{e1}'; 65 chars → '{e2}'"}
    run("VAL-CAPTURA-06", "Llave de idempotencia vacía o de más de 64 caracteres", "VAL", "cocina", val_captura_06)

    def val_captura_07():
        acc, o, _ = new_account_order(c, "TV07", "Asada", 1)
        ok(status(c, o["id"], "DELIVERED"))
        e = expect_error(edit(c, o["id"], [lines_(pid("Asada", st), 2)]), "Solo se puede editar")
        return {"esperado": "Editar orden entregada se rechaza", "obtenido": e}
    run("VAL-CAPTURA-07", "No se puede editar una orden que ya salió de cocina", "VAL", "cocina", val_captura_07)

    def val_captura_08():
        e = expect_error(edit(c, "orden-inexistente", [lines_(pid("Asada", st), 1)]), "ya no está activa")
        return {"esperado": "Editar orden inexistente se rechaza", "obtenido": e}
    run("VAL-CAPTURA-08", "Editar una orden que ya no existe", "VAL", "cocina", val_captura_08)

    # ---------- VAL órdenes/cuentas ----------
    def val_ordenes_01():
        acc, o, _ = new_account_order(c, "TV08", "Asada", 1)
        ok(status(c, o["id"], "CANCELLED"))
        e1 = expect_error(status(c, o["id"], "DELIVERED"), "ya está cancelada")
        e2 = expect_error(status(c, o["id"], "PENDING"), "Ya pasó el tiempo") if False else None
        # PENDING->PENDING no es transición válida
        acc2, o2, _ = new_account_order(c, "TV08b", "Asada", 1)
        e2 = expect_error(status(c, o2["id"], "PENDING"), "ya está en cocina")
        return {"esperado": "CANCELLED→DELIVERED y PENDING→PENDING se rechazan",
                "obtenido": f"'{e1}' / '{e2}'"}
    run("VAL-ORDENES-01", "Transiciones de estado de orden inválidas", "VAL", "cocina", val_ordenes_01)

    def val_cuentas_01():
        acc, _, _ = new_account_order(c, "TV09", "Asada", 1)
        ok(cancel(c, acc))
        e = expect_error(cancel(c, acc), "ya estaba cancelada")
        return {"esperado": "Cancelar dos veces se rechaza", "obtenido": e}
    run("VAL-CUENTAS-01", "Cancelar una cuenta que ya estaba cancelada", "VAL", "cocina", val_cuentas_01)

    def val_cuentas_02():
        acc, _, _ = new_account_order(c, "TV10", "Asada", 1)
        e = expect_error(restore(c, acc), "no está cancelada")
        return {"esperado": "Restaurar cuenta no cancelada se rechaza", "obtenido": e}
    run("VAL-CUENTAS-02", "Restaurar una cuenta que no está cancelada", "VAL", "cocina", val_cuentas_02)

    def val_cuentas_03():
        acc, _, _ = new_account_order(c, "TV11", "Asada", 1)
        ok(cancel(c, acc))
        e = expect_error(create(c, "TV11", [lines_(pid("Asada", st), 1)], account=acc), "está cancelada")
        return {"esperado": "Agregar orden a cuenta cancelada se rechaza", "obtenido": e}
    run("VAL-CUENTAS-03", "Agregar una orden a una cuenta cancelada", "VAL", "cocina", val_cuentas_03)

    def val_cuentas_04():
        acc, o, _ = new_account_order(c, "TV12", "Asada", 1)
        ok(pay(c, acc, 2000, "CASH", deliver=True))
        s2 = kitchen_state()
        assert check_account(s2, acc)["closedAt"], "la cuenta debía cerrarse"
        e = expect_error(create(c, "TV12", [lines_(pid("Asada", st), 1)], account=acc), "ya se cerró")
        return {"esperado": "Agregar orden a cuenta cerrada se rechaza", "obtenido": e}
    run("VAL-CUENTAS-04", "Agregar una orden a una cuenta ya cerrada", "VAL", "cocina", val_cuentas_04)

    def val_cuentas_05():
        acc, o, _ = new_account_order(c, "TV13", "Asada", 1)
        e = expect_error(create(c, "TV14", [lines_(pid("Asada", st), 1)], account=acc), "otra mesa")
        return {"esperado": "Reutilizar cuenta de otra mesa se rechaza", "obtenido": e}
    run("VAL-CUENTAS-05", "Reutilizar una cuenta de otra mesa", "VAL", "cocina", val_cuentas_05)

    def val_cuentas_06():
        a = pid("Asada", st)
        e1 = expect_error(create(c, "", [lines_(a, 1)]), "Mesa es requerido")
        e2 = expect_error(create(c, "x" * 21, [lines_(a, 1)]), "máximo 20")
        return {"esperado": "Mesa vacía y de 21 caracteres rechazadas",
                "obtenido": f"'{e1}' / '{e2}'"}
    run("VAL-CUENTAS-06", "Mesa vacía o de más de 20 caracteres", "VAL", "cocina", val_cuentas_06)

    # ---------- VAL cobro ----------
    def val_cobro_01():
        acc, o, _ = new_account_order(c, "TV15", "Asada", 1)
        e = expect_error(pay(c, acc, 2001), "excede el saldo")
        return {"esperado": "Pagar 2001 cuando se debe 2000 se rechaza", "obtenido": e}
    run("VAL-COBRO-01", "No se acepta pagar más que el saldo", "VAL", "cocina", val_cobro_01)

    def val_cobro_02():
        acc, o, _ = new_account_order(c, "TV16", "Asada", 1)
        e = expect_error(pay(c, acc, -1), "devolución excede")
        return {"esperado": "Devolución sin saldo negativo se rechaza", "obtenido": e}
    run("VAL-COBRO-02", "La devolución no puede exceder lo que se debe regresar", "VAL", "cocina", val_cobro_02)

    def val_cobro_03():
        acc, o, _ = new_account_order(c, "TV17", "Asada", 1)
        e = expect_error(pay(c, acc, 2000, "TRANSFER", received=2500), "solo aplica a cobros en efectivo")
        return {"esperado": "receivedCents con transferencia se rechaza", "obtenido": e}
    run("VAL-COBRO-03", "El cambio solo aplica a cobros en efectivo", "VAL", "cocina", val_cobro_03)

    def val_cobro_04():
        acc, o, _ = new_account_order(c, "TV18", "Asada", 1)
        e = expect_error(pay(c, acc, 2000, received=1999), "menos que el importe")
        return {"esperado": "Recibido 1999 < importe 2000 se rechaza", "obtenido": e}
    run("VAL-COBRO-04", "Efectivo recibido menor al importe", "VAL", "cocina", val_cobro_04)

    def val_cobro_05():
        acc, o, _ = new_account_order(c, "TV19", "Asada", 1)
        e = expect_error(pay(c, acc, 2000, received=10_000_001), "recibido inválido")
        return {"esperado": "Recibido > 10,000,000 se rechaza", "obtenido": e}
    run("VAL-COBRO-05", "Importe recibido fuera del máximo razonable", "VAL", "cocina", val_cobro_05)

    def val_cobro_06():
        acc, o, _ = new_account_order(c, "TV20", "Asada", 1)
        ok(pay(c, acc, 2000, deliver=True))
        e = expect_error(pay(c, acc, 1), "ya está cerrada")
        return {"esperado": "Pagar cuenta cerrada se rechaza", "obtenido": e}
    run("VAL-COBRO-06", "Pago sobre una cuenta ya cerrada", "VAL", "cocina", val_cobro_06)

    def val_cobro_07():
        acc, o, _ = new_account_order(c, "TV21", "Asada", 1)
        e = expect_error(pay(c, acc, 0), "no puede ser cero")
        return {"esperado": "Pago de importe 0 se rechaza", "obtenido": e}
    run("VAL-COBRO-07", "Importe de pago en cero", "VAL", "cocina", val_cobro_07)

    # ---------- BOR ----------
    def bor_captura_01():
        a = pid("Asada", st)
        r = ok(create(c, "TB01", [lines_(a, 99)]))
        assert r["order"]["lines"][0]["qty"] == 99
        e = expect_error(create(c, "TB01b", [lines_(a, 100)]), "Cantidad inválida")
        return {"esperado": "qty 99 aceptada y 100 rechazada", "obtenido": f"99 aceptada; 100 → '{e}'"}
    run("BOR-CAPTURA-01", "Tope de 99 piezas por grupo", "BOR", "cocina", bor_captura_01)

    def bor_captura_06():
        a = pid("Asada", st)
        l60 = [lines_(a, 1, note=f"n{i}") for i in range(60)]
        r = ok(create(c, "TB06", l60))
        n60 = len(r["order"]["lines"])
        l61 = [lines_(a, 1, note=f"m{i}") for i in range(61)]
        e = expect_error(create(c, "TB06b", l61), "Demasiados grupos")
        return {"esperado": "60 grupos aceptados, 61 rechazados",
                "obtenido": f"60 → {n60} líneas; 61 → '{e}'"}
    run("BOR-CAPTURA-06", "Máximo 60 grupos por orden; el 61 se rechaza", "BOR", "cocina", bor_captura_06)

    def bor_captura_07():
        a = pid("Asada", st)
        r = ok(create(c, "TB07", [lines_(a, 1, note="n" * 100)]))
        assert r["order"]["lines"][0]["note"] == "n" * 100
        e = expect_error(create(c, "TB07b", [lines_(a, 1, note="n" * 101)]), "máximo 100")
        return {"esperado": "Nota de 100 aceptada y 101 rechazada", "obtenido": f"100 aceptada; 101 → '{e}'"}
    run("BOR-CAPTURA-07", "Nota de 100 caracteres se conserva; 101 se rechaza", "BOR", "cocina", bor_captura_07)

    def bor_cobro_04():
        acc, o, _ = new_account_order(c, "TB08", "Asada", 1)
        ok(pay(c, acc, 2000))
        acc2, _, _ = new_account_order(c, "TB09", "Asada", 1)
        e = expect_error(pay(c, acc2, 2001), "excede")
        s2 = kitchen_state()
        assert check_account(s2, acc)["payments"][0]["amountCents"] == 2000
        return {"esperado": "Saldo exacto aceptado; un centavo más rechazado",
                "obtenido": f"2000 aceptado; 2001 → '{e}'"}
    run("BOR-COBRO-04", "Importe igual al saldo se acepta; un centavo más se rechaza", "BOR", "cocina", bor_cobro_04)

    # ---------- IDEM ----------
    def idem_captura_01():
        a = pid("Asada", st)
        o1 = ok(create(c, "TI01", [lines_(a, 1)], key="same-key-1"))["order"]
        o2 = ok(create(c, "TI01", [lines_(a, 1)], key="same-key-1"))["order"]
        s2 = kitchen_state()
        n = sum(1 for o in s2["orders"] if o["clientKey"] == "same-key-1")
        assert o1["id"] == o2["id"] and n == 1, f"ids {o1['id']} {o2['id']} n={n}"
        return {"esperado": "Misma llave devuelve la misma orden y no duplica",
                "obtenido": f"misma orden {o1['id'][:8]}…, 1 sola en el store"}
    run("IDEM-CAPTURA-01", "Reintento con la misma llave no duplica la orden", "IDEM", "ambos", idem_captura_01)

    def idem_captura_02():
        a = pid("Asada", st)
        o1 = ok(create(c, "TI02", [lines_(a, 1)]))["order"]
        o2 = ok(create(c, "TI02", [lines_(a, 1)]))["order"]
        assert o1["id"] != o2["id"] and o1["number"] != o2["number"]
        return {"esperado": "Llave distinta crea orden nueva", "obtenido": f"#{o1['number']} y #{o2['number']} distintas"}
    run("IDEM-CAPTURA-02", "Una llave distinta crea una orden nueva con el mismo contenido", "IDEM", "cocina", idem_captura_02)

    def idem_captura_03():
        a = pid("Asada", st)
        out = {}
        def go(n):
            cl = Client()
            try:
                out[n] = create(cl, "TI03", [lines_(a, 1)], key="concurrent-key")
            finally:
                cl.close()
        ts = [threading.Thread(target=go, args=(i,)) for i in range(2)]
        [t.start() for t in ts]
        [t.join() for t in ts]
        assert all("order" in r for r in out.values()), out
        s2 = kitchen_state()
        n = sum(1 for o in s2["orders"] if o["clientKey"] == "concurrent-key")
        ids = {r["order"]["id"] for r in out.values()}
        assert n == 1 and len(ids) == 1, f"n={n} ids={ids}"
        return {"esperado": "Dos clientes concurrentes con misma llave crean una orden",
                "obtenido": f"1 orden {list(ids)[0][:8]}… con 2 replies correctos"}
    run("IDEM-CAPTURA-03", "Dos meseros envían a la vez la misma llave y solo se crea una orden", "IDEM", "ambos", idem_captura_03)

    def idem_captura_04():
        a = pid("Asada", st)
        c1 = Client()
        o1 = ok(create(c1, "TI04", [lines_(a, 1)], key="retry-key"))["order"]
        c1.close()  # se cae la conexión
        c2 = Client()
        o2 = ok(create(c2, "TI04", [lines_(a, 2)], key="retry-key"))["order"]  # reintento
        c2.close()
        s2 = kitchen_state()
        n = sum(1 for o in s2["orders"] if o["clientKey"] == "retry-key")
        assert o1["id"] == o2["id"] and n == 1, f"n={n}"
        return {"esperado": "El reintento tras reconectar devuelve la orden original",
                "obtenido": f"misma orden, sin duplicado (n={n})"}
    run("IDEM-CAPTURA-04", "Reintento automático del mesero tras reconectar no duplica", "IDEM", "ambos", idem_captura_04)

    def idem_captura_06():
        a = pid("Asada", st)
        o = ok(create(c, "TI06", [lines_(a, 2)]))["order"]
        r = ok(edit(c, o["id"], [lines_(a, 2)]))
        s2 = kitchen_state()
        oo = check_order(s2, o["id"])
        assert not r["order"]["revisions"] and not oo.get("editedAt"), f"revisions={r['order']['revisions']} editedAt={oo.get('editedAt')}"
        return {"esperado": "Guardar sin cambios no crea revisión ni editedAt",
                "obtenido": "sin revisiones, editedAt nulo"}
    run("IDEM-CAPTURA-06", "Guardar la edición sin cambios no crea revisión nueva", "IDEM", "cocina", idem_captura_06)

    def idem_captura_07():
        a = pid("Asada", st)
        o = ok(create(c, "TI07", [lines_(a, 4)]))["order"]
        ok(edit(c, o["id"], [lines_(a, 3)]))
        ok(edit(c, o["id"], [lines_(a, 2)]))
        s2 = kitchen_state()
        oo = check_order(s2, o["id"])
        assert len(oo["revisions"]) == 2, oo["revisions"]
        return {"esperado": "Dos ediciones reales dejan dos revisiones en orden",
                "obtenido": f"revisions={[r[0]['qty'] for r in oo['revisions']]}"}
    run("IDEM-CAPTURA-07", "Dos ediciones reales dejan dos revisiones en orden", "IDEM", "cocina", idem_captura_07)

    def idem_cobro_01():
        acc, o, _ = new_account_order(c, "TI08", "Asada", 1)
        r1 = ok(pay(c, acc, 1000, key="pay-same-key"))
        r2 = ok(pay(c, acc, 1000, key="pay-same-key"))
        s2 = kitchen_state()
        a = check_account(s2, acc)
        assert r1["paymentId"] == r2["paymentId"] and len(a["payments"]) == 1
        return {"esperado": "Mismo payment clientKey devuelve el mismo pago", "obtenido": "1 pago, mismo id"}
    run("IDEM-COBRO-01", "El reintento del mismo pago no duplica el movimiento", "IDEM", "ambos", idem_cobro_01)

    def idem_cobro_02():
        acc, o, _ = new_account_order(c, "TI09", "Asada", 1)  # total 2000
        out = {}
        def go(n):
            cl = Client()
            try:
                out[n] = pay(cl, acc, 2000)
            finally:
                cl.close()
        ts = [threading.Thread(target=go, args=(i,)) for i in range(2)]
        [t.start() for t in ts]
        [t.join() for t in ts]
        s2 = kitchen_state()
        a = check_account(s2, acc)
        paid = sum(p["amountCents"] for p in a["payments"] if not p["voided"])
        assert paid <= 2000, f"paid={paid}"
        errores = [r.get("error") for r in out.values() if r.get("error")]
        return {"esperado": "Pagos simultáneos no exceden el saldo", "obtenido": f"pagado={paid}; errores={errores}"}
    run("IDEM-COBRO-02", "Dos pagos simultáneos no exceden el saldo", "IDEM", "cocina", idem_cobro_02)

    def idem_ordenes_01():
        acc, o, _ = new_account_order(c, "TI10", "Asada", 1)
        ok(status(c, o["id"], "DELIVERED"))
        e = expect_error(status(c, o["id"], "DELIVERED"), "ya está")
        return {"esperado": "Marcar entregada dos veces se rechaza", "obtenido": e}
    run("IDEM-ORDENES-01", "Marcar entregada dos veces", "IDEM", "cocina", idem_ordenes_01)

    def idem_cuentas_01():
        acc, _, _ = new_account_order(c, "TI11", "Asada", 1)
        ok(cancel(c, acc))
        e = expect_error(cancel(c, acc), "ya estaba cancelada")
        return {"esperado": "Cancelar dos veces se rechaza", "obtenido": e}
    run("IDEM-CUENTAS-01", "Cancelar dos veces la misma cuenta", "IDEM", "cocina", idem_cuentas_01)

    # ---------- UND ----------
    def und_cobro_04():
        acc, o, _ = new_account_order(c, "TU01", "Asada", 1)
        p = ok(pay(c, acc, 1000))["paymentId"]
        ok(void(c, acc, p))
        ok(void(c, acc, p))  # idempotente
        s2 = kitchen_state()
        a = check_account(s2, acc)
        assert len(a["payments"]) == 1 and a["payments"][0]["voided"]
        return {"esperado": "Anular dos veces el mismo pago no duplica ni falla",
                "obtenido": "1 pago, voided=true"}
    run("UND-COBRO-04", "Deshacer dos veces el mismo pago no duplica ni falla", "UND", "cocina", und_cobro_04)

    def und_cobro_03():
        a = pid("Asada", st)
        o = ok(create(c, "TU02", [lines_(a, 2)]))["order"]  # 4000
        acc = o["accountId"]
        ok(pay(c, acc, 4000))  # sin deliver: sigue PENDING
        ok(edit(c, o["id"], [lines_(a, 1)]))  # total 2000, pagado 4000 -> saldo -2000
        s2 = kitchen_state()
        bal = next(a2 for a2 in s2["accounts"] if a2["id"] == acc)
        # devolver 2000
        p = ok(pay(c, acc, -2000, key="refund-1"))["paymentId"]
        ok(void(c, acc, p))
        s3 = kitchen_state()
        paid = sum(x["amountCents"] for x in check_account(s3, acc)["payments"] if not x["voided"])
        assert paid == 4000, paid
        return {"esperado": "Devolución se registra y al deshacerla el saldo vuelve a -2000",
                "obtenido": f"pagado neto tras deshacer={paid} (saldo -2000)"}
    run("UND-COBRO-03", "Deshacer una devolución", "UND", "cocina", und_cobro_03)

    def und_cuentas_03():
        # 29.5 s: dentro de la ventana
        acc, _, _ = new_account_order(c, "TU03", "Asada", 1)
        ok(cancel(c, acc))
        time.sleep(29.5)
        ok(restore(c, acc))
        # 31.5 s: fuera de la ventana
        ok(cancel(c, acc))
        time.sleep(31.5)
        e = expect_error(restore(c, acc), "Ya pasó el tiempo")
        return {"esperado": "A los 29.5 s se restaura; a los 31.5 s ya no",
                "obtenido": f"29.5 s restauró; 31.5 s → '{e}'"}
    run("UND-CUENTAS-03", "Ventana de 30 s: a los 29 s se puede restaurar y a los 31 s no", "UND", "ambos", und_cuentas_03)

    def und_cuentas_04():
        # cuenta cancelada sin pagos -> cerrada; el ping la archiva pasados 30 s
        acc, o, _ = new_account_order(c, "TU04", "Asada", 1)
        ok(cancel(c, acc))
        s2 = kitchen_state()
        assert check_account(s2, acc)["closedAt"], "la cuenta cancelada debía quedar cerrada"
        time.sleep(45)
        s3 = kitchen_state()
        hist = kitchen_history()
        assert all(a["id"] != acc for a in s3["accounts"]), "la cuenta debía archivarse"
        assert any(h["account"]["id"] == acc for h in hist), "debía estar en historial.jsonl"
        return {"esperado": "A los 30 s la cuenta cerrada pasa a historial.jsonl",
                "obtenido": f"archivada; historial con {len(hist)} entradas"}
    run("UND-CUENTAS-04", "Tras 30 s la cuenta se archiva y ya no se puede deshacer", "UND", "cocina", und_cuentas_04)

    # ---------- ERR protocolo ----------
    def err_protocolo_01():
        cl = Client()
        try:
            cl.send_raw("esto no es json\n")
            try:
                cl.recv(timeout=1.2)
                raise AssertionError("el servidor no debía responder a JSON inválido")
            except (socket.timeout, TimeoutError):
                pass
            r = ok(create(cl, "TE01", [lines_(pid("Asada", st), 1)]))
            return {"esperado": "JSON inválido se ignora; la conexión sigue viva",
                    "obtenido": "sin respuesta al inválido; orden válida creada después"}
        finally:
            cl.close()
    run("ERR-PROTOCOLO-01", "JSON inválido por TCP se ignora y la conexión sigue viva", "ERR", "cocina", err_protocolo_01)

    def err_protocolo_02():
        cl = Client()
        try:
            cl.send_raw(json.dumps({"type": "create", "reqId": 1, "clientKey": "x", "table": "TE02",
                                    "accountId": None, "lines": [], "pad": "x" * 70_000}) + "\n")
            closed = False
            try:
                for _ in range(5):
                    cl.recv(timeout=1.5)
            except (EOFError, ConnectionError, socket.timeout, OSError):
                closed = True
            assert closed, "la conexión debía cerrarse con mensaje > 64 KB"
            c2 = Client()
            ok(create(c2, "TE02", [lines_(pid("Asada", st), 1)]))
            c2.close()
            return {"esperado": "Mensaje > 64 KB cierra esa conexión pero el servidor sigue vivo",
                    "obtenido": "conexión cerrada; nueva conexión crea orden OK"}
        finally:
            cl.close()
    run("ERR-PROTOCOLO-02", "Mensaje de más de 64 KB corta la conexión sin tumbar el servidor", "ERR", "cocina", err_protocolo_02)

    def err_protocolo_03():
        cl = Client()
        try:
            cl.send_raw(json.dumps({"type": "tipoDesconocido", "reqId": 2}) + "\n")
            try:
                cl.recv(timeout=1.0)
                raise AssertionError("no debía responder")
            except (socket.timeout, TimeoutError):
                pass
            ok(create(cl, "TE03", [lines_(pid("Asada", st), 1)]))
            return {"esperado": "Tipo desconocido se ignora sin cerrar la conexión",
                    "obtenido": "ignorado; siguiente mensaje válido procesado"}
        finally:
            cl.close()
    run("ERR-PROTOCOLO-03", "Tipo de mensaje o enum desconocido se ignora", "ERR", "cocina", err_protocolo_03)

    # ---------- PER ----------
    def per_historial_02():
        acc, o, _ = new_account_order(c, "TP01", "Asada", 1)
        ok(pay(c, acc, 2000, deliver=False))   # pagada, orden sigue en cocina
        ok(cancel(c, acc))                     # cuenta cancelada + orden cancelada; saldo -2000
        ok(pay(c, acc, -2000, key="ph2-refund"))  # devolución: saldo 0 y cuenta cerrada
        time.sleep(45)
        hist = kitchen_history()
        ent = next((h for h in hist if h["account"]["id"] == acc), None)
        assert ent, "la cuenta no se archivó"
        assert ent["account"]["payments"] and ent["account"]["cancelledAt"], "el historial debe conservar pago y cancelación"
        assert any(x["status"] == "CANCELLED" for x in ent["orders"]), "debe conservar la orden cancelada"
        return {"esperado": "Historial conserva la cuenta con pago y cancelación",
                "obtenido": f"entrada con {len(ent['account']['payments'])} pago(s) y {len(ent['orders'])} orden(es)"}
    run("PER-HISTORIAL-02", "El historial conserva cancelaciones y pagos de una cuenta cancelada", "PER", "cocina", per_historial_02)

    # ---------- SEG / FUN ----------
    def seg_auth_01():
        r = ok(create(c, "TS01", [lines_(pid("Asada", st), 1)]))
        return {"esperado": "Un cliente TCP sin autenticación puede crear órdenes (riesgo aceptado)",
                "obtenido": f"orden #{r['order']['number']} creada sin credenciales"}
    run("SEG-AUTENTICACION-01", "Sin autenticación: cualquier equipo de la red puede crear órdenes", "SEG", "ambos", seg_auth_01)

    def seg_auth_02():
        acc, o, _ = new_account_order(c, "TS02", "Asada", 1)
        ok(cancel(c, acc))
        return {"esperado": "Un cliente TCP sin autenticación puede cancelar cuentas (riesgo aceptado)",
                "obtenido": "cuenta cancelada sin credenciales"}
    run("SEG-AUTENTICACION-02", "Sin autenticación: cualquier cliente puede cancelar o anular pagos", "SEG", "ambos", seg_auth_02)

    def seg_protocolo_01():
        note = 'Dijo "sin cebolla" \\ y 😀'
        r = ok(create(c, "TS03", [lines_(pid("Asada", st), 1, note=note)]))
        got = r["order"]["lines"][0]["note"]
        assert got == note, got
        s2 = kitchen_state()
        assert check_order(s2, r["order"]["id"])["lines"][0]["note"] == note
        return {"esperado": "Nota con comillas, diagonal y emoji se conserva íntegra",
                "obtenido": repr(got)}
    run("SEG-PROTOCOLO-01", "Texto del usuario escapado: una nota no rompe el protocolo", "SEG", "ambos", seg_protocolo_01)

    def fun_cobro_04():
        acc1, o1, _ = new_account_order(c, "TF01", "Asada", 1)
        acc2, o2, _ = new_account_order(c, "TF01", "Pastor", 1)
        ok(pay(c, acc1, 2000))
        s2 = kitchen_state()
        a1, a2 = check_account(s2, acc1), check_account(s2, acc2)
        assert a1["payments"][0]["amountCents"] == 2000 and not a2["payments"]
        return {"esperado": "Pagar cuenta 1 no paga la cuenta 2 de la misma mesa",
                "obtenido": "Cuenta 1 pagada; Cuenta 2 sin pagos"}
    run("FUN-COBRO-04", "Pagar una cuenta no paga las demás de la misma mesa", "FUN", "ambos", fun_cobro_04)

    def fun_cobro_05():
        acc, o, _ = new_account_order(c, "TF02", "Asada", 2)  # 4000
        ok(pay(c, acc, 2000, "CASH"))
        ok(pay(c, acc, 2000, "TRANSFER"))
        s2 = kitchen_state()
        a = check_account(s2, acc)
        oo = check_order(s2, o["id"])
        paid = sum(p["amountCents"] for p in a["payments"] if not p["voided"])
        assert paid == 4000 and oo["status"] == "PENDING", (paid, oo["status"])
        return {"esperado": "Pago parcial efectivo+transferencia completa el saldo sin sacar de cocina",
                "obtenido": "pagado 4000; orden sigue PENDING"}
    run("FUN-COBRO-05", "Pago parcial efectivo + transferencia sin sacar la orden de cocina", "FUN", "ambos", fun_cobro_05)

    def fun_captura_05():
        a = pid("Pastor", st)
        r = ok(create(c, "TF03", [
            lines_(a, 1, ["Sin salsa", "Cebolla aparte"]),
            lines_(a, 1, ["Cebolla aparte", "Sin salsa"]),
            lines_(a, 2, ["Bien dorado", "Con queso"]),
        ]))
        qs = [l["qty"] for l in r["order"]["lines"]]
        assert qs == [2, 2], qs
        return {"esperado": "Grupos idénticos se unen (1+1→2) y Extras combinables se aceptan",
                "obtenido": f"cantidades={qs}"}
    run("FUN-CAPTURA-05", "Grupos idénticos se unen y Extras combinables se aceptan (payload TCP)", "FUN", "ambos", fun_captura_05)

    def fun_cuentas_02():
        acc1, o1, _ = new_account_order(c, "TF04", "Asada", 1)
        acc2, o2, _ = new_account_order(c, "TF04", "Refresco", 1)
        assert o1["accountNumber"] == 1 and o2["accountNumber"] == 2, (o1["accountNumber"], o2["accountNumber"])
        return {"esperado": "Segunda cuenta de la misma mesa es la número 2",
                "obtenido": f"cuenta {o1['accountNumber']} y cuenta {o2['accountNumber']}"}
    run("FUN-CUENTAS-02", "Segunda cuenta de la misma mesa con «+ Cuenta»", "FUN", "ambos", fun_cuentas_02)

    # Cierre
    c.close()
    with open(os.path.join(BASE, "datos", "protocolo-estado-final.json"), "w") as f:
        json.dump(kitchen_state(), f, ensure_ascii=False, indent=1)
    with open(OUT, "w") as f:
        json.dump({"generado": time.strftime("%Y-%m-%dT%H:%M:%S%z"), "casos": RESULTS}, f, ensure_ascii=False, indent=2)
    print(f"\n{len(RESULTS)} casos -> {OUT}")
    fallos = [r for r in RESULTS if r["estado"] != "PASA"]
    print("FALLAS:", [(r["id"], r["notas"]) for r in fallos] or "ninguna")


if __name__ == "__main__":
    main()
