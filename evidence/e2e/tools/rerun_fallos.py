#!/usr/bin/env python3
"""Re-ejecuta los casos de protocolo que fallaron por bugs del arnés o fixture."""
import json, os, socket, sys, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import suite_protocolo as S

BASE = S.BASE
OUT = S.OUT

results = []

def run(cid, title, typ, device, fn):
    t0 = time.time()
    try:
        d = fn()
        results.append({"id": cid, "titulo": title, "tipo": typ, "dispositivo": device,
                        "esperado": d.get("esperado",""), "obtenido": d.get("obtenido",""),
                        "estado": d.get("estado","PASA"), "ms": int((time.time()-t0)*1000), "notas": d.get("notas","")})
        print("PASA", cid)
    except Exception as e:
        results.append({"id": cid, "titulo": title, "tipo": typ, "dispositivo": device,
                        "esperado": "ver pasos", "obtenido": f"FALLA: {e}", "estado": "FALLA",
                        "ms": int((time.time()-t0)*1000), "notas": str(e)})
        print("FALLA", cid, e)

st = S.fresh_state()
c = S.Client()

def val_captura_06():
    a = S.pid("Asada", st)
    e1 = S.expect_error(S.create(c, "TV06b", [S.lines_(a,1)], key=""), "Solicitud inválida")
    e2 = S.expect_error(S.create(c, "TV06c", [S.lines_(a,1)], key="x"*65), "Solicitud inválida")
    return {"esperado":"clientKey vacío y >64 se rechazan","obtenido":f"'{e1}' / '{e2}'"}
run("VAL-CAPTURA-06","Llave de idempotencia vacía o de más de 64 caracteres","VAL","cocina",val_captura_06)

def _invalid_then_valid(cl, invalid, table, reqid_expected):
    """Envía un mensaje inválido y luego uno válido; comprueba que solo responde al válido."""
    a = S.pid("Asada", st)
    valid = {"type":"create","reqId":reqid_expected,"clientKey":"inv-"+table,"table":table,
             "accountId":None,"lines":[S.lines_(a,1)]}
    cl.send_raw(invalid + "\n")
    cl.send_raw(json.dumps(valid, ensure_ascii=False) + "\n")
    others = []
    for _ in range(6):
        m = cl.recv(timeout=8)
        if m.get("type") == "reply":
            if m.get("reqId") == reqid_expected:
                assert not m.get("error"), m
                return others
            others.append(m)
    raise AssertionError("no llegó reply al mensaje válido")

def err_protocolo_01():
    cl = S.Client()
    try:
        others = _invalid_then_valid(cl, "esto no es json", "TE01b", 880001)
        assert not [m for m in others if m.get("type")=="reply"], others
        return {"esperado":"JSON inválido se ignora; la conexión sigue viva",
                "obtenido":"sin reply al inválido; la siguiente orden válida se procesó"}
    finally:
        cl.close()
run("ERR-PROTOCOLO-01","JSON inválido por TCP se ignora y la conexión sigue viva","ERR","cocina",err_protocolo_01)

def err_protocolo_03():
    cl = S.Client()
    try:
        others = _invalid_then_valid(cl, json.dumps({"type":"tipoDesconocido","reqId":999999}), "TE03b", 880002)
        assert not [m for m in others if m.get("type")=="reply"], others
        return {"esperado":"Tipo desconocido se ignora sin cerrar la conexión",
                "obtenido":"ignorado; la siguiente orden válida se procesó"}
    finally:
        cl.close()
run("ERR-PROTOCOLO-03","Tipo de mensaje o enum desconocido se ignora","ERR","cocina",err_protocolo_03)

def per_historial_02():
    acc, o, _ = S.new_account_order(c, "TPH2", "Asada", 1)
    S.ok(S.pay(c, acc, 2000, deliver=False))
    S.ok(S.cancel(c, acc))
    S.ok(S.pay(c, acc, -2000, key="ph2-refund-rerun"))
    time.sleep(45)
    hist = S.kitchen_history()
    ent = next((h for h in hist if h["account"]["id"] == acc), None)
    assert ent, "la cuenta no se archivó"
    assert ent["account"]["payments"] and ent["account"]["cancelledAt"], "el historial debe conservar pagos y cancelación"
    assert any(x["status"] == "CANCELLED" for x in ent["orders"]), "debe conservar la orden cancelada"
    return {"esperado":"Historial conserva la cuenta con pagos y cancelación","obtenido":f"entrada con {len(ent['account']['payments'])} pago(s) y {len(ent['orders'])} orden(es) cancelada(s)"}
run("PER-HISTORIAL-02","El historial conserva cancelaciones y pagos de una cuenta cancelada","PER","cocina",per_historial_02)

def fun_cobro_05():
    acc, o, _ = S.new_account_order(c, "TF02b", "Asada", 2)
    S.ok(S.pay(c, acc, 2000, "CASH"))
    S.ok(S.pay(c, acc, 2000, "TRANSFER"))
    s2 = S.kitchen_state(); a = S.check_account(s2, acc); oo = S.check_order(s2, o["id"])
    paid = sum(p["amountCents"] for p in a["payments"] if not p["voided"])
    assert paid == 4000 and oo["status"] == "PENDING", (paid, oo["status"])
    return {"esperado":"Pago parcial efectivo+transferencia completa saldo sin sacar de cocina","obtenido":"pagado 4000; orden PENDING"}
run("FUN-COBRO-05","Pago parcial efectivo + transferencia sin sacar la orden de cocina","FUN","ambos",fun_cobro_05)

c.close()
# merge en protocolo.json
doc = json.load(open(OUT))
byid = {x["id"]: x for x in doc["casos"]}
for r in results:
    byid[r["id"]] = r
doc["casos"] = list(byid.values())
doc["generado"] = time.strftime("%Y-%m-%dT%H:%M:%S%z")
json.dump(doc, open(OUT,"w"), ensure_ascii=False, indent=2)
print("total tras reintento:", len(doc["casos"]), "PASA:", sum(1 for x in doc["casos"] if x["estado"]=="PASA"))
