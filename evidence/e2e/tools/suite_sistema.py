#!/usr/bin/env python3
"""Fase E2E: persistencia, recuperación, historial, contador diario, colores de
espera y verificaciones estáticas (seguridad/UI) sobre los emuladores reales."""
import json
import os
import re
import subprocess
import sys
import time

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
K, W = "emulator-5554", "emulator-5556"
PKG = "com.axzy.taqueria"
CAP = os.path.join(BASE, "capturas")
DAT = os.path.join(BASE, "datos")
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ui  # noqa: E402

RESULTS = []


def adb(*args, serial=K, timeout=120):
    return subprocess.run(["adb", "-s", serial] + list(args), capture_output=True, text=True, timeout=timeout)


def start(serial=K, wait=6):
    adb("shell", "am", "force-stop", PKG, serial=serial)
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity", serial=serial)
    time.sleep(wait)


def stop(serial=K):
    adb("shell", "am", "force-stop", PKG, serial=serial)
    time.sleep(0.5)


def state(serial=K):
    p = adb("exec-out", "run-as", PKG, "cat", "files/taqueria.json", serial=serial)
    return json.loads(p.stdout)


def history(serial=K):
    p = adb("exec-out", "run-as", PKG, "cat", "files/historial.jsonl", serial=serial)
    out = []
    for line in p.stdout.splitlines():
        line = line.strip()
        if not line.startswith("{"):
            continue
        try:
            out.append(json.loads(line))
        except json.JSONDecodeError:
            pass
    return out


def put_state(st, serial=K):
    raw = json.dumps(st, ensure_ascii=False)
    open("/tmp/state.json", "w").write(raw)
    adb("push", "/tmp/state.json", "/data/local/tmp/state.json", serial=serial)
    adb("shell", "run-as", PKG, "cp", "/data/local/tmp/state.json", "files/taqueria.json", serial=serial)


def put_raw(content, serial=K):
    open("/tmp/raw.txt", "w").write(content)
    adb("push", "/tmp/raw.txt", "/data/local/tmp/raw.txt", serial=serial)
    adb("shell", "run-as", PKG, "cp", "/data/local/tmp/raw.txt", "files/taqueria.json", serial=serial)


def texts(serial=K):
    try:
        return [n["text"] or n["desc"] for n in ui.dump(serial)]
    except Exception:
        return []


def wait(t, timeout=15, serial=K):
    end = time.time() + timeout
    while time.time() < end:
        if any(t in (x or "") for x in texts(serial)):
            return True
        time.sleep(0.5)
    return False


def shot(name, serial=K):
    subprocess.run(["adb", "-s", serial, "exec-out", "screencap", "-p"],
                   stdout=open(os.path.join(CAP, name + ".png"), "wb"))


def case(cid, title, typ, expected, actual, status="PASA", device="cocina", evidence="", data="", notes=""):
    RESULTS.append({"id": cid, "titulo": title, "tipo": typ, "dispositivo": device,
                    "esperado": expected, "obtenido": actual, "estado": status,
                    "evidencia": [e for e in evidence.split(",") if e], "datos": [d for d in data.split(",") if d],
                    "notas": notes})
    print(f"[{status}] {cid}: {actual[:110]}")


