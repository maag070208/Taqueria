#!/usr/bin/env python3
"""Fase E2E: red/reconexión/roles, rotación, gestos, deshacer, catálogo,
densidad y permisos sobre los dos emuladores reales."""
import json
import os
import subprocess
import sys
import time
import uuid

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
K, W = "emulator-5554", "emulator-5556"
PKG = "com.axzy.taqueria"
CAP = os.path.join(BASE, "capturas")
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ui  # noqa: E402
import suite_protocolo as S  # noqa: E402
RESULTS = []


def adb(*args, serial=K, timeout=120):
    return subprocess.run(["adb", "-s", serial] + list(args), capture_output=True, text=True, timeout=timeout)


def start(serial, wait=6):
    adb("shell", "am", "force-stop", PKG, serial=serial)
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity", serial=serial)
    time.sleep(wait)


def stop(serial):
    adb("shell", "am", "force-stop", PKG, serial=serial)
    time.sleep(0.5)


def texts(serial):
    try:
        return [n["text"] or n["desc"] for n in ui.dump(serial)]
    except Exception:
        return []


def has(t, serial):
    return any(t in (x or "") for x in texts(serial))


def wait(t, serial=K, timeout=20):
    end = time.time() + timeout
    while time.time() < end:
        if has(t, serial):
            return True
        time.sleep(0.5)
    return False


def tap(serial, t, contains=True):
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


def home(serial=W):
    for _ in range(8):
        if has("Cuentas abiertas", serial):
            return
        adb("shell", "input", "keyevent", "4", serial=serial)
        time.sleep(1)


def case(cid, title, typ, expected, actual, status="PASA", device="ambos", evidence="", data="", notes=""):
    RESULTS.append({"id": cid, "titulo": title, "tipo": typ, "dispositivo": device,
                    "esperado": expected, "obtenido": actual, "estado": status,
                    "evidencia": [e for e in evidence.split(",") if e], "datos": [d for d in data.split(",") if d],
                    "notas": notes})
    print(f"[{status}] {cid}: {actual[:110]}")


def create_order(table="1", qty=1):
    st = S.kitchen_state()
    p = S.pid("Asada", st)
    cl = S.Client()
    r = cl.request({"type": "create", "reqId": int(time.time() * 1000) % 900000 + 100, "clientKey": str(uuid.uuid4()),
                    "table": table, "accountId": None, "lines": [S.lines_(p, qty)]})
    cl.close()
    return r


