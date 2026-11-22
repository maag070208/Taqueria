#!/usr/bin/env python3
"""Genera evidence/unitarios/resumen.json a partir de los XML de JUnit y del snapshot de auditoría.

Uso (desde la raíz del repo):
    python3 evidence/unitarios/generar_resumen.py

Solo lee de app/build/test-results/ y escribe resumen.json junto a este script.
"""
import datetime
import glob
import json
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent / "resumen.json"
XML_GLOB = str(ROOT / "app/build/test-results/testDebugUnitTest/*.xml")

O, L, Q = "OrderStoreTest", "LicenseTest", "QrPayloadTest"


def parse_tests():
    rows = []
    for f in sorted(glob.glob(XML_GLOB)):
        root = ET.parse(f).getroot()
        cls = root.get("name").split(".")[-1]
        for tc in root.findall("testcase"):
            status = "passed"
            if tc.find("failure") is not None:
                status = "failed"
            elif tc.find("error") is not None:
                status = "error"
            elif tc.find("skipped") is not None:
                status = "skipped"
            rows.append({
                "clase": cls,
                "nombre": tc.get("name"),
                "estado": status,
                "ms": int(round(float(tc.get("time")) * 1000)),
            })
    order = {O: 0, L: 1, Q: 2}
    rows.sort(key=lambda t: (order.get(t["clase"], 9), t["nombre"]))
    return rows


def test_refs(value):
    return [{"clase": c, "nombre": n} for c, n in value]


rows = parse_tests()
total = len(rows)
passed = sum(1 for r in rows if r["estado"] == "passed")
failed = sum(1 for r in rows if r["estado"] in ("failed", "error"))
skipped = sum(1 for r in rows if r["estado"] == "skipped")

por_clase = {}
for r in rows:
    c = por_clase.setdefault(r["clase"], {"tests": 0, "ms": 0, "pasados": 0})
    c["tests"] += 1
    c["ms"] += r["ms"]
    if r["estado"] == "passed":
        c["pasados"] += 1