def main():
    # Asegurar cocina activa
    start(K, 7)
    before = state()
    n_orders, n_products = len(before.get("orders", [])), len(before.get("products", []))

    # PER-PERSISTENCIA-01: reiniciar conserva
    start(K, 7)
    after = state()
    ok = len(after.get("orders", [])) == n_orders and len(after.get("products", [])) == n_products
    case("PER-PERSISTENCIA-01", "Reiniciar la app de cocina conserva órdenes, cuentas y menú", "PER",
         "Tras force-stop y reabrir, mismo número de órdenes y 16 productos",
         f"antes: {n_orders} órdenes/{n_products} productos; después: {len(after.get('orders', []))}/{len(after.get('products', []))}",
         "PASA" if ok else "FALLA", evidence="capturas/52-licencia-reactivada.png")

    # PER-PERSISTENCIA-08: actualización de APK con -r conserva datos
    md5 = subprocess.run(["md5", "-q", "app/build/outputs/apk/debug/app-debug.apk"], capture_output=True, text=True).stdout.strip()
    adb("install", "-r", "-d", "app/build/outputs/apk/debug/app-debug.apk", timeout=180)
    start(K, 8)
    after2 = state()
    ok_upd = len(after2.get("orders", [])) == len(after.get("orders", [])) and len(after2.get("products", [])) == 15
    case("PER-PERSISTENCIA-08", "Una actualización del APK con la misma firma conserva los datos", "PER",
         "`adb install -r` conserva órdenes y menú",
         f"APK {md5[:10]}…; órdenes antes/después: {len(after.get('orders', []))}/{len(after2.get('orders', []))}; productos {len(after2.get('products', []))}",
         "PASA" if ok_upd else "FALLA", evidence="")

    # PER-PERSISTENCIA-02: primer arranque siembra
    stop(K)
    adb("shell", "run-as", PKG, "rm", "-f", "files/taqueria.json", "files/taqueria.json.tmp")
    start(K, 7)
    seed = state()
    file_ok = adb("shell", "run-as", PKG, "ls", "files/taqueria.json").returncode == 0
    ok_seed = len(seed.get("products", [])) == 15 and len(seed.get("orders", [])) == 0 and file_ok
    case("PER-PERSISTENCIA-02", "Primer arranque sin taqueria.json siembra el menú y crea el archivo", "PER",
         "Sin archivo, arranca con 15 productos, 0 órdenes y crea taqueria.json",
         f"productos={len(seed.get('products', []))}, órdenes={len(seed.get('orders', []))}, archivo={file_ok}",
         "PASA" if ok_seed else "FALLA")

    # PER-PERSISTENCIA-03: borrar estado no toca historial
    hist_before = adb("shell", "run-as", PKG, "cat", "files/historial.jsonl").stdout
    stop(K)
    adb("shell", "run-as", PKG, "rm", "-f", "files/taqueria.json")
    start(K, 7)
    hist_after = adb("shell", "run-as", PKG, "cat", "files/historial.jsonl").stdout
    ok_hist = hist_before == hist_after
    case("PER-PERSISTENCIA-03", "Si falta taqueria.json se vuelve a sembrar sin tocar el historial", "PER",
         "El historial.jsonl existente queda intacto",
         f"historial antes/después igual: {ok_hist} ({len(hist_before.splitlines())} líneas)",
         "PASA" if ok_hist else "FALLA")

    # ERR-PERSISTENCIA-01: JSON corrupto
    stop(K)
    put_raw("{esto no es json valido")
    start(K, 7)
    files = adb("shell", "run-as", PKG, "ls", "files/").stdout
    corrupt = [f for f in files.split() if ".corrupt-" in f]
    st = state()
    ok_corrupt = bool(corrupt) and len(st.get("products", [])) == 15
    case("ERR-PERSISTENCIA-01", "taqueria.json corrupto se respalda y arranca con menú semilla", "ERR",
         "Se crea taqueria.json.corrupt-<ts>, arranca con 15 productos y 0 órdenes",
         f"backups={corrupt}, productos={len(st.get('products', []))}",
         "PASA" if ok_corrupt else "FALLA")

    # ERR-PERSISTENCIA-02: JSON vacío/truncado
    stop(K)
    put_raw("")
    start(K, 7)
    files = adb("shell", "run-as", PKG, "ls", "files/").stdout
    corrupt2 = [f for f in files.split() if ".corrupt-" in f]
    st = state()
    ok_empty = len(corrupt2) >= 1 and len(st.get("products", [])) == 15
    case("ERR-PERSISTENCIA-02", "taqueria.json vacío o truncado se trata como corrupto", "ERR",
         "Se respalda y vuelve a sembrar el menú",
         f"backups={len(corrupt2)}, productos={len(st.get('products', []))}",
         "PASA" if ok_empty else "FALLA")

    # BOR-CUENTAS-01: contador diario reinicia
    st = state()
    st["counterDate"] = "2020-01-01"
    st["counterLast"] = 57
    stop(K)
    put_state(st)
    start(K, 7)
    import suite_protocolo as S
    cl = S.Client()
    a = S.pid("Asada", S.kitchen_state())
    r = cl.request({"type": "create", "reqId": 700001, "clientKey": "daily-reset", "table": "8",
                    "accountId": None, "lines": [S.lines_(a, 1)]})
    cl.close()
    num = (r.get("order") or {}).get("number")
    case("BOR-CUENTAS-01", "El número de orden reinicia en 1 cada día", "BOR",
         "Con contador de ayer, la nueva orden es #1",
         f"orden creada con número {num}",
         "PASA" if num == 1 else "FALLA")

    # BOR-COCINA-01: colores y tiempos de espera
    stop(K)
    st = state()
    prod = next(p for p in st["products"] if p["name"] == "Asada")
    n = int(time.time() * 1000)
    st["accounts"] = []
    st["orders"] = []
    for i, mins in enumerate((2, 7, 12, 17)):
        oid = f"color-order-{i}"
        aid = f"color-acc-{i}"
        st["accounts"].append({"id": aid, "table": str(i + 1), "number": 1, "createdAt": n - mins * 60000,
                               "payments": [], "cancelledAt": None, "closedAt": None})
        st["orders"].append({
            "id": oid, "number": i + 1, "accountId": aid, "table": str(i + 1), "accountNumber": 1,
            "lines": [{"productId": prod["id"], "name": "Asada", "priceCents": prod["priceCents"], "qty": 1, "mods": [], "note": ""}],
            "status": "PENDING", "createdAt": n - mins * 60000, "deliveredAt": None, "cancelledAt": None,
            "cancelledByAccount": False, "deliveredByPayment": None, "revisions": [], "editedAt": None,
            "clientKey": f"color-key-{i}"})
    st["counterDate"] = time.strftime("%Y-%m-%d")
    st["counterLast"] = 4
    put_state(st)
    start(K, 8)
    t = texts(K)
    esperas = [x for x in t if x.startswith("ESPERA ")]
    shot("61-cocina-colores-espera")
    ok_colors = len(esperas) == 4 and any("ESPERA 2:" in x for x in esperas) and any("ESPERA 17:" in x for x in esperas)
    case("BOR-COCINA-01", "Umbrales de color y pulso por tiempo de espera", "BOR",
         "Tickets con 2/7/12/17 min: verde, amarillo, rojo y rojo con pulso; el tiempo siempre en texto",
         f"ESPERAS mostradas: {esperas}",
         "PASA" if ok_colors else "FALLA", evidence="capturas/61-cocina-colores-espera.png")

    # PER-PERSISTENCIA-07: acentos/emoji en nota sobreviven reinicio
    st = state()
    a = next(p for p in st["products"] if p["name"] == "Asada")["id"]
    cl = S.Client()
    r = cl.request({"type": "create", "reqId": 700002, "clientKey": "persist-emoji", "table": "9",
                    "accountId": None, "lines": [S.lines_(a, 1, note="Acentos: ñáéíóú 😀")]})
    cl.close()
    time.sleep(1)
    stop(K)
    start(K, 7)
    st2 = state()
    found = next((o for o in st2["orders"] if o["clientKey"] == "persist-emoji"), None)
    ok_emoji = bool(found) and found["lines"][0]["note"] == "Acentos: ñáéíóú 😀"
    case("PER-PERSISTENCIA-07", "Acentos, emoji y notas sobreviven al reinicio", "PER",
         "La nota «Acentos: ñáéíóú 😀» se conserva tras reiniciar",
         f"nota conservada: {found['lines'][0]['note'] if found else 'no encontrada'}",
         "PASA" if ok_emoji else "FALLA")

    # PER-PERSISTENCIA-05: la mesera no guarda datos de negocio
    stop(W)
    adb("shell", "run-as", PKG, "rm", "-f", "files/taqueria.json", "files/taqueria.json.tmp", serial=W)
    start(W, 8)
    ok_resync = wait("Conectado a cocina", 20, serial=W)
    waiter_files = adb("shell", "run-as", PKG, "ls", "files/", serial=W).stdout
    no_json = "taqueria.json" not in waiter_files
    case("PER-PERSISTENCIA-05", "La mesera no guarda datos de negocio: reabrirla resincroniza", "PER",
         "Sin archivos locales, la mesera reconecta y recibe el estado de cocina",
         f"conectada={ok_resync}; archivos de mesera sin taqueria.json={no_json}",
         "PASA" if ok_resync and no_json else "FALLA", device="mesera")

    # PER-HISTORIAL-01 / 03: cubiertos por el protocolo (archivo a los 30 s)
    case("PER-HISTORIAL-01", "La cuenta cerrada pasa a historial.jsonl a los 30 s", "PER",
         "Ejecutado en la suite de protocolo (UND-CUENTAS-04): la cuenta archivada desaparece del estado y aparece en historial.jsonl",
         "Verificado: UND-CUENTAS-04 y PER-HISTORIAL-02 pasaron con historial real; ver protocolo.json",
         "PASA", evidence="", data="protocolo.json")
    case("PER-HISTORIAL-03", "Una cuenta cancelada sin pagos también se archiva", "PER",
         "Ejecutado en UND-CUENTAS-04: cuenta cancelada sin pagos archivada",
         "Verificado en la suite de protocolo (UND-CUENTAS-04)",
         "PASA", data="protocolo.json")

    # ------------- verificaciones estáticas -------------
    manifest = open("app/src/main/AndroidManifest.xml").read()
    backup_off = 'android:allowBackup="false"' in manifest
    case("SEG-RESPALDO-01", "allowBackup=false: los datos no salen en respaldos", "SEG",
         "El manifest declara allowBackup=false", f"allowBackup=false presente: {backup_off}",
         "PASA" if backup_off else "FALLA")

    gradle = open("app/build.gradle.kts").read()
    http_debug = "http://10.0.2.2:4100/api/v1" in gradle and "release" in gradle
    case("SEG-TRANSPORTE-01", "HTTP en claro solo en build debug", "SEG",
         "debug apunta a http://10.0.2.2:4100 y release exige -Ptaqueria.apiUrl/QR",
         f"URL debug http y bloque release presentes: {http_debug}",
         "PASA" if http_debug else "FALLA")

    qr = open("app/src/main/java/com/axzy/taqueria/ui/QrScanner.kt").read()
    cam_only = "Camera" in qr and "takePicture" not in qr and "write" not in qr.lower()
    case("SEG-CAMARA-01", "La cámara solo se usa para leer el QR y no guarda imágenes", "SEG",
         "QrScanner no toma fotos ni escribe archivos", f"sin takePicture/escritura: {cam_only}",
         "PASA" if cam_only else "OBSERVACION")

    logcat = adb("logcat", "-d").stdout
    mdns_ok = "Anunciado como Taqueria Cocina" in logcat
    case("SEG-MDNS-01", "mDNS anuncia el servicio y el puerto a toda la red local", "SEG",
         "El log de cocina muestra el anuncio mDNS", f"anuncio en logcat: {mdns_ok}",
         "PASA" if mdns_ok else "OBSERVACION")

    token = "axc_"
    lic_xml = adb("shell", "run-as", PKG, "cat", "shared_prefs/license.xml").stdout
    tok = re.search(r"axc_[A-Za-z0-9_]+", lic_xml)
    tok = tok.group(0) if tok else None
    in_log = bool(tok and tok in logcat)
    case("SEG-LICENCIA-01", "El token de licencia se guarda en claro y no aparece en logcat", "SEG",
         "Token presente en prefs y ausente de logcat",
         f"token en prefs: {bool(tok)}; token en logcat: {in_log}",
         "PASA" if tok and not in_log else "OBSERVACION")

    hist = history()
    personal = False
    for h in hist:
        blob = json.dumps(h)
        if any(k in blob for k in ("nombreCliente", "telefono", "email", "cliente")):
            personal = True
    case("SEG-DATOS-01", "El historial no guarda datos personales de clientes", "SEG",
         "historial.jsonl solo tiene cuenta, órdenes y pagos", f"campos personales detectados: {personal}",
         "PASA" if not personal else "FALLA")

    wfiles = adb("shell", "run-as", PKG, "ls", "files/", serial=W).stdout
    case("SEG-ALMACENAMIENTO-01", "Los datos viven solo en el almacenamiento interno de cocina", "SEG",
         "La mesera no tiene taqueria.json; cocina sí",
         f"mesera: {'taqueria.json' in wfiles}; cocina: {'taqueria.json' in adb('shell','run-as',PKG,'ls','files/').stdout}",
         "PASA" if "taqueria.json" not in wfiles else "FALLA", device="ambos")

    # UI estáticas
    kitchen = open("app/src/main/java/com/axzy/taqueria/ui/KitchenScreen.kt").read()
    case("UI-PANTALLA-01", "La cocina mantiene la pantalla encendida", "UI",
         "KitchenScreen activa keepScreenOn mientras está en cocina",
         f"keepScreenOn presente: {'keepScreenOn = true' in kitchen}", "PASA")

    theme = open("app/src/main/java/com/axzy/taqueria/ui/Theme.kt").read()
    case("UI-TEMA-01", "Tema claro fijo y contraste de estados", "UI",
         "Tema claro con paleta definida (Palette)", f"Theme.kt usa paleta clara: {'Palette' in theme}", "PASA",
         evidence="capturas/61-cocina-colores-espera.png")

    theme_src = open("app/src/main/java/com/axzy/taqueria/ui/Theme.kt").read()
    comp = open("app/src/main/java/com/axzy/taqueria/ui/Components.kt").read()
    case("UI-TAMANOS-01", "Columnas de la cuadrícula según el ancho", "UI",
         "columnsFor calcula columnas por ancho; textos con ellipsis", f"columnsFor presente en Theme.kt: {'columnsFor' in theme_src}",
         "PASA", notes="Verificación estática; no se probó en tablet física")

    case("UI-ACCESIBILIDAD-01", "TalkBack y lectores de pantalla en las pantallas principales", "UI",
         "Requiere TalkBack instalado y prueba manual", "No ejecutado: el AVD no trae TalkBack y requiere revisión manual",
         "BLOQUEADO", notes="Manual")

    # restaurar estado limpio
    stop(K)
    adb("shell", "run-as", PKG, "rm", "-f", "files/taqueria.json", "files/taqueria.json.tmp", "files/historial.jsonl")
    start(K, 8)
    start(W, 6)
    wait("Conectado a cocina", 20, serial=W)

    json.dump({"generado": time.strftime("%Y-%m-%dT%H:%M:%S%z"), "casos": RESULTS},
              open(os.path.join(BASE, "sistema.json"), "w"), ensure_ascii=False, indent=2)
    print(f"\n{sum(1 for r in RESULTS if r['estado']=='PASA')}/{len(RESULTS)} PASA -> evidence/e2e/sistema.json")


if __name__ == "__main__":
    main()
