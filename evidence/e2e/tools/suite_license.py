#!/usr/bin/env python3
"""Fase E2E de licencia: activación, suspensión, vencimiento, revocación, aviso
de vencimiento, errores de activación, rechazo a meseras y URL del QR conservada.

Usa el mock de Axzy Central en :4100/:4101 y escribe la caché de licencia real
del emulador de cocina. Al final restaura el respaldo del Lead.
"""
import json
import os
import shutil
import subprocess
import sys
import time

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
K = "emulator-5554"
W = "emulator-5556"
PKG = "com.axzy.taqueria"
MODE = os.path.join(BASE, "mock", "mode.json")
CAP = os.path.join(BASE, "capturas")
DAT = os.path.join(BASE, "datos", "licencia")
os.makedirs(DAT, exist_ok=True)
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ui  # noqa: E402

RESULTS = []


def adb(*args, serial=K, timeout=60):
    return subprocess.run(["adb", "-s", serial] + list(args), capture_output=True, text=True, timeout=timeout)


def set_mode(mode):
    json.dump({"mode": mode}, open(MODE, "w"))
    time.sleep(0.4)


def texts(serial=K):
    try:
        return [n["text"] or n["desc"] for n in ui.dump(serial)]
    except Exception:
        return []


def has(t, serial=K):
    return any(t in (x or "") for x in texts(serial))


def wait(t, timeout=15, serial=K):
    end = time.time() + timeout
    while time.time() < end:
        if has(t, serial):
            return True
        time.sleep(0.5)
    return False


def tap(t, serial=K, contains=True):
    for _ in range(4):
        n = ui.find(ui.dump(serial), t, contains=contains)
        if n:
            ui.tap_node(serial, n)
            time.sleep(1.0)
            return True
        time.sleep(0.6)
    return False


def shot(name, serial=K):
    subprocess.run(["adb", "-s", serial, "exec-out", "screencap", "-p"],
                   stdout=open(os.path.join(CAP, name + ".png"), "wb"))


def cache_xml(cache, api_base=None):
    raw = json.dumps(cache, ensure_ascii=False).replace("&", "&amp;").replace('"', "&quot;")
    lines = ["<?xml version='1.0' encoding='utf-8' standalone='yes' ?>", "<map>"]
    lines.append(f'    <string name="cache">{raw}</string>')
    if api_base:
        lines.append(f'    <string name="apiBase">{api_base}</string>')
    lines.append("</map>")
    return "\n".join(lines)


def write_prefs(cache, api_base=None):
    xml = cache_xml(cache, api_base).encode()
    open("/tmp/lic.xml", "wb").write(xml)
    adb("push", "/tmp/lic.xml", "/data/local/tmp/lic.xml")
    adb("shell", "run-as", PKG, "cp", "/data/local/tmp/lic.xml", "shared_prefs/license.xml")
    adb("shell", "run-as", PKG, "chmod", "660", "shared_prefs/license.xml")


def restart_kitchen(wait_s=6):
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
    time.sleep(wait_s)


def cache(cache_obj):
    return json.loads(adb("shell", "run-as", PKG, "cat", "shared_prefs/license.xml").stdout
                      if "&quot;" not in adb("shell", "run-as", PKG, "cat", "shared_prefs/license.xml").stdout
                      else "{}")


def read_cache():
    out = adb("shell", "run-as", PKG, "cat", "shared_prefs/license.xml").stdout
    import html
    import re
    m = re.search(r'<string name="cache">(.*?)</string>', out, re.S)
    if not m:
        return None
    try:
        return json.loads(html.unescape(m.group(1)))
    except Exception:
        return None


def now_ms():
    return int(time.time() * 1000)


def base_cache(token="axc_mock_ok_123", status="ACTIVE", revoked=False, valid_delta_days=15, max_seen=None):
    n = now_ms()
    return {
        "token": token,
        "status": status,
        "revoked": revoked,
        "tenantName": "Taquería El Fogón",
        "nextDueDate": "2026-12-06",
        "validUntilMs": n + valid_delta_days * 24 * 3600 * 1000,
        "clockOffsetMs": 0,
        "maxSeenMs": max_seen or n,
        "lastCheckMs": n,
    }


def case(cid, title, typ, expected, actual, status="PASA", evidence=""):
    RESULTS.append({"id": cid, "titulo": title, "tipo": typ, "dispositivo": "cocina",
                    "esperado": expected, "obtenido": actual, "estado": status,
                    "evidencia": [e for e in evidence.split(",") if e], "datos": [], "notas": ""})
    print(f"[{status}] {cid}: {actual[:120]}")