cobertura = {
    "regla_1": {
        "descripcion": "La cantidad de un producto = suma de sus grupos; grupos idénticos se unen; cantidades 1-99.",
        "estado": "parcial",
        "tests": [[O, "cuatro tacos de asada se dividen en dos grupos dentro de una sola orden"],
                  [O, "grupos identicos se unen y las modificaciones combinables se aceptan"],
                  [O, "opciones exclusivas contradictorias o ajenas se rechazan"]],
        "fuente": "app/src/main/java/com/axzy/taqueria/OrderStore.kt:290-319",
        "pruebas": "app/src/test/java/com/axzy/taqueria/OrderStoreTest.kt:32-58",
        "nota": "Cubre división en grupos, unión de idénticos y rechazo de qty 0. Sin prueba: tope 99 (OrderStore.kt:299), tope de 60 grupos (OrderStore.kt:292), nota como parte de la clave de unión (OrderStore.kt:313) y orden por menú (OrderStore.kt:318).",
    },
    "regla_2": {
        "descripcion": "Modificaciones validadas contra los tipos del producto; en tipos exclusivos máximo una opción.",
        "estado": "cubierta",
        "tests": [[O, "opciones exclusivas contradictorias o ajenas se rechazan"],
                  [O, "grupos identicos se unen y las modificaciones combinables se aceptan"]],
        "fuente": "app/src/main/java/com/axzy/taqueria/OrderStore.kt:300-311",
        "pruebas": "app/src/test/java/com/axzy/taqueria/OrderStoreTest.kt:42-58",
        "nota": "Cubre exclusivas contradictorias, opción ajena y combinables en grupo no exclusivo. Sin prueba: normalización trim/duplicados (OrderStore.kt:301) y reordenar mods al orden del catálogo (OrderStore.kt:311).",
    },
    "regla_3": {
        "descripcion": "Reintentos con el mismo clientKey devuelven la misma orden/pago (no duplican).",
        "estado": "parcial",
        "tests": [[O, "reintento con la misma llave no duplica"]],
        "fuente": "app/src/main/java/com/axzy/taqueria/OrderStore.kt:38 y :157",
        "pruebas": "app/src/test/java/com/axzy/taqueria/OrderStoreTest.kt:60-67",
        "nota": "Solo se prueba la idempotencia de createOrder; la de addPayment (OrderStore.kt:157) no tiene prueba.",
    },
    "regla_4": {
        "descripcion": "Cuenta nueva = siguiente número dentro de la mesa; pagar una cuenta no afecta a las demás.",
        "estado": "cubierta",
        "tests": [[O, "cuentas separadas en la misma mesa con saldos independientes"],
                  [O, "pagar una cuenta no paga las demas de la mesa"]],
        "fuente": "app/src/main/java/com/axzy/taqueria/OrderStore.kt:45-54",
        "pruebas": "app/src/test/java/com/axzy/taqueria/OrderStoreTest.kt:69-79 y :97-105",
        "nota": "Falta: numeración tras cancelar/cerrar cuentas y rechazo de cuenta de otra mesa (OrderStore.kt:52).",
    },
    "regla_5": {
        "descripcion": "Pagos parciales permitidos; no se acepta pagar más que el saldo; efectivo con receivedCents >= importe.",
        "estado": "parcial",
        "tests": [[O, "pago parcial en efectivo y transferencia no cambia la preparacion"],
                  [O, "cobrar en efectivo guarda con cuanto pagaron y entrega la orden"],
                  [O, "deshacer pago lo anula sin borrarlo"]],
        "fuente": "app/src/main/java/com/axzy/taqueria/OrderStore.kt:154-188",
        "pruebas": "app/src/test/java/com/axzy/taqueria/OrderStoreTest.kt:81-95, :119-127 y :170-186",
        "nota": "Cubre parcial, exceso de saldo y received<importe. Sin prueba: importe cero (OrderStore.kt:162), devolución negativa y su tope (OrderStore.kt:165), received>10M (OrderStore.kt:172) y cambio solo en efectivo (OrderStore.kt:170).",
    },
    "regla_6": {
        "descripcion": "Cobrar = entregar (marcado por defecto); deshacer el pago regresa lo pendiente a cocina.",
        "estado": "cubierta",
        "tests": [[O, "cobrar en efectivo guarda con cuanto pagaron y entrega la orden"],
                  [O, "pago parcial en efectivo y transferencia no cambia la preparacion"]],
        "fuente": "app/src/main/java/com/axzy/taqueria/OrderStore.kt:180-186 y :203-207",
        "pruebas": "app/src/test/java/com/axzy/taqueria/OrderStoreTest.kt:170-186",
        "nota": "Cubre deliver=true con deshacer. Falta: deshacer entrega manual (setOrderStatus DELIVERED->PENDING, OrderStore.kt:99-102) y cobro con deliver=false.",
    },
    "regla_7": {
        "descripcion": "Editar orden solo si sigue PENDING; la versión anterior queda en revisions; lo ya pedido se acepta aunque luego se agote.",
        "estado": "cubierta",
        "tests": [[O, "editar 4 de asada a 2 asada 1 adobada 1 chorizo conserva la version anterior"],
                  [O, "no se edita una orden entregada y se respeta lo agotado ya pedido"]],
        "fuente": "app/src/main/java/com/axzy/taqueria/OrderStore.kt:75-87",
        "pruebas": "app/src/test/java/com/axzy/taqueria/OrderStoreTest.kt:146-168",
        "nota": "Falta: edición idéntica que no debe crear revisión (OrderStore.kt:83), cuenta cancelada (OrderStore.kt:81) y producto borrado del catálogo (OrderStore.kt:295-311).",
    },
    "regla_8": {
        "descripcion": "Deshacer (cancelar cuenta/orden, entregar, pagar) aceptado por cocina durante 30 s (UNDO_WINDOW_MS).",
        "estado": "parcial",
        "tests": [[O, "cancelar cuenta se puede deshacer solo dentro del periodo"],
                  [O, "deshacer pago lo anula sin borrarlo"],
                  [O, "cobrar en efectivo guarda con cuanto pagaron y entrega la orden"]],
        "fuente": "app/src/main/java/com/axzy/taqueria/OrderStore.kt:324-326",
        "pruebas": "app/src/test/java/com/axzy/taqueria/OrderStoreTest.kt:107-117 y :170-186",
        "nota": "Solo se prueba fuera-de-ventana para restoreAccount. Sin prueba: voidPayment fuera de ventana (OrderStore.kt:196) y deshacer entrega/cancelación de orden (OrderStore.kt:99-109).",
    },
    "regla_9": {
        "descripcion": "Cuenta cerrada = sin pendientes en cocina y saldo 0; tras 30 s se archiva en historial.jsonl.",
        "estado": "parcial",
        "tests": [[O, "cuenta cerrada pasa al historial despues del periodo de deshacer"],
                  [O, "pago parcial en efectivo y transferencia no cambia la preparacion"]],
        "fuente": "app/src/main/java/com/axzy/taqueria/Model.kt:162-163 y app/src/main/java/com/axzy/taqueria/OrderStore.kt:213-222, :329-343",
        "pruebas": "app/src/test/java/com/axzy/taqueria/OrderStoreTest.kt:81-95 y :129-144",
        "nota": "Cubre cierre por pago+entrega y archivo de una cuenta. Sin prueba: varias cuentas archivadas, cuenta cancelada, reapertura (closedAt->null) y contenido de historial.jsonl con cancelaciones.",
    },
    "regla_10": {
        "descripcion": "Cancelar cuenta cancela lo pendiente; lo entregado sigue cobrándose.",
        "estado": "parcial",
        "tests": [[O, "cancelar cuenta se puede deshacer solo dentro del periodo"]],
        "fuente": "app/src/main/java/com/axzy/taqueria/OrderStore.kt:119-134",
        "pruebas": "app/src/test/java/com/axzy/taqueria/OrderStoreTest.kt:107-117",
        "nota": "Se prueba que lo PENDING pasa a CANCELLED, pero no que lo DELIVERED siga cobrándose (totalOf ignora canceladas, Model.kt:146).",
    },
    "licencia": {
        "descripcion": "Licencia de Axzy Central: sin código bloquea; activa funciona sin internet hasta validUntil; suspendida/revocada bloquean; atrasar el reloj no la alarga.",
        "estado": "parcial",
        "tests": [[L, "sinCodigoNoEstaActivada"], [L, "activaFuncionaSinInternetHastaSuFecha"],
                  [L, "avisaCuandoEstaPorVencer"], [L, "suspendidaSigueBloqueadaSinInternet"],
                  [L, "revocadaBloquea"], [L, "atrasarElRelojNoAlargaLaLicencia"],
                  [L, "usaLaHoraDelServidorSiElTelefonoVaAtrasado"]],
        "fuente": "app/src/main/java/com/axzy/taqueria/License.kt:78-112",
        "pruebas": "app/src/test/java/com/axzy/taqueria/LicenseTest.kt:19-56",
        "nota": "Solo la lógica pura. Sin prueba: activate/refresh HTTP (License.kt:188-215), resolveApiUrl con URL del QR (License.kt:237-239), 401->revoked (License.kt:212), persistencia en prefs (License.kt:316-331) y bloqueo del servidor (KitchenServer.kt:120).",
    },
    "qr": {
        "descripcion": "Parser del QR de activación (contrato §6.1): JSON con v/u/c/p, código suelto axc_ y rechazos con mensaje claro.",
        "estado": "cubierta",
        "tests": [[Q, "jsonDelContratoConServidorYProducto"], [Q, "jsonSinUrlUsaElServidorCompilado"],
                  [Q, "jsonSinProductoEsValido"], [Q, "jsonConLlavesExtraLasIgnora"],
                  [Q, "quitaLaDiagonalFinalDeLaUrl"], [Q, "productoAjenoDaMensajeClaro"],
                  [Q, "versionDistintaSeRechaza"], [Q, "versionFaltanteSeRechaza"],
                  [Q, "urlInvalidaSeRechaza"], [Q, "codigoSueltoAxcSeAcepta"],
                  [Q, "codigoSueltoRecortaEspacios"], [Q, "basuraSeRechaza"],
                  [Q, "codigoVacioEnJsonSeRechaza"]],
        "fuente": "app/src/main/java/com/axzy/taqueria/QrPayload.kt:41-105",
        "pruebas": "app/src/test/java/com/axzy/taqueria/QrPayloadTest.kt:22-108",
        "nota": "Parser sin huecos relevantes. Falta el escaneo con cámara (ui/QrScanner.kt:232) y el flujo de activación en UI (ui/LicenseScreen.kt:61-68).",
    },
}
for k in cobertura:
    cobertura[k]["tests"] = test_refs(cobertura[k]["tests"])

