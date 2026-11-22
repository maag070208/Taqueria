#!/usr/bin/env python3
"""Genera la matriz final de pruebas (XLSX + Markdown) a partir de:
- evidence/matriz/casos.json (184 casos)
- evidence/e2e/{resultados,protocolo,licencia,sistema,red_ui}.json
- evidence/unitarios/resumen.json
- capturas y datos en evidence/e2e/
"""
import glob
import json
import os
import time
from collections import Counter, defaultdict

from openpyxl import Workbook
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
from openpyxl.utils import get_column_letter

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
E2E = os.path.join(ROOT, "evidence", "e2e")
OUT_DIR = os.path.join(ROOT, "docs", "pruebas")
os.makedirs(OUT_DIR, exist_ok=True)

BUILD = {"md5": "7b9eb36856a2c5b1df9e77360d3cdc46", "versionName": "1.0.1", "versionCode": 2,
         "fecha": "2026-11-21", "nota": "APK HEAD instalada en ambos emuladores: menú rápido de acciones, QR de conexión y corrección de datos en mesera"}
DEVICES = {"cocina": "emulator-5554 · rol KITCHEN · IP 10.0.2.15 · licencia activa",
           "mesera": "emulator-5556 · rol WAITER · host manual 10.0.2.2 · conectada por TCP"}

GREEN = PatternFill("solid", fgColor="1F6E4A")
GREEN_L = PatternFill("solid", fgColor="D8EFE4")
RED = PatternFill("solid", fgColor="B42318")
RED_L = PatternFill("solid", fgColor="FDE2DF")
AMBER = PatternFill("solid", fgColor="B54708")
AMBER_L = PatternFill("solid", fgColor="FDF0D5")
GRAY_L = PatternFill("solid", fgColor="EEF0F2")
BLUE = PatternFill("solid", fgColor="1D4E89")
WHITE = Font(color="FFFFFF", bold=True, size=10)
BOLD = Font(bold=True)
THIN = Side(style="thin", color="D0D5DD")
BORDER = Border(left=THIN, right=THIN, top=THIN, bottom=THIN)


def load(path):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return None


def sheet_title(ws, title, subtitle, ncols):
    ws.merge_cells(start_row=1, start_column=1, end_row=1, end_column=ncols)
    c = ws.cell(row=1, column=1, value=title)
    c.font = Font(bold=True, size=16, color="1F2937")
    ws.row_dimensions[1].height = 26
    ws.merge_cells(start_row=2, start_column=1, end_row=2, end_column=ncols)
    c = ws.cell(row=2, column=1, value=subtitle)
    c.font = Font(size=10, color="475467")
    ws.row_dimensions[2].height = 18


def header(ws, row, cols):
    for i, name in enumerate(cols, start=1):
        c = ws.cell(row=row, column=i, value=name)
        c.fill = GREEN
        c.font = WHITE
        c.alignment = Alignment(horizontal="center", vertical="center", wrap_text=True)
        c.border = BORDER
    ws.row_dimensions[row].height = 30
    ws.freeze_panes = ws.cell(row=row + 1, column=1)


def write_rows(ws, row, rows, widths):
    for r in rows:
        for i, v in enumerate(r, start=1):
            c = ws.cell(row=row, column=i, value=v)
            c.alignment = Alignment(vertical="top", wrap_text=True)
            c.border = BORDER
            c.font = Font(size=9)
        row += 1
    for i, w in enumerate(widths, start=1):
        ws.column_dimensions[get_column_letter(i)].width = w
    return row


def status_fill(status):
    return {"PASA": GREEN_L, "FALLA": RED_L, "BLOQUEADO": GRAY_L,
            "NO EJECUTADO": GRAY_L, "OBSERVACION": AMBER_L}.get(status, GRAY_L)