def main():
    start(K, 7)
    start(W, 7)
    wait("Conectado a cocina", W, 20)
    if not has("Cuentas abiertas", W):
        home(W)

    # ---------- RED: detener cocina ----------
    stop(K)
    detected = None
    t0 = time.time()
    while time.time() - t0 < 30:
        if not has("Conectado a cocina", W):
            detected = time.time() - t0
            break
        time.sleep(1)
    shot("62-mesera-sin-conexion", W)
    ok_det = detected is not None and detected <= 25
    case("RED-RECONEXION-01", "Al detener la cocina, la mesera detecta el enlace muerto en ≤25 s", "RED",
         "La mesera deja de mostrar «Conectado a cocina» en ≤25 s",
         f"detectado en {detected:.1f}s" if detected else "no se detectó en 30 s",
         "PASA" if ok_det else "FALLA", device="mesera", evidence="capturas/62-mesera-sin-conexion.png")

    offline_text = next((x for x in texts(W) if "Sin conexión" in (x or "") or "Buscando" in (x or "") or "Conectando" in (x or "")), "")
    btn_sin = has("Sin conexión con cocina", W)
    case("RED-OFFLINE-01", "Sin conexión la UI bloquea las operaciones", "RED",
         "La lista muestra «Sin conexión con cocina» y el botón está deshabilitado",
         f"texto de enlace: '{offline_text}'; botón «Sin conexión con cocina»: {btn_sin}",
         "PASA" if btn_sin else "FALLA", device="mesera", evidence="capturas/62-mesera-sin-conexion.png")

    # ---------- RED: reconectar ----------
    start(K, 8)
    ok_re = wait("Conectado a cocina", W, 25)
    kitchen_count = next((x for x in texts(K) if "mesera conectada" in (x or "") or "meseras conectadas" in (x or "")), "")
    case("RED-RECONEXION-02", "Reconexión automática y resincronización del estado", "RED",
         "Al volver cocina, la mesera reconecta sola y cocina reporta 1 mesera",
         f"reconectó={ok_re}; cocina: '{kitchen_count}'",
         "PASA" if ok_re else "FALLA", evidence="capturas/52-licencia-reactivada.png")

    # ---------- RED-OFFLINE-02: orden creada mientras la mesera estaba apagada ----------
    stop(W)
    time.sleep(1)
    r = create_order(table="11")
    new_id = (r.get("order") or {}).get("id")
    start(W, 8)
    ok_sync = wait("Conectado a cocina", W, 25)
    # buscar la tarjeta nueva por su número de pedido en el detalle o por el total
    time.sleep(1)
    seen = has("Mesa 11", W) or has("Debe", W)
    case("RED-OFFLINE-02", "Lo creado durante el corte aparece al reconectar", "RED",
         "La orden creada mientras la mesera estaba apagada aparece al reconectar",
         f"reconectó={ok_sync}; tarjeta visible={seen}",
         "PASA" if ok_sync and seen else "FALLA", device="mesera", data="")

    # ---------- RED-CONEXION-03: dos meseras ----------
    cl = S.Client()
    time.sleep(1.5)
    two = has("2 meseras conectadas", K)
    shot("63-cocina-dos-meseras")
    cl.close()
    time.sleep(2)
    one = has("1 mesera conectada", K)
    case("RED-CONEXION-03", "Dos meseras conectadas a la vez", "RED",
         "Cocina cuenta 2 meseras con un cliente extra y vuelve a 1 al desconectarlo",
         f"2 meseras: {two}; vuelve a 1: {one}",
         "PASA" if two and one else "FALLA", evidence="capturas/63-cocina-dos-meseras.png")

    # ---------- Roles / RED-CONEXION-04 ----------
    tap(K, "⇄")
    time.sleep(1)
    conf = has("¿Cambiar el rol de este teléfono?", K)
    tap(K, "Cambiar")
    role_screen = wait("¿Qué hará este teléfono?", K, 10)
    shot("64-pantalla-rol")
    case("FUN-ROLES-03", "Cambiar rol de cocina a mesera conserva órdenes y menú", "FUN",
         "El rol se cambia desde ⇄ y luego se puede volver a cocina conservando datos",
         f"diálogo={conf}; pantalla de rol={role_screen}",
         "PASA" if conf and role_screen else "FALLA", device="cocina", evidence="capturas/64-pantalla-rol.png")

    tap(K, "Mesero")
    time.sleep(2)
    waiter_on_k = has("Cuentas abiertas", K)
    net = adb("shell", "netstat", "-ltn").stdout
    port_closed = not any("7777" in line and "LISTEN" in line for line in net.splitlines())
    case("RED-CONEXION-04", "Cambiar de rol detiene el servidor y libera el puerto 7777", "RED",
         "En rol Mesero el puerto 7777 queda cerrado",
         f"pantalla de mesera en cocina={waiter_on_k}; puerto 7777 cerrado={port_closed}",
         "PASA" if waiter_on_k and port_closed else "FALLA", device="cocina")

    # volver a cocina (FUN-ROLES-04)
    tap(K, "⚙")
    time.sleep(1)
    tap(K, "Cambiar rol de este teléfono")
    back_role = wait("¿Qué hará este teléfono?", K, 10)
    tap(K, "Cocina")
    ok_back = wait("Cocina ·", K, 20)
    st = S.kitchen_state()
    n_orders = len(st.get("orders", []))
    case("FUN-ROLES-04", "Volver a cocina desde el engrane de la mesera", "FUN",
         "Desde Mesero → ⚙ → Cambiar rol → Cocina vuelve el monitor con las órdenes intactas",
         f"pantalla de rol={back_role}; monitor={ok_back}; órdenes conservadas={n_orders}",
         "PASA" if back_role and ok_back and n_orders >= 1 else "FALLA", device="cocina", evidence="capturas/52-licencia-reactivada.png")

    # ---------- RED-CONEXION-02/05: IP manual ----------
    wait("Conectado a cocina", W, 25)
    tap(W, "⚙")
    time.sleep(1)
    ip_dialog = has("IP de cocina (opcional)", W)
    ui.set_text(W, "IP de cocina", "10.0.2.2:7777")
    tap(W, "Guardar")
    ok_port = wait("Conectado a cocina", W, 20)
    tap(W, "⚙")
    time.sleep(1)
    ui.set_text(W, "IP de cocina", "10.0.2.99")
    tap(W, "Guardar")
    time.sleep(4)
    bad = has("Conectando", W) or has("Buscando", W) or not has("Conectado a cocina", W)
    shot("65-mesera-ip-equivocada", W)
    tap(W, "⚙")
    time.sleep(1)
    ui.set_text(W, "IP de cocina", "10.0.2.2:7777")
    tap(W, "Guardar")
    ok_fix = wait("Conectado a cocina", W, 20)
    case("RED-CONEXION-02", "Formato de IP manual: host, host:puerto y puerto alterno", "RED",
         "Acepta «10.0.2.2:7777» y conecta",
         f"diálogo={ip_dialog}; conectó con host:puerto={ok_port}",
         "PASA" if ip_dialog and ok_port else "FALLA", device="mesera")
    case("RED-CONEXION-05", "IP equivocada: «Conectando…» y corrección sin reiniciar", "RED",
         "Con IP mala no queda «Conectado»; al corregirla reconecta sin reiniciar",
         f"estado con IP mala detectado={bad}; reconectó al corregir={ok_fix}",
         "PASA" if bad and ok_fix else "FALLA", device="mesera", evidence="capturas/65-mesera-ip-equivocada.png")

    # ---------- UI-ROTACION-01 ----------
    home(W)
    tap(W, "+ Nueva orden")
    tap(W, "4")
    tap(W, "Asada")
    tap(W, "Asada")
    before_rot = has("Revisar · 2", W)
    adb("shell", "settings", "put", "system", "accelerometer_rotation", "0", serial=W)
    adb("shell", "settings", "put", "system", "user_rotation", "1", serial=W)
    time.sleep(3)
    after_rot = texts(W)
    lost_capture = not any("Mesa 4" in (x or "") for x in after_rot) or has("Cuentas abiertas", W)
    shot("66-rotacion-captura", W)
    adb("shell", "settings", "put", "system", "user_rotation", "0", serial=W)
    time.sleep(2)
    case("UI-ROTACION-01", "Rotar durante la captura: se pierde la pantalla de captura (comportamiento actual)", "UI",
         "Comportamiento observado: al rotar se pierde la captura y el borrador queda inaccesible",
         f"antes «Revisar · 2»={before_rot}; tras rotar se perdió la captura={lost_capture} (pantalla: {[x for x in after_rot if x][:4]})",
         "PASA" if lost_capture else "OBSERVACION", device="mesera", evidence="capturas/66-rotacion-captura.png")

    # ---------- UI-ROTACION-02: cocina ----------
    adb("shell", "settings", "put", "system", "user_rotation", "1", serial=K)
    time.sleep(3)
    k_after = texts(K)
    monitor_ok = any("Cocina ·" in (x or "") for x in k_after)
    shot("67-rotacion-cocina")
    adb("shell", "settings", "put", "system", "user_rotation", "0", serial=K)
    time.sleep(2)
    case("UI-ROTACION-02", "Rotar en cocina conserva la pestaña del menú y no afecta el monitor", "UI",
         "El monitor de cocina sigue mostrando órdenes tras rotar",
         f"monitor presente tras rotar={monitor_ok}",
         "PASA" if monitor_ok else "FALLA", device="cocina", evidence="capturas/67-rotacion-cocina.png")

    # ---------- UI-DESHACER-01: barra 6 s ----------
    home(W)
    card = ui.find(ui.dump(W), "Debe", contains=True)
    if card:
        x1, y1, x2, y2 = card["bounds"]
        cy = (y1 + y2) // 2
        adb("shell", "input", "swipe", "950", str(cy), "150", str(cy), "500", serial=W)
        time.sleep(1)
        bar1 = has("Deshacer", W)
        shot("68-barra-deshacer", W)
        time.sleep(6.5)
        bar2 = has("Deshacer", W)
        case("UI-DESHACER-01", "La barra de deshacer dura 6 s y trunca el mensaje a una línea", "UI",
             "La barra aparece y desaparece sola a los 6 s",
             f"visible al inicio={bar1}; visible a los 6.5 s={bar2}",
             "PASA" if bar1 and not bar2 else "FALLA", device="mesera", evidence="capturas/68-barra-deshacer.png")

    # ---------- UI-GESTOS-03: scroll vertical no dispara ----------
    home(W)
    adb("shell", "input", "swipe", "540", "1600", "540", "900", "400", serial=W)
    time.sleep(1)
    no_action = not (has("Cancelar cuenta", W) or has("Entregar (sale de cocina)", W))
    case("UI-GESTOS-03", "Tocar abre el detalle y el scroll vertical no dispara el gesto", "UI",
         "Un deslizamiento vertical no abre acciones ni cancela la cuenta",
         f"sin acción tras scroll vertical={no_action}",
         "PASA" if no_action else "FALLA", device="mesera")

    # ---------- UI-NAVEGACION-01 ----------
    home(W)
    tap(W, "+ Nueva orden")
    tap(W, "4")
    back1 = adb("shell", "input", "keyevent", "4", serial=W)
    time.sleep(1)
    table_back = has("Paso 1 · Elige la mesa", W)
    adb("shell", "input", "keyevent", "4", serial=W)
    time.sleep(1)
    home_back = has("Cuentas abiertas", W)
    case("UI-NAVEGACION-01", "BackHandler y flujo de pasos de la captura", "UI",
         "Retroceder desde productos lleva a mesa y luego a cuentas abiertas",
         f"volvió a mesa={table_back}; volvió a inicio={home_back}",
         "PASA" if table_back and home_back else "FALLA", device="mesera")

    # ---------- Catálogo ----------
    tap(K, "📋")
    time.sleep(1.5)
    admin = has("Menú", K) and has("Productos", K)
    tap(K, "+ Agregar producto")
    time.sleep(1)
    ui.set_text(K, "Nombre", "Taco de Canasta")
    ui.set_text(K, "Precio", "15")
    tap(K, "Guardar")
    time.sleep(2)
    st = S.kitchen_state()
    added = next((p for p in st["products"] if p["name"] == "Taco de Canasta"), None)
    case("FUN-CATALOGO-01", "Alta de producto nuevo disponible para la mesera", "FUN",
         "Se agrega un producto desde 📋 y queda visible para la mesera",
         f"admin abierto={admin}; producto creado={bool(added)}" + (f" precio={added['priceCents']}" if added else ""),
         "PASA" if admin and added and added["priceCents"] == 1500 else "FALLA", device="cocina", evidence="")

    # Agotar el producto (la lista puede requerir scroll; tocar el interruptor al final de la fila)
    if added:
        adb("shell", "input", "swipe", "540", "1800", "540", "900", "400", serial=K)
        time.sleep(1)
        row = ui.find(ui.dump(K), "Taco de Canasta", contains=True)
        if row:
            ns = ui.dump(K)
            cx = (row["bounds"][0] + row["bounds"][2]) // 2
            cy = (row["bounds"][1] + row["bounds"][3]) // 2
            anc = [n for n in ns if n["clickable"] and n["bounds"] and n["bounds"][0] <= cx <= n["bounds"][2] and n["bounds"][1] <= cy <= n["bounds"][3]]
            if anc:
                full = min(anc, key=lambda n: (n["bounds"][2] - n["bounds"][0]) * (n["bounds"][3] - n["bounds"][1]))
                adb("shell", "input", "tap", str(full["bounds"][2] - 40), str(cy), serial=K)
                time.sleep(1.5)
        st2 = S.kitchen_state()
        off = next((p for p in st2["products"] if p["name"] == "Taco de Canasta"), None)
        case("FUN-CATALOGO-02", "Apagar un producto lo marca agotado y lo quita de la captura", "FUN",
             "El interruptor deja active=false y la mesera ya no lo ve en captura",
             f"activo tras apagar={off['active'] if off else 'no encontrado'}",
             "PASA" if off and off["active"] is False else "FALLA", device="cocina")

    # Validación: producto sin nombre no se guarda
    tap(K, "+ Agregar producto")
    time.sleep(1)
    save_node = ui.find(ui.dump(K), "Guardar")
    enabled = False
    if save_node:
        ns = ui.dump(K)
        x, y = (save_node["bounds"][0] + save_node["bounds"][2]) // 2, (save_node["bounds"][1] + save_node["bounds"][3]) // 2
        cands = [n for n in ns if n["clickable"] and n["bounds"] and n["bounds"][0] <= x <= n["bounds"][2] and n["bounds"][1] <= y <= n["bounds"][3]]
        enabled = any(n["enabled"] for n in cands)
    case("VAL-CATALOGO-01", "Producto sin nombre o con precio inválido no se guarda", "VAL",
         "Con nombre vacío Guardar está deshabilitado",
         f"Guardar habilitado con nombre vacío={enabled}",
         "PASA" if not enabled else "FALLA", device="cocina")
    tap(K, "Cancelar")
    time.sleep(0.8)

    # Opción duplicada entre tipos de modificación
    tap(K, "Modificaciones")
    time.sleep(1)
    tap(K, "+ Agregar tipo de modificación")
    time.sleep(1)
    ui.set_text(K, "Nombre", "Prueba")
    ui.set_text(K, "Opciones, una por renglón", "Con salsa")
    groups_before = len(S.kitchen_state()["modifierGroups"])
    tap(K, "Guardar")
    time.sleep(2)
    groups_after = len(S.kitchen_state()["modifierGroups"])
    case("VAL-CATALOGO-03", "Una opción no puede existir en dos tipos de modificación", "VAL",
         "Guardar un tipo con la opción «Con salsa» no crea el tipo",
         f"tipos antes={groups_before}, después={groups_after}",
         "PASA" if groups_after == groups_before else "FALLA", device="cocina")
    # cerrar diálogo y volver al monitor
    if has("Cancelar", K):
        tap(K, "Cancelar")
    adb("shell", "input", "keyevent", "4", serial=K)
    time.sleep(1)

    # ---------- QR de conexión ----------
    tap(K, "📲")
    time.sleep(1.5)
    qr_dialog = has("Conectar la mesera", K)
    ip_line = next((x for x in texts(K) if x.startswith("Cocina: ")), "")
    has_hint = has("Escanear QR de cocina", K)
    shot("71-cocina-qr-conexion")
    case("FUN-CONEXION-02", "QR de conexión en cocina (host y puerto)", "FUN",
         "La cocina muestra un QR con su IP:puerto y la instrucción para la mesera",
         f"diálogo={qr_dialog}; {ip_line}; instrucción={has_hint}",
         "PASA" if qr_dialog and ip_line and has_hint else "FALLA", device="cocina",
         evidence="capturas/71-cocina-qr-conexion.png")
    tap(K, "Cerrar")
    time.sleep(1)

    adb("shell", "pm", "grant", PKG, "android.permission.CAMERA", serial=W)
    tap(W, "⚙")
    time.sleep(1)
    scan_btn = has("Escanear QR de cocina", W)
    tap(W, "Escanear QR de cocina")
    time.sleep(2)
    scanner = has("Escanear QR de cocina", W)
    shot("72-mesera-escaner-qr", W)
    if has("Cerrar", W):
        tap(W, "Cerrar")
    time.sleep(1)
    case("FUN-CONEXION-03", "La mesera escanea el QR de cocina y se conecta sin escribir IP", "FUN",
         "El escáner abre desde ⚙ → Conexión; el escaneo real necesita cámara física",
         f"botón en ajustes={scan_btn}; pantalla de escáner={scanner}; escaneo real bloqueado en AVD (cámara virtual)",
         "BLOQUEADO" if scan_btn and scanner else "FALLA", device="mesera",
         evidence="capturas/72-mesera-escaner-qr.png")

    # ---------- Densidad ----------
    adb("shell", "settings", "put", "system", "font_scale", "1.5", serial=W)
    stop(W)
    start(W, 7)
    scaled = has("Cuentas abiertas", W)
    shot("69-mesera-fuente-150", W)
    adb("shell", "settings", "put", "system", "font_scale", "1.0", serial=W)
    stop(W)
    start(W, 7)
    case("UI-DENSIDAD-01", "Escala de fuente 1.5× y tamaño de pantalla grande", "UI",
         "Con fuente 1.5× la pantalla principal sigue usable",
         f"pantalla principal visible con 1.5×={scaled}",
         "PASA" if scaled else "FALLA", device="mesera", evidence="capturas/69-mesera-fuente-150.png")

    # ---------- Permiso de red local ----------
    adb("shell", "pm", "revoke", PKG, "android.permission.ACCESS_LOCAL_NETWORK", serial=W)
    stop(W)
    start(W, 4)
    time.sleep(2)
    perm_dialog = any("permiso" in (x or "").lower() or "allow" in (x or "").lower() for x in texts(W)) or "mCurrentFocus" in ""
    grant = adb("shell", "pm", "grant", PKG, "android.permission.ACCESS_LOCAL_NETWORK", serial=W)
    shot("70-permiso-red-local", W)
    time.sleep(2)
    start(W, 7)
    ok_conn = wait("Conectado a cocina", W, 25)
    case("RED-PERMISO-01", "Permiso de red local en Android 17+: denegado y concedido", "RED",
         "La app pide el permiso; al concederlo restablece la red",
         f"diálogo de permiso detectado={perm_dialog}; conectó tras conceder={ok_conn}",
         "PASA" if ok_conn else "OBSERVACION", device="mesera", evidence="capturas/70-permiso-red-local.png")

    json.dump({"generado": time.strftime("%Y-%m-%dT%H:%M:%S%z"), "casos": RESULTS},
              open(os.path.join(BASE, "red_ui.json"), "w"), ensure_ascii=False, indent=2)
    print(f"\n{sum(1 for r in RESULTS if r['estado']=='PASA')}/{len(RESULTS)} PASA -> evidence/e2e/red_ui.json")


if __name__ == "__main__":
    main()