huecos = [
    {"id": "H01", "area": "formato_dinero", "prioridad": "alta",
     "descripcion": "parseMoney (entrada del usuario -> centavos) no tiene ninguna prueba: acepta '80', '80.5' y '80,50'; rechaza negativos, >2 decimales y montos >= 1,000,000.",
     "evidencia": ["app/src/main/java/com/axzy/taqueria/Model.kt:244-246", "consumo: app/src/main/java/com/axzy/taqueria/ui/AccountScreen.kt:227"],
     "riesgo": "Un fallo aquí cobra mal o bloquea el cobro; es la conversión crítica de efectivo/transferencia."},
    {"id": "H02", "area": "formato_dinero", "prioridad": "media",
     "descripcion": "money() y moneyInput() (formato es-MX y relleno del campo de importe) sin pruebas.",
     "evidencia": ["app/src/main/java/com/axzy/taqueria/Model.kt:241", "app/src/main/java/com/axzy/taqueria/Model.kt:248", "consumo: app/src/main/java/com/axzy/taqueria/ui/AccountScreen.kt:257"],
     "riesgo": "Redondeo/formatos distintos entre pantallas; montos mostrados al cliente."},
    {"id": "H03", "area": "formato_tiempo", "prioridad": "media",
     "descripcion": "elapsedText() (espera en cocina 'm:ss') y su interacción con los umbrales de color sin pruebas.",
     "evidencia": ["app/src/main/java/com/axzy/taqueria/Model.kt:250-253", "app/src/main/java/com/axzy/taqueria/ui/KitchenScreen.kt:189", "app/src/main/java/com/axzy/taqueria/ui/AccountScreen.kt:164"],
     "riesgo": "Tiempos de espera mal calculados o negativos en la pantalla pasiva de cocina."},
    {"id": "H04", "area": "formato_tiempo", "prioridad": "baja",
     "descripcion": "clockText() y tableLabel() sin pruebas.",
     "evidencia": ["app/src/main/java/com/axzy/taqueria/Model.kt:256", "app/src/main/java/com/axzy/taqueria/Model.kt:258"],
     "riesgo": "Hora de recepción y etiqueta de mesa ('Llevar' -> 'Para llevar')."},
    {"id": "H05", "area": "waiter_draft", "prioridad": "alta",
     "descripcion": "WaiterDraft.split() (✂ dividir preparación: 4 -> 2+2, 5 -> 3+2, invariante de total) no tiene prueba.",
     "evidencia": ["app/src/main/java/com/axzy/taqueria/App.kt:143-150", "consumo: app/src/main/java/com/axzy/taqueria/ui/CaptureScreen.kt:476"],
     "riesgo": "Es una acción de un toque sobre el grupo más grande; si pierde o duplica piezas el ticket sale mal."},
    {"id": "H06", "area": "waiter_draft", "prioridad": "media",
     "descripcion": "WaiterDraft.toggleMod() (exclusividad: elegir una salsa quita las otras) sin prueba.",
     "evidencia": ["app/src/main/java/com/axzy/taqueria/App.kt:153-160", "consumo: app/src/main/java/com/axzy/taqueria/ui/CaptureScreen.kt:460"],
     "riesgo": "La UI puede armar combinaciones que cocina rechazará (regla 2 se revalida en servidor)."},
    {"id": "H07", "area": "waiter_draft", "prioridad": "media",
     "descripcion": "WaiterDraft.add/removeOne/setQty (un toque = +1, tope 99, bajar de 1 quita el grupo) sin prueba.",
     "evidencia": ["app/src/main/java/com/axzy/taqueria/App.kt:125-140"],
     "riesgo": "Captura rápida: cantidades fuera de rango o grupos fantasma."},
    {"id": "H08", "area": "waiter_draft", "prioridad": "media",
     "descripcion": "WaiterDraft.retainAvailable() (quita lo que cocina desactivó al capturar, pero conserva lo ya pedido al editar) sin prueba.",
     "evidencia": ["app/src/main/java/com/axzy/taqueria/App.kt:170-174"],
     "riesgo": "Pérdida silenciosa de productos o envío de productos agotados."},
    {"id": "H09", "area": "waiter_draft", "prioridad": "media",
     "descripcion": "WaiterDraft.differsFrom() (normaliza por producto+mods+nota para avisar cambios sin guardar) sin prueba.",
     "evidencia": ["app/src/main/java/com/axzy/taqueria/App.kt:114-118", "consumo: app/src/main/java/com/axzy/taqueria/ui/CaptureScreen.kt:87"],
     "riesgo": "Descartar ediciones sin avisar o bloquear salidas innecesariamente."},
    {"id": "H10", "area": "app_state_derivado", "prioridad": "media",
     "descripcion": "AppState.payStatus()/isClosed() solo se ejercitan de forma indirecta; las ramas CANCELLED (Model.kt:151) y saldo negativo o cuenta sin órdenes (Model.kt:162-163) no se afirman directamente.",
     "evidencia": ["app/src/main/java/com/axzy/taqueria/Model.kt:150-163", "indirecto en: app/src/test/java/com/axzy/taqueria/OrderStoreTest.kt:86,90,104,126"],
     "riesgo": "Gobiernan gestos (cobrar/entregar/devolver), colores y el archivado de cuentas."},
    {"id": "H11", "area": "regla_3", "prioridad": "alta",
     "descripcion": "Idempotencia de pagos por clientKey (reintento de cobro no duplica el movimiento) sin prueba.",
     "evidencia": ["app/src/main/java/com/axzy/taqueria/OrderStore.kt:157", "prueba existente solo para createOrder: app/src/test/java/com/axzy/taqueria/OrderStoreTest.kt:60-67"],
     "riesgo": "Cobro duplicado al reintentar con mala señal (regla 3 aplica a órdenes y pagos)."},
    {"id": "H12", "area": "regla_5", "prioridad": "media",
     "descripcion": "Rechazos de pago no probados: importe cero (OrderStore.kt:162), devolución negativa que excede lo debido (OrderStore.kt:165), received>10M (OrderStore.kt:172) y cambio solo en efectivo (OrderStore.kt:170).",
     "evidencia": ["app/src/main/java/com/axzy/taqueria/OrderStore.kt:160-173"],
     "riesgo": "Devoluciones o cambios inválidos aceptados."},
    {"id": "H13", "area": "regla_8", "prioridad": "media",
     "descripcion": "Ventanas de deshacer sin prueba fuera del caso restoreAccount: voidPayment fuera de 30 s (OrderStore.kt:196) y deshacer entrega/cancelación de orden (OrderStore.kt:99-109).",
     "evidencia": ["app/src/main/java/com/axzy/taqueria/OrderStore.kt:99-109", "app/src/main/java/com/axzy/taqueria/OrderStore.kt:191-210", "app/src/main/java/com/axzy/taqueria/OrderStore.kt:324-326"],
     "riesgo": "Deshacer fuera de plazo o entregas que no regresan a cocina."},
    {"id": "H14", "area": "regla_10", "prioridad": "media",
     "descripcion": "Cancelar cuenta con líneas ya entregadas: no se prueba que lo entregado siga cobrándose (totalOf ignora CANCELLED).",
     "evidencia": ["app/src/main/java/com/axzy/taqueria/OrderStore.kt:119-134", "app/src/main/java/com/axzy/taqueria/Model.kt:146"],
     "riesgo": "Cobros perdidos al cancelar cuentas con consumo ya servido."},
    {"id": "H15", "area": "regla_9", "prioridad": "baja",
     "descripcion": "archiveClosed() solo se prueba con 1 cuenta; sin prueba: lote con varias cuentas, cuenta cancelada, historial vacío y reapertura (closedAt->null).",
     "evidencia": ["app/src/main/java/com/axzy/taqueria/OrderStore.kt:213-222", "app/src/main/java/com/axzy/taqueria/OrderStore.kt:329-343", "prueba: app/src/test/java/com/axzy/taqueria/OrderStoreTest.kt:129-144"],
     "riesgo": "Pérdida o duplicación de registros del historial (nada se borra, regla 9)."},
    {"id": "H16", "area": "persistencia", "prioridad": "media",
     "descripcion": "Arranque con taqueria.json corrupto (respaldo taqueria.json.corrupt-<ts> y semilla) sin prueba.",
     "evidencia": ["app/src/main/java/com/axzy/taqueria/OrderStore.kt:345-356"],
     "riesgo": "Pérdida de datos no recuperable si el respaldo falla; comportamiento documentado en CONTEXTO.md."},
    {"id": "H17", "area": "regla_4", "prioridad": "baja",
     "descripcion": "Numeración diaria de órdenes (counterDate/counterLast: reinicia cada día) sin prueba.",
     "evidencia": ["app/src/main/java/com/axzy/taqueria/OrderStore.kt:56-57", "app/src/main/java/com/axzy/taqueria/OrderStore.kt:70"],
     "riesgo": "Números repetidos o salto de folio entre días."},
    {"id": "H18", "area": "validaciones_order", "prioridad": "media",
     "descripcion": "Validaciones de createOrder sin prueba: clientKey vacío/>64 (OrderStore.kt:39), mesa >20 (OrderStore.kt:41), cuenta cerrada/cancelada/de otra mesa (OrderStore.kt:49-52), tope 60 grupos (OrderStore.kt:292), nota >100 (OrderStore.kt:312).",
     "evidencia": ["app/src/main/java/com/axzy/taqueria/OrderStore.kt:36-54", "app/src/main/java/com/axzy/taqueria/OrderStore.kt:290-319"],
     "riesgo": "Mensajes de error y límites que protegen la red y el archivo."},
    {"id": "H19", "area": "regla_7", "prioridad": "media",
     "descripcion": "Editar orden con producto borrado del catálogo (se conservan sus mods originales) sin prueba.",
     "evidencia": ["app/src/main/java/com/axzy/taqueria/OrderStore.kt:295-311"],
     "riesgo": "Edición de órdenes viejas tras cambios de catálogo."},
    {"id": "H20", "area": "catalogo", "prioridad": "media",
     "descripcion": "Catálogo sin pruebas: saveProduct con validaciones (precio 0..10M, emoji <=16, grupos filtrados), deleteProduct, moveProduct, saveModifierGroup (opción repetida en otro tipo) y deleteModifierGroup; seed() tampoco se valida.",
     "evidencia": ["app/src/main/java/com/axzy/taqueria/OrderStore.kt:227-282", "app/src/main/java/com/axzy/taqueria/OrderStore.kt:371-393", "uso indirecto: app/src/test/java/com/axzy/taqueria/OrderStoreTest.kt:163"],
     "riesgo": "Precios/orden/agotados mal guardados desde AdminScreen."},
    {"id": "H21", "area": "protocolo_tcp", "prioridad": "alta",
     "descripcion": "Protocolo TCP y serialización sin pruebas JVM: KitchenServer.handle (incluye rechazo por licencia bloqueada), readLineLimited (tope 64 KB), WaiterClient.request (timeout 8 s), session/clockOffset, mDNS y parseHost; tampoco hay round-trip de ClientMsg/ServerMsg.",
     "evidencia": ["app/src/main/java/com/axzy/taqueria/KitchenServer.kt:113-137", "app/src/main/java/com/axzy/taqueria/KitchenServer.kt:120", "app/src/main/java/com/axzy/taqueria/KitchenServer.kt:205-217", "app/src/main/java/com/axzy/taqueria/WaiterClient.kt:121-142", "app/src/main/java/com/axzy/taqueria/WaiterClient.kt:144-168", "app/src/main/java/com/axzy/taqueria/WaiterClient.kt:171-210", "app/src/main/java/com/axzy/taqueria/WaiterClient.kt:224-230", "app/src/main/java/com/axzy/taqueria/Model.kt:174-228"],
     "riesgo": "Es el contrato entre los dos teléfonos: un cambio de campo rompe cocina/mesera sin que la suite lo detecte."},
    {"id": "H22", "area": "licencia_red", "prioridad": "alta",
     "descripcion": "Licencia sin prueba de red/persistencia: activate/refresh HTTP, resolveApiUrl (la URL del QR manda sobre la compilada), 401->revoked, guardado en prefs y bloqueo del servidor de cocina.",
     "evidencia": ["app/src/main/java/com/axzy/taqueria/License.kt:188-215", "app/src/main/java/com/axzy/taqueria/License.kt:237-239", "app/src/main/java/com/axzy/taqueria/License.kt:242-272", "app/src/main/java/com/axzy/taqueria/License.kt:279-331"],
     "riesgo": "Una activación que no persiste deja la app bloqueada tras reiniciar; el bloqueo del servidor no se verifica."},
    {"id": "H23", "area": "qr_escaneo", "prioridad": "media",
     "descripcion": "Parser del QR cubierto (13/13), pero el escaneo CameraX/ZXing y el flujo de activación en UI no tienen prueba (ni instrumentada).",
     "evidencia": ["app/src/main/java/com/axzy/taqueria/ui/QrScanner.kt:72", "app/src/main/java/com/axzy/taqueria/ui/QrScanner.kt:145", "app/src/main/java/com/axzy/taqueria/ui/QrScanner.kt:232", "app/src/main/java/com/axzy/taqueria/ui/LicenseScreen.kt:61-68"],
     "riesgo": "El camino de alta del local depende de la cámara; sin emulador de cámara solo se valida en dispositivo."},
    {"id": "H24", "area": "ui_compose", "prioridad": "media",
     "descripcion": "No existe app/src/androidTest: sin pruebas de UI/instrumentadas. Lógica de presentación sin cubrir: SwipeCard (umbral 1/3), payColor, waitColor/isCritical, gestos de cuenta y editor de grupos.",
     "evidencia": ["app/src/main/java/com/axzy/taqueria/ui/Components.kt:151-190", "app/src/main/java/com/axzy/taqueria/ui/WaiterScreen.kt:272", "app/src/main/java/com/axzy/taqueria/ui/Components.kt:52", "app/src/main/java/com/axzy/taqueria/ui/Components.kt:61"],
     "riesgo": "Los umbrales de color y gestos (cobrar/cancelar) son reglas de UX críticas y solo se validan a ojo/E2E."},
    {"id": "H25", "area": "rol_y_alertas", "prioridad": "baja",
     "descripcion": "applyRole/manualHost/restartNetwork, Alerts.beep/vibrate y el permiso de red local de MainActivity sin pruebas.",
     "evidencia": ["app/src/main/java/com/axzy/taqueria/App.kt:37-60", "app/src/main/java/com/axzy/taqueria/App.kt:194-212", "app/src/main/java/com/axzy/taqueria/MainActivity.kt:22-30"],
     "riesgo": "Arranque del rol y avisos de orden nueva; se cubren parcialmente en E2E."},
]