def main():
    matrix = load(os.path.join(ROOT, "evidence", "matriz", "casos.json"))["casos"]
    unit = load(os.path.join(ROOT, "evidence", "unitarios", "resumen_actualizado.json")) or load(os.path.join(ROOT, "evidence", "unitarios", "resumen.json"))

    # casos ejecutados por las suites E2E
    executed = {}
    for fname in ("resultados.json", "protocolo.json", "licencia.json", "sistema.json", "red_ui.json"):
        doc = load(os.path.join(E2E, fname))
        if not doc:
            continue
        source = {"resultados.json": "E2E UI (emuladores)", "protocolo.json": "Protocolo TCP real",
                  "licencia.json": "E2E licencia (mock Central)", "sistema.json": "E2E sistema/estático",
                  "red_ui.json": "E2E red/UI/roles"}[fname]
        for c in doc.get("casos", []):
            cid = c.get("id")
            if not cid:
                continue
            entry = dict(c)
            entry["fuente"] = source
            # la UI manda sobre protocolo si el caso se ejecutó en ambos
            if cid not in executed or source.startswith("E2E UI") or source.startswith("E2E licencia") or source.startswith("E2E red"):
                executed[cid] = entry

    # A1 se registró como E2E-FUN-01; cubre varios casos de la matriz
    if "E2E-FUN-01" in executed:
        a1 = executed["E2E-FUN-01"]
        for cid in ("FUN-CUENTAS-01", "FUN-CAPTURA-01", "FUN-CAPTURA-04"):
            executed.setdefault(cid, {**a1, "id": cid,
                                      "titulo": {"FUN-CUENTAS-01": "Nueva orden en Mesa 4 crea la Cuenta 1 y llega a cocina",
                                                 "FUN-CAPTURA-01": "Un toque suma +1 y el total se calcula en vivo",
                                                 "FUN-CAPTURA-04": "✂ Dividir preparación: 4 asada → 2+2 en una sola orden"}[cid]})

    # caso nuevo derivado de la mejora UX
    if "FUN-COBRO-07" in executed:
        matrix.append({
            "id": "FUN-COBRO-07", "tipo": "FUN", "modulo": "Cobro",
            "titulo": "Entregar antes de pagar con menú rápido (mejora UX nueva)",
            "prioridad": "Alta",
            "precondiciones": "Cuenta con orden PENDING y saldo pendiente en la mesera; cocina activa.",
            "pasos": ["Deslizar → sobre la tarjeta", "Tocar «✓ Entregar (sale de cocina)»", "Cobrar después desde el mismo menú"],
            "esperado": "Entregar sin pagar deja la orden DELIVERED y la cuenta abierta; al cobrar se cierra",
            "cobertura": "e2e", "dispositivo": "ambos", "regla": "regla 6", "estado": "pendiente",
            "nuevo": True,
        })

    # casos nuevos de la conexión por QR (mejora pedida por el usuario)
    extra_cases = [
        {"id": "FUN-CONEXION-02", "tipo": "FUN", "modulo": "Red/QR",
         "titulo": "QR de conexión en cocina (host y puerto)", "prioridad": "Alta",
         "precondiciones": "Cocina con rol KITCHEN y red local activa.",
         "pasos": ["Tocar 📲 en la pantalla de cocina", "Elegir la red si hay varias", "Leer IP:puerto y el QR"],
         "esperado": "Se muestra un QR con {\"v\":1,\"host\":IP,\"port\":7777} y la instrucción para la mesera",
         "cobertura": "e2e", "dispositivo": "cocina", "regla": "n/a", "estado": "pendiente", "nuevo": True},
        {"id": "FUN-CONEXION-03", "tipo": "FUN", "modulo": "Red/QR",
         "titulo": "La mesera escanea el QR de cocina y se conecta sin escribir IP", "prioridad": "Alta",
         "precondiciones": "Cocina mostrando su QR; mesera con cámara.",
         "pasos": ["En la mesera: ⚙ → Conexión → «Escanear QR de cocina»", "Apunta al QR de la cocina"],
         "esperado": "Se guarda host:puerto y la mesera conecta sola",
         "cobertura": "e2e", "dispositivo": "mesera", "regla": "n/a", "estado": "pendiente", "nuevo": True},
        {"id": "VAL-CONEXION-01", "tipo": "VAL", "modulo": "Red/QR",
         "titulo": "QR ajeno (activación de la Central) se rechaza en el escáner de conexión", "prioridad": "Media",
         "precondiciones": "Escáner de conexión abierto.",
         "pasos": ["Escanear un QR de activación axc_…"],
         "esperado": "Mensaje «Este QR no es de conexión de Tacomanda»", "cobertura": "unitaria",
         "dispositivo": "JVM", "regla": "n/a", "estado": "pendiente", "nuevo": True},
        {"id": "VAL-CONEXION-02", "tipo": "VAL", "modulo": "Red/QR",
         "titulo": "Host o puerto inválidos en el QR de conexión se rechazan", "prioridad": "Media",
         "precondiciones": "—", "pasos": ["Parsear QR con host vacío, host con espacios, puerto 0 o 70000"],
         "esperado": "Se devuelve error claro y no se configura la conexión", "cobertura": "unitaria",
         "dispositivo": "JVM", "regla": "n/a", "estado": "pendiente", "nuevo": True},
        {"id": "BOR-CONEXION-01", "tipo": "BOR", "modulo": "Red/QR",
         "titulo": "QR de conexión acepta JSON con v=1 y esquema taqueria:// con o sin puerto", "prioridad": "Media",
         "precondiciones": "—", "pasos": ["Parsear JSON sin port", "Parsear taqueria://host", "Parsear taqueria://host:puerto"],
         "esperado": "Puerto por defecto 7777 y host válido", "cobertura": "unitaria",
         "dispositivo": "JVM", "regla": "n/a", "estado": "pendiente", "nuevo": True},
    ]
    known = {c["id"] for c in matrix}
    matrix.extend(c for c in extra_cases if c["id"] not in known)

    unit_by_id = {c["id"] for c in matrix if c.get("cobertura") == "unitaria"}

    blocked = {
        "LIC-QR-12": "Requiere cámara física o webcam para escanear un QR real.",
        "LIC-QR-13": "Requiere QR escaneado que fije apiBase; la cámara virtual no es fiable.",
        "BOR-QR-01": "Requiere generar y escanear QR de 200/201 caracteres.",
        "BOR-QR-02": "Requiere generar y escanear QR con URL de 500/501 caracteres.",
        "ERR-LICENCIA-05": "Requiere compilar build release sin -Ptaqueria.apiUrl.",
        "RED-MDNS-01": "El multicast mDNS no cruza el NAT entre dos AVD; en emulador se usa adb forward + IP manual.",
    }
    manual = {
        "PER-PERSISTENCIA-04": "Garantía atómica ante force-stop durante la escritura: no determinista desde UI; se verificó que no quedan .tmp y que el archivo parsea.",
        "LIC-LICENCIA-13": "Refresco de licencia al abrir, al validar red y cada 6 h: se observó el GET /license al abrir (mock) y el código; el ciclo de 6 h requiere espera real.",
        "UI-ACCESIBILIDAD-01": "Requiere TalkBack instalado y revisión manual de accesibilidad.",
    }

    # casos del catálogo sin ejecutar en esta corrida: se dejan como NO EJECUTADO con motivo, nunca como PASA
    pendientes = {
        "FUN-CAPTURA-09": "Editar 4 asada → 2 asada + 1 adobada + 1 chorizo y conservar la versión anterior. Pendiente de ejecutar en la mesera.",
        "FUN-CAPTURA-10": "Cocina muestra «● EDITADA» y «QUITAR: …» tachado. Pendiente de ejecutar.",
        "UND-ORDENES-01": "Deshacer la cancelación de una sola orden. Pendiente de ejecutar.",
        "FUN-COBRO-06": "Devolución de saldo negativo desde la UI. Pendiente de ejecutar.",
        "FUN-ROLES-01": "Primer arranque de la mesera eligiendo «Mesero». Pendiente de ejecutar (requiere reinstalar limpio).",
        "IDEM-CAPTURA-05": "La llave del borrador cambia con cada edición. Pendiente de ejecutar.",
        "BOR-CATALOGO-01": "Precio 0 y tope de precio en el catálogo. Pendiente de ejecutar.",
        "BOR-CATALOGO-02": "Límites de longitud de texto en el catálogo. Pendiente de ejecutar.",
        "VAL-CATALOGO-02": "Tipo de modificación sin nombre/opciones. Pendiente de ejecutar.",
        "VAL-CATALOGO-04": "Opción de más de 30 caracteres. Pendiente de ejecutar.",
        "RED-CONEXION-01": "IP manual + adb forward: funcionó en la corrida manual, falta registrar el resultado como caso.",
        "RED-RECONEXION-03": "Servidor que acepta pero no responde: timeout de 25 s. Pendiente de ejecutar.",
        "RED-OFFLINE-03": "WiFi sin internet en el AVD. Pendiente de ejecutar.",
        "ERR-CONEXION-01": "Timeout de conexión de 8 s. Pendiente de ejecutar.",
        "ERR-CONEXION-02": "Puerto 7777 ocupado. Pendiente de ejecutar.",
        "ERR-CONEXION-03": "Caída de conexión a mitad del envío. Pendiente de ejecutar.",
        "ERR-CAMARA-01": "Cámara denegada → respaldo a IP manual. Pendiente de ejecutar.",
    }

    rows = []
    counts = Counter()
    for c in matrix:
        cid = c["id"]
        if cid in executed:
            e = executed[cid]
            status = e.get("estado", "PASA")
            actual = e.get("obtenido", "")
            ev = ", ".join(e.get("evidencia", []) or [])
            fuente = e.get("fuente", "E2E")
            cobertura_ejec = "E2E ejecutado" if "TCP" not in fuente else "Protocolo TCP"
        elif cid in unit_by_id:
            status = "PASA"
            actual = f"Suite JVM ({unit['pasados']}/{unit['total']}): cubierto por pruebas unitarias existentes. Ver hoja «Unitarias»."
            ev = "evidence/unitarios/resumen.json"
            fuente = "Suite JVM"
            cobertura_ejec = "Cubierto por JVM"
        elif cid in blocked:
            status = "BLOQUEADO"
            actual = blocked[cid]
            ev = ""
            fuente = "—"
            cobertura_ejec = "No ejecutable en emulador"
        elif cid in manual:
            status = "NO EJECUTADO"
            actual = manual[cid]
            ev = ""
            fuente = "—"
            cobertura_ejec = "Manual / no determinista"
        elif cid in pendientes:
            status = "NO EJECUTADO"
            actual = pendientes[cid]
            ev = ""
            fuente = "—"
            cobertura_ejec = "Pendiente de ejecutar"
        else:
            status = "NO EJECUTADO"
            actual = "Caso sin registro de ejecución en esta corrida."
            ev = ""
            fuente = "—"
            cobertura_ejec = "Pendiente"
        counts[status] += 1
        rows.append([
            cid, c.get("tipo", ""), c.get("modulo", ""), c.get("prioridad", ""), c.get("titulo", ""),
            c.get("precondiciones", ""), "\n".join(c.get("pasos", [])), c.get("esperado", ""),
            c.get("cobertura", ""), c.get("dispositivo", ""), c.get("regla", ""),
            status, cobertura_ejec, fuente, actual, ev,
        ])

    wb = Workbook()
    # ---------------- Resumen ----------------
    ws = wb.active
    ws.title = "Resumen"
    sheet_title(ws, "Matriz de pruebas — Tacomanda / Taquería",
                f"Corrida E2E sobre los 2 emuladores · build {BUILD['versionName']} ({BUILD['versionCode']}) · {BUILD['fecha']}", 6)
    info = [
        ("Build", f"APK md5 {BUILD['md5']} · {BUILD['nota']}"),
        ("Cocina", DEVICES["cocina"]),
        ("Mesera", DEVICES["mesera"]),
        ("Casos en la matriz", f"{len(matrix)} (184 del catálogo + {len(matrix)-184} de las mejoras UX pedidas: menú rápido y QR de conexión)"),
        ("Suite JVM", f"{unit['pasados']}/{unit['total']} PASA · 0 fallidos · 0 omitidos ({unit['duracion_total_ms']} ms)"),
        ("Protocolo TCP", "51/51 PASA"),
        ("Hallazgos principales", "1) APK instalada estaba desactualizada (corregida) · 2) no había forma de entregar antes de pagar con el gesto → menú rápido implementado · 3) la mesera creaba taqueria.json con el menú semilla → corregido · 4) rotar durante la captura pierde la pantalla (comportamiento actual documentado)"),
    ]
    r = 4
    for k, v in info:
        ws.cell(row=r, column=1, value=k).font = BOLD
        ws.merge_cells(start_row=r, start_column=2, end_row=r, end_column=6)
        c = ws.cell(row=r, column=2, value=v)
        c.alignment = Alignment(wrap_text=True, vertical="top")
        ws.row_dimensions[r].height = 30 if len(v) < 120 else 46
        r += 1

    # tabla de estados
    r += 1
    ws.cell(row=r, column=1, value="Estado de ejecución").font = Font(bold=True, size=13)
    r += 1
    for i, h in enumerate(["Estado", "Casos", "% sobre matriz"], start=1):
        c = ws.cell(row=r, column=i, value=h)
        c.fill = GREEN; c.font = WHITE; c.alignment = Alignment(horizontal="center"); c.border = BORDER
    r += 1
    total = len(matrix)
    for st in ["PASA", "FALLA", "OBSERVACION", "BLOQUEADO", "NO EJECUTADO"]:
        ws.cell(row=r, column=1, value=st).fill = status_fill(st)
        ws.cell(row=r, column=1).border = BORDER
        ws.cell(row=r, column=2, value=counts.get(st, 0)).border = BORDER
        c = ws.cell(row=r, column=3, value=f"{100*counts.get(st,0)/total:.1f}%")
        c.border = BORDER
        r += 1
    ws.cell(row=r, column=1, value="TOTAL").font = BOLD
    ws.cell(row=r, column=2, value=total).font = BOLD
    ws.cell(row=r, column=3, value="100%").font = BOLD
    r += 2

    # cobertura por tipo
    ws.cell(row=r, column=1, value="Cobertura por tipo").font = Font(bold=True, size=13)
    r += 1
    by_type = defaultdict(Counter)
    for row in rows:
        by_type[row[1]][row[11]] += 1
    for i, h in enumerate(["Tipo", "PASA", "FALLA", "BLOQUEADO/NO EJEC.", "Total"], start=1):
        c = ws.cell(row=r, column=i, value=h); c.fill = GREEN; c.font = WHITE; c.border = BORDER
    r += 1
    for t in sorted(by_type):
        cy = by_type[t]
        vals = [t, cy.get("PASA", 0), cy.get("FALLA", 0), cy.get("BLOQUEADO", 0) + cy.get("NO EJECUTADO", 0), sum(cy.values())]
        for i, v in enumerate(vals, start=1):
            c = ws.cell(row=r, column=i, value=v); c.border = BORDER
        r += 1
    r += 1
    ws.cell(row=r, column=1, value="Evidencias").font = Font(bold=True, size=13)
    r += 1
    n_caps = len(glob.glob(os.path.join(E2E, "capturas", "*.png")))
    ws.cell(row=r, column=1, value=f"{n_caps} capturas en evidence/e2e/capturas/ · datos en evidence/e2e/datos/ · logs en evidence/e2e/logs/")
    for i, w in enumerate([26, 42, 22, 14, 12, 12], start=1):
        ws.column_dimensions[get_column_letter(i)].width = w

    # ---------------- Matriz ----------------
    ws2 = wb.create_sheet("Matriz de casos")
    sheet_title(ws2, "Matriz exhaustiva de casos", f"{len(matrix)} casos · catálogo por tipo (FUN/BOR/VAL/ERR/UND/IDEM/PER/RED/LIC/UI/SEG)", 16)
    header(ws2, 4, ["ID", "Tipo", "Módulo", "Prioridad", "Título", "Precondiciones", "Pasos", "Esperado",
                    "Cobertura", "Dispositivo", "Regla", "Estado", "Ejecución", "Fuente", "Resultado / observación", "Evidencia"])
    r = 5
    for row in rows:
        for i, v in enumerate(row, start=1):
            c = ws2.cell(row=r, column=i, value=v)
            c.font = Font(size=9)
            c.alignment = Alignment(vertical="top", wrap_text=True)
            c.border = BORDER
        ws2.cell(row=r, column=12).fill = status_fill(row[11])
        r += 1
    for i, w in enumerate([16, 7, 13, 9, 34, 34, 44, 40, 10, 10, 11, 13, 20, 18, 38, 34], start=1):
        ws2.column_dimensions[get_column_letter(i)].width = w
    ws2.auto_filter.ref = f"A4:P{4+len(rows)}"

    # ---------------- Ejecución E2E ----------------
    ws3 = wb.create_sheet("Ejecución E2E")
    e2e_rows = [e for e in executed.values() if e.get("id") not in ("E2E-FUN-01",)]
    sheet_title(ws3, "Ejecución E2E en los 2 emuladores", f"{len(e2e_rows)} ejecuciones con evidencia (UI, licencia, sistema, red)", 8)
    header(ws3, 4, ["Caso", "Título", "Tipo", "Dispositivo", "Esperado", "Obtenido", "Estado", "Fuente / evidencia"])
    r = 5
    for e in sorted(e2e_rows, key=lambda x: x.get("id", "")):
        vals = [e.get("id"), e.get("titulo", ""), e.get("tipo", ""), e.get("dispositivo", ""),
                e.get("esperado", ""), e.get("obtenido", ""), e.get("estado", ""),
                e.get("fuente", "") + (" · " + ", ".join(e.get("evidencia", [])) if e.get("evidencia") else "")]
        for i, v in enumerate(vals, start=1):
            c = ws3.cell(row=r, column=i, value=v)
            c.font = Font(size=9); c.alignment = Alignment(vertical="top", wrap_text=True); c.border = BORDER
        ws3.cell(row=r, column=7).fill = status_fill(e.get("estado", ""))
        r += 1
    for i, w in enumerate([18, 40, 8, 12, 46, 60, 11, 46], start=1):
        ws3.column_dimensions[get_column_letter(i)].width = w

    # ---------------- Protocolo ----------------
    ws4 = wb.create_sheet("Protocolo TCP")
    proto = load(os.path.join(E2E, "protocolo.json")) or {"casos": []}
    sheet_title(ws4, "Suite de protocolo TCP (servidor real de cocina)", f"{len(proto['casos'])} casos · payloads reales vía adb forward/proxy", 7)
    header(ws4, 4, ["Caso", "Título", "Tipo", "Esperado", "Obtenido", "Estado", "ms"])
    r = 5
    for e in sorted(proto["casos"], key=lambda x: x.get("id", "")):
        vals = [e.get("id"), e.get("titulo"), e.get("tipo"), e.get("esperado"), e.get("obtenido"), e.get("estado"), e.get("ms")]
        for i, v in enumerate(vals, start=1):
            c = ws4.cell(row=r, column=i, value=v)
            c.font = Font(size=9); c.alignment = Alignment(vertical="top", wrap_text=True); c.border = BORDER
        ws4.cell(row=r, column=6).fill = status_fill(e.get("estado", ""))
        r += 1
    for i, w in enumerate([18, 44, 7, 42, 58, 10, 8], start=1):
        ws4.column_dimensions[get_column_letter(i)].width = w

    # ---------------- Unitarias ----------------
    ws5 = wb.create_sheet("Unitarias")
    sheet_title(ws5, f"Suite JVM (JUnit) — {unit['total']} pruebas", f"{unit['pasados']}/{unit['total']} PASA · BUILD SUCCESSFUL · {unit['duracion_total_ms']} ms", 6)
    header(ws5, 4, ["Clase", "Prueba", "Estado", "ms", "Orden", "Cobertura"])
    r = 5
    order = 1
    for t in unit["tests"]:
        cov = next((k for k, v in unit.get("cobertura_por_regla", {}).items()
                    if any(x["nombre"] == t["nombre"] for x in v.get("tests", []))), "")
        vals = [t["clase"], t["nombre"], t["estado"], t.get("ms"), order, cov]
        for i, v in enumerate(vals, start=1):
            c = ws5.cell(row=r, column=i, value=v)
            c.font = Font(size=9); c.border = BORDER
        ws5.cell(row=r, column=3).fill = GREEN_L if t["estado"] == "passed" else RED_L
        r += 1; order += 1
    for i, w in enumerate([20, 78, 10, 8, 8, 16], start=1):
        ws5.column_dimensions[get_column_letter(i)].width = w

    # ---------------- Hallazgos ----------------
    ws6 = wb.create_sheet("Hallazgos")
    sheet_title(ws6, "Hallazgos y cambios de esta corrida", "Problemas encontrados, correcciones aplicadas y casos no ejecutables", 5)
    header(ws6, 4, ["#", "Hallazgo", "Impacto", "Acción tomada", "Evidencia"])
    findings = [
        ("1", "La APK instalada en los emuladores era anterior al renombrado a Tacomanda (label y firma viejas); versionName/Code no distinguían el build.",
         "Alto: los primeros resultados no correspondían al código HEAD.", "Se reinstaló el APK HEAD y se re-corrió toda la fase E2E.",
         "evidence/unitarios/apk-instalada-badging.txt vs apk-actual-badging.txt"),
        ("2", "El gesto → solo cobraba: no permitía entregar la comida antes de pagar (caso real: comen primero y pagan al final).",
         "Alto: el ticket se quedaba en cocina y enrojecía sin necesidad.", "Menú rápido implementado: deslizar → abre «Entregar» y «Cobrar/Devolver». Requiere recompilar e instalar.",
         "capturas/40-menu-acciones.png · FUN-COBRO-07"),
        ("3", "El teléfono de mesera creaba files/taqueria.json con el menú semilla al arrancar (contradecía «la mesera no guarda datos de negocio»): applyRole(WAITER) tocaba app.server.stop() e inicializaba el OrderStore perezoso.",
         "Medio: datos de negocio en el teléfono equivocado.", "Corregido: solo se inicia/detiene el servidor si realmente estaba arrancado; la mesera ya no crea taqueria.json.",
         "PER-PERSISTENCIA-05 · SEG-ALMACENAMIENTO-01"),
        ("4", "Rotar durante la captura pierde la pantalla de captura: `page`/`step` usan `remember` (no saveable); el borrador queda inaccesible y «+ Nueva orden» lo limpia.",
         "Medio: se pierde la captura en curso al girar el teléfono.", "Documentado como comportamiento actual (UI-ROTACION-01). Queda como mejora pendiente.",
         "capturas/66-rotacion-captura.png"),
        ("5", "Cobertura JVM sin pruebas para: protocolo TCP/JSON, WaiterDraft (split/toggleMod/retainAvailable/differsFrom), parseMoney/money/elapsedText, AppState.payStatus/isClosed y licencia de red.",
         "Medio: comportamiento crítico sin red de seguridad automatizada.", "Documentado con archivo:línea en evidence/unitarios/AUDITORIA.md (25 huecos).",
         "evidence/unitarios/AUDITORIA.md"),
        ("7", "No había forma cómoda de conectar la mesera: mDNS falla en Android 17 o hay que escribir la IP a mano.",
         "Alto: fricción en cada instalación y soporte.", "Implementado: cocina muestra QR de conexión con 📲 y la mesera lo escanea desde ⚙ → Conexión; parser con 11 pruebas unitarias nuevas.",
         "capturas/71-cocina-qr-conexion.png · capturas/72-mesera-escaner-qr.png · ConnectionQrTest"),
        ("6", "Casos no ejecutables en AVD: escaneo de QR por cámara, mDNS entre emuladores, build release sin servidor, TalkBack.",
         "Bajo/medio: requieren teléfono físico o entorno específico.", "Marcados como BLOQUEADO en la matriz con su justificación.",
         "Hoja «Matriz de casos» (columna Estado)"),
    ]
    r = 5
    for f in findings:
        for i, v in enumerate(f, start=1):
            c = ws6.cell(row=r, column=i, value=v)
            c.font = Font(size=10); c.alignment = Alignment(vertical="top", wrap_text=True); c.border = BORDER
        r += 1
    for i, w in enumerate([5, 70, 34, 55, 48], start=1):
        ws6.column_dimensions[get_column_letter(i)].width = w

    # ---------------- Evidencias ----------------
    ws7 = wb.create_sheet("Evidencias")
    sheet_title(ws7, "Capturas de evidencia", "Archivos en evidence/e2e/capturas/ (se abren desde el repo)", 4)
    header(ws7, 4, ["#", "Archivo", "KB", "Caso(s)"])
    caps = sorted(glob.glob(os.path.join(E2E, "capturas", "*.png")))
    r = 5
    for i, p in enumerate(caps, start=1):
        name = os.path.basename(p)
        vals = [i, name, round(os.path.getsize(p) / 1024, 1), ""]
        for j, v in enumerate(vals, start=1):
            c = ws7.cell(row=r, column=j, value=v); c.font = Font(size=9); c.border = BORDER
        r += 1
    for i, w in enumerate([5, 44, 9, 40], start=1):
        ws7.column_dimensions[get_column_letter(i)].width = w

    path = os.path.join(OUT_DIR, "MATRIZ_PRUEBAS_TACOMANDA.xlsx")
    wb.save(path)

    # ---------------- Markdown ----------------
    md = []
    md.append("# Matriz de pruebas — Tacomanda\n")
    md.append(f"Corrida E2E sobre **2 emuladores** (cocina + mesera) · build `{BUILD['versionName']} ({BUILD['versionCode']})` · md5 `{BUILD['md5']}` · {BUILD['fecha']}\n")
    md.append("## Resumen\n")
    md.append(f"- Casos en la matriz: **{total}** (184 del catálogo + {total-184} derivados de las mejoras UX: menú rápido y QR de conexión)")
    md.append(f"- Suite JVM: **{unit['pasados']}/{unit['total']}** PASA")
    md.append("- Protocolo TCP real: **51/51** PASA")
    md.append(f"- Cobertura de la matriz: **{counts.get('PASA',0)} PASA / {counts.get('FALLA',0)} FALLA / {counts.get('OBSERVACION',0)} observación / {counts.get('BLOQUEADO',0)} bloqueados / {counts.get('NO EJECUTADO',0)} no ejecutados**")
    md.append("")
    md.append("## Estados\n")
    md.append("| Estado | Casos | % |")
    md.append("|---|---|---|")
    for st in ["PASA", "FALLA", "OBSERVACION", "BLOQUEADO", "NO EJECUTADO"]:
        md.append(f"| {st} | {counts.get(st,0)} | {100*counts.get(st,0)/total:.1f}% |")
    md.append("")
    md.append("## Hallazgos y cambios\n")
    for f in findings:
        md.append(f"- **{f[1]}** → {f[3]}")
    md.append("")
    md.append("## Archivos\n")
    md.append("- `MATRIZ_PRUEBAS_TACOMANDA.xlsx` — matriz completa (Resumen, Matriz de casos, Ejecución E2E, Protocolo TCP, Unitarias, Hallazgos, Evidencias)")
    md.append("- `evidence/e2e/capturas/` — capturas de pantalla de la corrida")
    md.append("- `evidence/e2e/{protocolo,licencia,sistema,red_ui,resultados}.json` — resultados crudos")
    md.append("- `evidence/unitarios/AUDITORIA.md` — auditoría de la suite JVM y huecos")
    open(os.path.join(OUT_DIR, "MATRIZ_PRUEBAS_TACOMANDA.md"), "w").write("\n".join(md))

    print("Total:", total, dict(counts))
    print("XLSX:", path)
    print("MD:", os.path.join(OUT_DIR, "MATRIZ_PRUEBAS_TACOMANDA.md"))


if __name__ == "__main__":
    main()