def main():
    # ------------------ A) suspensión ------------------
    set_mode("suspended")
    restart_kitchen(7)
    t = texts()
    ok_lock = any("Cuenta suspendida" in x for x in t)
    shot("51-licencia-suspendida")
    case("LIC-LICENCIA-08", "Pantalla de bloqueo por suspensión con «Reintentar»", "LIC",
         "La cocina muestra «Cuenta suspendida» con Reintentar y no opera",
         f"{'Se mostró «Cuenta suspendida» y Reintentar' if ok_lock else 'NO se mostró la pantalla de suspensión: '+str(t[:4])}",
         "PASA" if ok_lock else "FALLA", "capturas/51-licencia-suspendida.png")

    # A2) servidor rechaza meseras mientras está bloqueada
    try:
        import suite_protocolo as S
        cl = S.Client()
        r = cl.request({"type": "create", "reqId": 900001, "clientKey": "lic-locked", "table": "L",
                        "accountId": None, "lines": []})
        cl.close()
        msg = r.get("error") or ""
        ok_reject = "suspendida" in msg.lower()
        case("LIC-LICENCIA-11", "Con la cocina bloqueada, el servidor rechaza a las meseras", "LIC",
             "Un create por TCP recibe el mensaje de cuenta suspendida",
             f"Respuesta del servidor: '{msg}'" if ok_reject else f"No se rechazó: {r}",
             "PASA" if ok_reject else "FALLA")
    except Exception as e:
        case("LIC-LICENCIA-11", "Con la cocina bloqueada, el servidor rechaza a las meseras", "LIC",
             "Create por TCP rechazado con mensaje de suspensión", f"Error del arnés: {e}", "FALLA")

    # ------------------ B) reactivación ------------------
    set_mode("ok")
    tap("Reintentar")
    ok_react = wait("Cocina ·", 15)
    n_orders = len(json.loads(adb("exec-out", "run-as", PKG, "cat", "files/taqueria.json").stdout).get("orders", []))
    shot("52-licencia-reactivada")
    case("LIC-LICENCIA-12", "Al reactivar, las órdenes y el menú siguen intactos", "LIC",
         "Tras Reintentar con licencia activa vuelve el monitor y conserva las órdenes",
         f"Monitor de vuelta: {ok_react}; órdenes conservadas: {n_orders}",
         "PASA" if ok_react and n_orders >= 1 else "FALLA", "capturas/52-licencia-reactivada.png")

    # ------------------ C) vencimiento ------------------
    set_mode("expired")
    restart_kitchen(7)
    t = texts()
    ok_exp = any("Falta confirmar la cuenta" in x for x in t)
    shot("53-licencia-vencida")
    case("LIC-LICENCIA-09", "Pantalla de bloqueo por vencimiento sin conexión", "LIC",
         "La cocina muestra «Falta confirmar la cuenta»",
         f"{'Se mostró el bloqueo por vencimiento' if ok_exp else 'NO se mostró: '+str(t[:4])}",
         "PASA" if ok_exp else "FALLA", "capturas/53-licencia-vencida.png")

    # ------------------ D) revocación + Escribir otro código + URL conservada ------------------
    set_mode("invalid")
    write_prefs(base_cache(), api_base="http://10.0.2.2:4101/api/v1")
    restart_kitchen(8)
    t = texts()
    ok_rev = any("Teléfono no autorizado" in x for x in t)
    shot("54-licencia-revocada")
    has_btn = any("Escribir otro código" in x for x in t)
    case("LIC-LICENCIA-10", "Código revocado (401 en GET /license) y «Escribir otro código»", "LIC",
         "La cocina muestra «Teléfono no autorizado» y el botón «Escribir otro código»",
         f"Revocada: {ok_rev}; botón presente: {has_btn}",
         "PASA" if ok_rev and has_btn else "FALLA", "capturas/54-licencia-revocada.png")

    # Al tocar "Escribir otro código" se borra la caché pero debe conservar apiBase=4101
    tap("Escribir otro código")
    ok_act_screen = wait("Activa esta cocina", 10)
    set_mode("ok")
    ui.set_text(K, "Código de activación", "axc_qr_url_4101")
    tap("Activar")
    ok_mon = wait("Cocina ·", 15)
    time.sleep(1)
    log4101 = ""
    p = os.path.join(BASE, "logs", "mock-4101.log")
    if os.path.exists(p):
        log4101 = open(p).read()
    ok_url = "POST /api/v1/activate" in log4101
    shot("55-licencia-url-conservada")
    case("LIC-LICENCIA-14", "«Escribir otro código» conserva la URL del servidor del QR", "LIC",
         "Tras revocar y pulsar Escribir otro código, la activación usa apiBase guardada (:4101)",
         f"Pantalla de activación: {ok_act_screen}; monitor: {ok_mon}; petición vista en mock :4101: {ok_url}",
         "PASA" if ok_mon and ok_url else "FALLA", "capturas/55-licencia-url-conservada.png")

    # ------------------ E) aviso de vencimiento (expiresSoon) ------------------
    set_mode("soon")
    restart_kitchen(7)
    t = texts()
    ok_soon = any("Conecta este teléfono a internet antes del" in x for x in t)
    shot("56-licencia-por-vencer")
    case("BOR-LICENCIA-02", "Aviso de vencimiento justo en los 3 días", "BOR",
         "Con validUntil a 2 días la cocina muestra el aviso de conectar a internet",
         f"{'Se mostró el aviso de vencimiento próximo' if ok_soon else 'NO se mostró: '+str(t[:5])}",
         "PASA" if ok_soon else "FALLA", "capturas/56-licencia-por-vencer.png")

    # ------------------ F) errores de activación ------------------
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "run-as", PKG, "rm", "-f", "shared_prefs/license.xml")
    set_mode("invalid")
    restart_kitchen(5)
    if not wait("Activa esta cocina", 10):
        case("ERR-LICENCIA-01", "Activación con código inválido o revocado (401)", "ERR",
             "La app muestra error de código inválido", "No apareció la pantalla de activación", "FALLA")
    else:
        ui.set_text(K, "Código de activación", "axc_invalido")
        tap("Activar")
        time.sleep(3)
        t = texts()
        ok = any("Código inválido o revocado" in x for x in t)
        shot("57-error-activacion-401")
        case("ERR-LICENCIA-01", "Activación con código inválido o revocado (401)", "ERR",
             "Error «Código inválido o revocado. Pide uno nuevo a tu proveedor.»",
             "Mensaje mostrado" if ok else f"No se mostró el mensaje esperado: {[x for x in t if x][:6]}",
             "PASA" if ok else "FALLA", "capturas/57-error-activacion-401.png")

        set_mode("error500")
        tap("Activar")
        time.sleep(4)
        t = texts()
        ok500 = any("El servidor respondió 500" in x for x in t)
        shot("58-error-activacion-500")
        case("ERR-LICENCIA-03", "El servidor de licencias responde 500", "ERR",
             "Mensaje «El servidor respondió 500. Intenta más tarde.»",
             "Mensaje mostrado" if ok500 else f"No: {[x for x in t if x][:6]}",
             "PASA" if ok500 else "FALLA", "capturas/58-error-activacion-500.png")

        set_mode("badjson")
        tap("Activar")
        time.sleep(4)
        t = texts()
        okjson = any("No se pudo leer la respuesta" in x for x in t)
        shot("59-error-activacion-json")
        case("ERR-LICENCIA-04", "Respuesta de licencia que no es JSON válido", "ERR",
             "Mensaje «No se pudo leer la respuesta del servidor.»",
             "Mensaje mostrado" if okjson else f"No: {[x for x in t if x][:6]}",
             "PASA" if okjson else "FALLA", "capturas/59-error-activacion-json.png")

        # sin internet: se detienen los mocks
        subprocess.run(["pkill", "-f", "mock_central.py"], capture_output=True)
        time.sleep(1)
        tap("Activar")
        ok_off = wait("Sin internet", 20)
        shot("60-error-activacion-sin-internet")
        case("ERR-LICENCIA-02", "Activación sin internet", "ERR",
             "Mensaje «Sin internet. Conecta este teléfono a internet e intenta otra vez.»",
             f"{'Mensaje mostrado' if ok_off else 'No se mostró el mensaje de sin internet'}",
             "PASA" if ok_off else "FALLA", "capturas/60-error-activacion-sin-internet.png")

    # ------------------ G) restaurar licencia real ------------------
    adb("shell", "am", "force-stop", PKG)
    adb("push", os.path.join(DAT, "license-backup.xml"), "/data/local/tmp/lic-backup.xml")
    adb("shell", "run-as", PKG, "cp", "/data/local/tmp/lic-backup.xml", "shared_prefs/license.xml")
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
    time.sleep(8)
    ok_restore = wait("Cocina ·", 15)
    case("PER-PERSISTENCIA-06", "La cache de licencia sobrevive al reinicio y funciona sin internet", "PER",
         "Con la licencia restaurada y sin mock, la cocina arranca activa",
         f"Monitor activo tras restaurar cache: {ok_restore}",
         "PASA" if ok_restore else "FALLA")

    json.dump({"generado": time.strftime("%Y-%m-%dT%H:%M:%S%z"), "casos": RESULTS},
              open(os.path.join(BASE, "licencia.json"), "w"), ensure_ascii=False, indent=2)
    print(f"\n{sum(1 for r in RESULTS if r['estado']=='PASA')}/{len(RESULTS)} casos de licencia -> evidence/e2e/licencia.json")


if __name__ == "__main__":
    main()