emuladores = {
    "verificado_en": "2026-11-21T12:24:00-08:00",
    "adb_devices": [
        "emulator-5554  device  product:sdk_gphone16k_arm64 model:sdk_gphone16k_arm64 device:emu64a16k transport_id:6",
        "emulator-5556  device  product:sdk_gphone16k_arm64 model:sdk_gphone16k_arm64 device:emu64a16k transport_id:7",
    ],
    "adb_forward": [
        "emulator-5554 tcp:7777 tcp:7777",
        "emulator-5554 tcp:17777 tcp:7777",
    ],
    "dispositivos": {
        "emulator-5554": {
            "rol": "KITCHEN",
            "manual_host": None,
            "android": "17",
            "api": 37,
            "modelo": "sdk_gphone16k_arm64",
            "versionName": "1.0.1",
            "versionCode": 2,
            "lastUpdateTime": "2026-11-21 11:11:02",
            "firstInstallTime": "2026-11-20 21:52:10",
            "apk_md5": "6a5a289baff01e3a4d8a785a2b9a7f4c",
            "apk_bytes": 15420207,
            "apk_label": "Taquería",
            "ip_local": "10.0.2.15/24 (eth0)",
            "proceso_pid": 13137,
            "app_en_primer_plano": True,
            "primer_plano": "com.axzy.taqueria/.MainActivity",
            "store": {"products": 15, "modifierGroups": 5, "accounts": 0, "orders": 0, "counterDate": "", "counterLast": 0},
            "historial_jsonl": "no existe",
            "licencia_cache": {"status": "ACTIVE", "tenant": "Taquería El Fogón", "validUntilMs": 1795651200000,
                               "nextDueDate": "2026-11-21", "ultima_consulta": "sin conexión a 10.0.2.2:4100 (API local apagada), usa caché"},
        },
        "emulator-5556": {
            "rol": "WAITER",
            "manual_host": "10.0.2.2",
            "android": "17",
            "api": 37,
            "modelo": "sdk_gphone16k_arm64",
            "versionName": "1.0.1",
            "versionCode": 2,
            "lastUpdateTime": "2026-11-21 11:36:04",
            "firstInstallTime": "2026-11-20 21:52:18",
            "apk_md5": "6a5a289baff01e3a4d8a785a2b9a7f4c",
            "apk_bytes": 15420207,
            "apk_label": "Taquería",
            "ip_local": "10.0.2.17/24 (wlan0) y 10.0.2.15/24 (eth0)",
            "proceso_pid": 14412,
            "app_en_primer_plano": True,
            "primer_plano": "com.axzy.taqueria/.MainActivity",
            "store": "no aplica (la mesera no guarda datos de negocio)",
        },
    },
    "conexion_tcp": {
        "cocina_escucha": "tcp6 :7777 (0x1E61) estado 0A LISTEN, uid 10228",
        "mesera_conectada": "10.0.2.2:7777 (000...0202000A:1E61) estado 01 ESTABLISHED, uid 10228",
        "nota": "Enlace vivo al momento del snapshot; el tráfico llega por adb forward tcp:7777 de la Mac.",
    },
    "apk_instalada_vs_codigo_actual": {
        "version_coincide": True,
        "versionName": "1.0.1",
        "versionCode": 2,
        "gradle_properties": "taqueria.versionName=1.0.1, taqueria.versionCode=2 (gradle.properties:9-10)",
        "contenido_coincide": False,
        "instalada": {"md5": "6a5a289baff01e3a4d8a785a2b9a7f4c", "bytes": 15420207, "application_label": "Taquería",
                      "iconos": "res/drawable/ic_launcher_bg.xml + ic_launcher_fg.xml (vectores viejos), sin res/mipmap-*/ic_launcher_round"},
        "build_local": {"md5": "4ed325aa7bb20cd16ec9bd4c369c888b", "bytes": 16121376, "application_label": "Tacomanda",
                        "mtime": "2026-11-21 11:51", "iconos": "res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}-v4/*.png + ic_launcher_round.xml",
                        "nota": "gradle :app:assembleDebug quedó UP-TO-DATE a las 12:20 (evidence/recon/assemble.log), o sea que este APK corresponde al código actual"},
        "evidencia": ["evidence/unitarios/apk-instalada-badging.txt", "evidence/unitarios/apk-actual-badging.txt",
                      "app/src/main/AndroidManifest.xml:25 (android:label=\"Tacomanda\")"],
        "conclusion": "La APK instalada NO corresponde al código actual: mismo versionName/versionCode (1.0.1/2) pero distinto contenido (se anuncia como 'Taquería', iconos viejos y 15,420,207 bytes frente a 16,121,376 del build actual). Se instaló a las 11:11/11:36; el commit que la deja atrás es c81a83a (2026-11-21 12:01, 'renombrar a Tacomanda con iconos nuevos').",
    },
    "observaciones": [
        "Durante la auditoría el Lead ejecutó E2E en ambos emuladores: a las 12:19 el store de cocina tenía 3 cuentas/3 órdenes (evidence/recon/state/taqueria_5554.json); en el snapshot de las 12:24 aparece solo la semilla (0 cuentas, 0 órdenes) y sin historial.jsonl, porque el estado se reinició y la app se relanzó a las ~12:21.",
        "La app de mesera (5556) tiene un taqueria.json de semilla aunque no guarda datos de negocio: se crea igual porque comparte el mismo código y en algún momento se abrió con rol de cocina; no se escribe al operar como mesera.",
    ],
}

resumen = {
    "fecha": "2026-11-21T12:24:00-08:00",
    "fecha_utc": "2026-11-21T20:24:00Z",
    "repo": "~/DEV/AXZY/Taqueria",
    "paquete": "com.axzy.taqueria",
    "commit_auditado": "4086f5d (main, Merge pull request #3 fix/landing-capturas)",
    "comando": "./gradlew :app:testDebugUnitTest --console=plain --rerun-tasks",
    "log": "evidence/unitarios/gradle-test.log",
    "resultado_build": "BUILD SUCCESSFUL in 32s",
    "total": total,
    "pasados": passed,
    "fallidos": failed,
    "omitidos": skipped,
    "errores": 0,
    "duracion_total_ms": sum(r["ms"] for r in rows),
    "tests_por_clase": por_clase,
    "tests": rows,
    "cobertura_por_regla": cobertura,
    "huecos": huecos,
    "emuladores": emuladores,
}

OUT.write_text(json.dumps(resumen, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"escrito {OUT}  total={total} pasados={passed} fallidos={failed} omitidos={skipped} huecos={len(huecos)}")
