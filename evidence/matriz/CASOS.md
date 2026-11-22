# Matriz de casos de prueba — Tacomanda / Taquería

Generada a partir de `CONTEXTO.md`, `README.md`, el código de `app/src/main/java/com/axzy/taqueria/` (Model, OrderStore, License, QrPayload, KitchenServer, WaiterClient, App, MainActivity, `ui/*.kt`) y las **34 pruebas unitarias** de `app/src/test/java/com/axzy/taqueria/`.

- **Total de casos:** 184
- **Tipos:** 11 / 11 (todos presentes)
- **IDs únicos:** 184 (OK)
- **Estado de todos:** `pendiente` (la matriz es el plan; la ejecución la reporta el Lead)
- **Archivo de datos:** [`casos.json`](casos.json) — esquema exacto `{casos:[{id,tipo,modulo,titulo,prioridad,precondiciones,pasos,esperado,cobertura,dispositivo,regla,estado}]}`

> Nota de ejecución: los pasos usan los textos literales de la UI («Enviar a cocina», «✂ Dividir preparación», «Cobrar $X», «Entregada», «NUEVA/EDITADA»…) y los payloads TCP reales (`create`, `editOrder`, `orderStatus`, `cancelAccount`, `restoreAccount`, `pay`, `voidPayment`). Las pruebas que tocan licencia o archivos requieren respaldo previo; ver [Notas de ejecución](#notas-de-ejecución).

## Conteo por tipo

| Tipo | Nombre | Casos | Alta | Media | Baja |
|---|---|---:|---:|---:|---:|
| `FUN` | Funcional / camino feliz | 31 | 23 | 8 | 0 |
| `BOR` | Borde / límite | 19 | 1 | 10 | 8 |
| `VAL` | Validación de entrada | 26 | 10 | 14 | 2 |
| `ERR` | Error / fallo | 14 | 4 | 7 | 3 |
| `UND` | Deshacer / ventana de 30 s | 10 | 8 | 2 | 0 |
| `IDEM` | Idempotencia / reintentos / concurrencia | 11 | 5 | 6 | 0 |
| `PER` | Persistencia / recuperación / reinicio | 11 | 7 | 4 | 0 |
| `RED` | Red / conexión / reconexión / offline | 13 | 8 | 5 | 0 |
| `LIC` | Licencia / activación / QR / reloj | 27 | 16 | 9 | 2 |
| `UI` | UI / gestos / accesibilidad / rotación / densidad | 12 | 6 | 5 | 1 |
| `SEG` | Seguridad / datos / privacidad | 10 | 3 | 7 | 0 |
| | **Total** | **184** | **91** | **77** | **16** |

## Conteo por módulo

| Tipo | Módulo | Casos |
|---|---|---:|
| `FUN` | Captura | 10 |
| `FUN` | Catálogo | 2 |
| `FUN` | Cobro | 6 |
| `FUN` | Cocina | 3 |
| `FUN` | Conexión | 1 |
| `FUN` | Cuentas | 4 |
| `FUN` | Licencia | 1 |
| `FUN` | Roles | 4 |
| `BOR` | Captura | 7 |
| `BOR` | Catálogo | 2 |
| `BOR` | Cobro | 4 |
| `BOR` | Cocina | 1 |
| `BOR` | Cuentas | 1 |
| `BOR` | Licencia | 2 |
| `BOR` | QR | 2 |
| `VAL` | Captura | 8 |
| `VAL` | Catálogo | 4 |
| `VAL` | Cobro | 7 |
| `VAL` | Cuentas | 6 |
| `VAL` | Órdenes | 1 |
| `ERR` | Conexión | 3 |
| `ERR` | Cámara | 1 |
| `ERR` | Licencia | 5 |
| `ERR` | Persistencia | 2 |
| `ERR` | Protocolo TCP | 3 |
| `UND` | Deshacer | 10 |
| `IDEM` | Idempotencia | 11 |
| `PER` | Historial | 3 |
| `PER` | Persistencia | 8 |
| `RED` | Conexión | 5 |
| `RED` | Offline | 3 |
| `RED` | Permiso | 1 |
| `RED` | Reconexión | 3 |
| `RED` | mDNS | 1 |
| `LIC` | Licencia | 14 |
| `LIC` | QR | 13 |
| `UI` | Accesibilidad | 1 |
| `UI` | Densidad | 1 |
| `UI` | Deshacer | 1 |
| `UI` | Gestos | 3 |
| `UI` | Navegación | 1 |
| `UI` | Pantalla | 1 |
| `UI` | Rotación | 2 |
| `UI` | Tamaños | 1 |
| `UI` | Tema | 1 |
| `SEG` | Seguridad | 10 |

## Cobertura y dispositivo

| Cobertura | Casos | | Dispositivo | Casos |
|---|---:|---|---|---:|
| `unitaria` | 18 | | `cocina` | 72 |
| `e2e` | 125 | | `mesera` | 21 |
| `ambas` | 20 | | `ambos` | 73 |
| `manual` | 21 | | `JVM` | 18 |

`ambas` = el caso ya lo cubre una prueba unitaria **y** se puede ejecutar en los 2 teléfonos; `unitaria` = solo JVM (JUnit); `e2e` = solo en dispositivos; `manual` = requiere teléfono físico, cámara, cambio de ajustes o espera larga.

## Trazabilidad a las reglas de negocio 1-10

| Regla | Casos | IDs |
|---:|---:|---|
| 1 | 13 | Cantidad = suma de grupos; grupos idénticos se unen; 1–99: `FUN-CUENTAS-01`, `FUN-CUENTAS-03`, `FUN-CAPTURA-01`, `FUN-CAPTURA-04`, `FUN-CAPTURA-05`, `BOR-CAPTURA-01`, `BOR-CAPTURA-02`, `BOR-CAPTURA-03`, `BOR-CAPTURA-04`, `BOR-CAPTURA-05`, `BOR-CAPTURA-06`, `VAL-CAPTURA-03`, `VAL-CAPTURA-04` |
| 2 | 7 | Modificaciones validadas; exclusivas máximo una opción: `FUN-CAPTURA-03`, `FUN-CAPTURA-05`, `VAL-CAPTURA-01`, `VAL-CAPTURA-02`, `VAL-CATALOGO-02`, `VAL-CATALOGO-03`, `VAL-CATALOGO-04` |
| 3 | 12 | Reintentos con el mismo `clientKey` no duplican: `VAL-CAPTURA-06`, `ERR-PROTOCOLO-01`, `ERR-PROTOCOLO-03`, `ERR-CONEXION-01`, `ERR-CONEXION-03`, `UND-COBRO-04`, `IDEM-CAPTURA-01`, `IDEM-CAPTURA-02`, `IDEM-CAPTURA-03`, `IDEM-CAPTURA-04`, `IDEM-CAPTURA-05`, `IDEM-COBRO-01` |
| 4 | 6 | Cuenta nueva = siguiente número; pagar una no afecta a las demás: `FUN-CUENTAS-01`, `FUN-CUENTAS-02`, `FUN-CAPTURA-06`, `FUN-COBRO-04`, `VAL-CUENTAS-04`, `VAL-CUENTAS-05` |
| 5 | 18 | Pagos parciales, sin exceder saldo, efectivo con recibido y cambio, transferencia confirmada: `FUN-COBRO-02`, `FUN-COBRO-03`, `FUN-COBRO-05`, `FUN-COBRO-06`, `BOR-COBRO-01`, `BOR-COBRO-02`, `BOR-COBRO-03`, `BOR-COBRO-04`, `VAL-COBRO-01`, `VAL-COBRO-02`, `VAL-COBRO-03`, `VAL-COBRO-04`, `VAL-COBRO-05`, `VAL-COBRO-06`, `VAL-COBRO-07`, `UND-COBRO-03`, `IDEM-COBRO-01`, `IDEM-COBRO-02` |
| 6 | 4 | Cobrar = entregar (por defecto); deshacer el pago regresa a cocina: `FUN-COBRO-01`, `FUN-COBRO-02`, `FUN-COBRO-05`, `UND-COBRO-01` |
| 7 | 10 | Editar solo PENDING; versión anterior en `revisions`; lo agotado ya pedido se conserva: `FUN-CAPTURA-09`, `FUN-CAPTURA-10`, `FUN-CATALOGO-02`, `VAL-CAPTURA-05`, `VAL-CAPTURA-07`, `VAL-CAPTURA-08`, `VAL-ORDENES-01`, `IDEM-ORDENES-01`, `IDEM-CAPTURA-06`, `IDEM-CAPTURA-07` |
| 8 | 15 | Deshacer (cancelar/entregar/pagar) dentro de 30 s; barra de 6 s: `VAL-ORDENES-01`, `VAL-CUENTAS-01`, `VAL-CUENTAS-02`, `VAL-CUENTAS-03`, `UND-CUENTAS-01`, `UND-CUENTAS-02`, `UND-CUENTAS-03`, `UND-CUENTAS-04`, `UND-ORDENES-01`, `UND-ORDENES-02`, `UND-COBRO-01`, `UND-COBRO-02`, `UND-COBRO-03`, `UND-COBRO-04`, `IDEM-ORDENES-01` |
| 9 | 10 | Cuenta cerrada = sin pendientes y saldo 0; historial tras 30 s; nada se borra: `FUN-COBRO-01`, `FUN-COBRO-06`, `VAL-CUENTAS-04`, `VAL-COBRO-06`, `UND-CUENTAS-04`, `PER-PERSISTENCIA-03`, `PER-HISTORIAL-01`, `PER-HISTORIAL-02`, `PER-HISTORIAL-03`, `SEG-DATOS-01` |
| 10 | 7 | Cancelar cuenta cancela lo pendiente; lo entregado se sigue cobrando: `FUN-COBRO-06`, `VAL-CUENTAS-01`, `VAL-CUENTAS-03`, `UND-CUENTAS-01`, `IDEM-CUENTAS-01`, `PER-HISTORIAL-02`, `PER-HISTORIAL-03` |

## Trazabilidad a las 34 pruebas unitarias existentes

Cada fila indica qué casos de la matriz están cubiertos por ese test (además de servir como verificación JVM del comportamiento).

| # | Clase | Test | Casos que lo cubren |
|---:|---|---|---|
| 1 | `OrderStoreTest` | `cuatro tacos de asada se dividen en dos grupos dentro de una sola orden` | `FUN-CAPTURA-04` |
| 2 | `OrderStoreTest` | `grupos identicos se unen y las modificaciones combinables se aceptan` | `FUN-CAPTURA-05` |
| 3 | `OrderStoreTest` | `opciones exclusivas contradictorias o ajenas se rechazan` | `VAL-CAPTURA-01`, `VAL-CAPTURA-02`, `VAL-CAPTURA-03` |
| 4 | `OrderStoreTest` | `reintento con la misma llave no duplica` | `IDEM-CAPTURA-01` |
| 5 | `OrderStoreTest` | `cuentas separadas en la misma mesa con saldos independientes` | `FUN-CUENTAS-02` |
| 6 | `OrderStoreTest` | `pago parcial en efectivo y transferencia no cambia la preparacion` | `FUN-COBRO-05` |
| 7 | `OrderStoreTest` | `pagar una cuenta no paga las demas de la mesa` | `FUN-COBRO-04` |
| 8 | `OrderStoreTest` | `cancelar cuenta se puede deshacer solo dentro del periodo` | `UND-CUENTAS-03` |
| 9 | `OrderStoreTest` | `deshacer pago lo anula sin borrarlo` | `UND-COBRO-02` |
| 10 | `OrderStoreTest` | `cuenta cerrada pasa al historial despues del periodo de deshacer` | `UND-CUENTAS-04`, `PER-HISTORIAL-01` |
| 11 | `OrderStoreTest` | `editar 4 de asada a 2 asada 1 adobada 1 chorizo conserva la version anterior` | `FUN-CAPTURA-09` |
| 12 | `OrderStoreTest` | `no se edita una orden entregada y se respeta lo agotado ya pedido` | `VAL-CAPTURA-07` |
| 13 | `OrderStoreTest` | `cobrar en efectivo guarda con cuanto pagaron y entrega la orden` | `FUN-COBRO-02`, `VAL-COBRO-04`, `UND-COBRO-01` |
| 14 | `OrderStoreTest` | `los datos sobreviven a reiniciar` | `PER-PERSISTENCIA-01` |
| 15 | `LicenseTest` | `sinCodigoNoEstaActivada` | `LIC-LICENCIA-01` |
| 16 | `LicenseTest` | `activaFuncionaSinInternetHastaSuFecha` | `LIC-LICENCIA-02` |
| 17 | `LicenseTest` | `avisaCuandoEstaPorVenecer` | `LIC-LICENCIA-03` |
| 18 | `LicenseTest` | `suspendidaSigueBloqueadaSinInternet` | `LIC-LICENCIA-04` |
| 19 | `LicenseTest` | `revocadaBloquea` | `LIC-LICENCIA-05` |
| 20 | `LicenseTest` | `atrasarElRelojNoAlargaLaLicencia` | `LIC-LICENCIA-06` |
| 21 | `LicenseTest` | `usaLaHoraDelServidorSiElTelefonoVaAtrasado` | `LIC-LICENCIA-07` |
| 22 | `QrPayloadTest` | `jsonDelContratoConServidorYProducto` | `LIC-QR-01` |
| 23 | `QrPayloadTest` | `jsonSinUrlUsaElServidorCompilado` | `LIC-QR-02` |
| 24 | `QrPayloadTest` | `jsonSinProductoEsValido` | `LIC-QR-03` |
| 25 | `QrPayloadTest` | `jsonConLlavesExtraLasIgnora` | `LIC-QR-04` |
| 26 | `QrPayloadTest` | `quitaLaDiagonalFinalDeLaUrl` | `LIC-QR-05` |
| 27 | `QrPayloadTest` | `productoAjenoDaMensajeClaro` | `LIC-QR-06` |
| 28 | `QrPayloadTest` | `versionDistintaSeRechaza` | `LIC-QR-07` |
| 29 | `QrPayloadTest` | `versionFaltanteSeRechaza` | `LIC-QR-07` |
| 30 | `QrPayloadTest` | `urlInvalidaSeRechaza` | `LIC-QR-08` |
| 31 | `QrPayloadTest` | `codigoSueltoAxcSeAcepta` | `LIC-QR-09` |
| 32 | `QrPayloadTest` | `codigoSueltoRecortaEspacios` | `LIC-QR-09` |
| 33 | `QrPayloadTest` | `basuraSeRechaza` | `LIC-QR-10` |
| 34 | `QrPayloadTest` | `codigoVacioEnJsonSeRechaza` | `LIC-QR-11` |

## Casos no ejecutables en los 2 emuladores (requieren teléfono físico, cámara o ajustes)

| Caso | Motivo | Alternativa |
|---|---|---|
| `RED-MDNS-01` | El multicast mDNS no cruza el NAT de los emuladores; mDNS no descubre la cocina entre 2 AVD. | Usar los 2 teléfonos físicos en el mismo Wi-Fi/hotspot, o el `adb forward tcp:7777` + IP manual `10.0.2.2` para el resto de casos. |
| `LIC-QR-12`, `LIC-QR-13` (escanear QR con cámara) | La cámara virtual del emulador no enfoca un QR real de forma fiable, y `LIC-QR-13` necesita un QR con URL http para fijar `apiBase`. | Teléfono físico, emulador con webcam apuntando a la pantalla, o el respaldo manual «Código de activación» (no permite fijar la URL del servidor). |
| `BOR-QR-01`, `BOR-QR-02` (límites 200/500 caracteres) | Requieren generar y **escanear** QRs de longitudes extremas. | Generador de QR + teléfono físico; en emulador queda cubierto el parser por `QrPayloadTest` salvo los topes. |
| `ERR-LICENCIA-05` (release sin servidor) | En los emuladores está instalada la APK debug. | Compilar `:app:assembleRelease` e instalar el APK release. |
| `RED-PERMISO-01` (permiso de red local) | `ACCESS_LOCAL_NETWORK` solo existe en Android 17+/API 37; si el AVD es menor, el sistema no pide permiso. | AVD API 37+ o teléfono con Android 17+. |
| `UI-DENSIDAD-01`, `UI-TAMANOS-01`, `UI-TEMA-01`, `UI-PANTALLA-01`, `UI-ACCESIBILIDAD-01` | Requieren cambiar ajustes del sistema (fuente/tamaño/tema/TalkBack) o un teléfono/tablet distinto. | `adb shell settings put system font_scale 1.5`, cambiar la densidad del AVD, o hacerlo a mano en Ajustes. |
| `BOR-COCINA-01` (umbrales 5/10/15 min) | Implica esperar ~15 min reales; `createdAt` no se puede inyectar por TCP. | Dejarlo correr en segundo plano o probar en cocina con una orden real; no afecta la funcionalidad. |
| `PER-PERSISTENCIA-04` (guardado atómico) | Requiere cortes de energía/`force-stop` en momentos aleatorios; no es determinista. | Repetir force-stop durante ráfagas de cobro y verificar que `taqueria.json` siempre decodifica. |
| `BOR-CUENTAS-01` (contador diario) | Necesita cambiar la fecha del dispositivo o editar `taqueria.json` (root/emulador). | Editar el JSON con la app detenida; en emulador AOSP con `adb root` también se puede mover el reloj (ojo: afecta la licencia). |
| `SEG-RESPALDO-01`, `SEG-LICENCIA-01`, `SEG-TRANSPORTE-01`, `SEG-MDNS-01`, `SEG-CAMARA-01` | Requieren `adb backup`/root, logcat, otra máquina en la LAN, build release o cámara. | Ejecutables con la Mac y el emulador usando `adb`; se marcan `manual` por no ser un tap en la app. |

## Casos e2e de licencia que requieren un mock de Axzy Central

Los casos `LIC-LICENCIA-08` a `LIC-LICENCIA-14`, `BOR-LICENCIA-01`, `BOR-LICENCIA-02` y `LIC-QR-13` necesitan un servidor HTTP con el contrato `POST /activate` y `GET /license` (status, tenant.name, validUntil, serverTime). En debug la URL compilada es `http://10.0.2.2:4100/api/v1` y se permite http. También se puede activar con un QR que traiga `u`. **Tocar la licencia actual de los emuladores es destructivo:** respaldar `shared_prefs/license.xml` y restaurarlo al terminar, o reactivar contra el mock.

## Casos destructivos (respaldar estado antes de ejecutarlos)

Respaldar `files/taqueria.json`, `files/historial.jsonl` y `shared_prefs/*.xml` del teléfono de cocina antes de: casos `ERR-PERSISTENCIA-01/02` (corrupción), `PER-PERSISTENCIA-02/03` (borrar datos/archivo), `RED-PERMISO-01` (clear de permisos), `BOR-CUENTAS-01` (editar JSON/reloj), `LIC-LICENCIA-08..14` (licencia), `LIC-QR-12/13` (activación) y `SEG-*` que usan `pm clear` o `adb backup`. Los casos de cobro/cancelación normales dejan datos en cocina: se recomienda correrlos en un emulador de pruebas o limpiar al final.

## Estado observado en los emuladores al generar la matriz

De `evidence/recon/` (2026-11-21 12:18):

- **Cocina** (`evidence/recon/cocina_actual.png`): licencia **activa** (se ve el monitor), IP `10.0.2.15`, **1 mesera conectada**, 3 órdenes pendientes: Mesa 7 · Cuenta 1 · #1 (11:29), Mesa 1 · Cuenta 1 · #2 (11:34) y Mesa 4 · Cuenta 1 · #3 (11:38, 2 asada con cebolla + 2 sin cebolla = el caso de división 4→2+2 ya ejecutado).
- **Mesera** (`evidence/recon/mesero_actual.png`): detalle «Mesa 4 · Cuenta 1», Total $80.00, Pagado $50.00, Saldo $30.00 (chip «Pago parcial»), Orden #3 con dos grupos y pago «Efectivo · 11:39 $50.00».
- Los ids de producto/cuenta/orden son **UUID aleatorios por instalación**: los casos TCP deben leerlos del `state` inicial o de `taqueria.json`, no reutilizar los de un respaldo.

## Notas de ejecución

- **TCP:** `adb -s <cocina> forward tcp:7777 tcp:7777` y `nc 127.0.0.1 7777`; una línea JSON por mensaje; el servidor manda `state` al conectar y `ping` cada 10 s. Los payloads usan el discriminador `type` (`create`, `editOrder`, `orderStatus`, `cancelAccount`, `restoreAccount`, `pay`, `voidPayment`).
- **IP manual de la mesera:** `10.0.2.2` (con el `adb forward` de arriba) o la IP que muestra la cocina; mDNS entre AVD no funciona.
- **Para pruebas con servidor mudo:** `adb forward --remove tcp:7777` y `nc -l 7777` en la Mac (la mesera usa `10.0.2.2`).
- **Archivos de cocina:** `adb -s <cocina> shell run-as com.axzy.taqueria ls files shared_prefs`; el paquete es `com.axzy.taqueria`.
- **Regla de oro:** cada caso deja una salida verificable (texto en pantalla, toast, línea de `state`/`reply`, archivo o logcat). Si algo no coincide, se reporta como fallo con el id del caso.

## Anexo · todos los casos

### FUN — Funcional / camino feliz (31)

| ID | Título | Prioridad | Cobertura | Dispositivo | Módulo | Regla |
|---|---|---|---|---|---|---|
| `FUN-ROLES-01` | Primer arranque en el teléfono de la mesera: elegir Mesero | Alta | e2e | ambos | Roles | n/a |
| `FUN-ROLES-02` | Primer arranque en cocina: la licencia bloquea el monitor | Alta | e2e | cocina | Roles | n/a |
| `FUN-ROLES-03` | Cambiar rol de cocina a mesera conserva órdenes y menú | Alta | e2e | cocina | Roles | n/a |
| `FUN-ROLES-04` | Volver a cocina desde el engrane de la mesera | Media | e2e | mesera | Roles | n/a |
| `FUN-CUENTAS-01` | Nueva orden en Mesa 4 crea la Cuenta 1 y llega a cocina | Alta | e2e | ambos | Cuentas | regla 1, regla 4 |
| `FUN-CUENTAS-02` | Segunda cuenta de la misma mesa con «+ Cuenta» | Alta | ambas | ambos | Cuentas | regla 4 |
| `FUN-CUENTAS-03` | Agregar productos a una cuenta abierta desde el detalle | Alta | e2e | ambos | Cuentas | regla 1 |
| `FUN-CUENTAS-04` | Varias mesas se ordenan por la cuenta más antigua | Media | e2e | mesera | Cuentas | n/a |
| `FUN-CAPTURA-01` | Un toque suma +1 y el total se calcula en vivo | Alta | e2e | ambos | Captura | regla 1 |
| `FUN-CAPTURA-02` | Los chips de categoría desplazan la rejilla a su sección | Media | e2e | mesera | Captura | n/a |
| `FUN-CAPTURA-03` | Mantener presionado abre el editor con modificaciones y nota | Alta | e2e | ambos | Captura | regla 2 |
| `FUN-CAPTURA-04` | ✂ Dividir preparación: 4 asada → 2+2 en una sola orden | Alta | ambas | ambos | Captura | regla 1 |
| `FUN-CAPTURA-05` | Grupos idénticos se unen y Extras combinables se aceptan (payload TCP) | Media | ambas | ambos | Captura | regla 1, regla 2 |
| `FUN-CAPTURA-06` | Mesa «Llevar» se muestra como «Para llevar» | Media | e2e | ambos | Captura | regla 4 |
| `FUN-CAPTURA-07` | Mesa personalizada con «Otra…» | Media | e2e | ambos | Captura | n/a |
| `FUN-CAPTURA-08` | Descartar una captura pide confirmación y no crea nada | Media | e2e | mesera | Captura | n/a |
| `FUN-CAPTURA-09` | Editar una orden: 4 asada → 2 asada + 1 adobada + 1 chorizo | Alta | ambas | ambos | Captura | regla 7 |
| `FUN-CAPTURA-10` | Editar quita un grupo y agrega otro: cocina muestra «QUITAR: …» tachado | Alta | e2e | ambos | Captura | regla 7 |
| `FUN-COCINA-01` | Ticket de cocina con todos los datos de la orden | Alta | e2e | cocina | Cocina | n/a |
| `FUN-COCINA-02` | Orden nueva: suena, vibra y muestra «● NUEVA» durante 1 min | Alta | e2e | cocina | Cocina | n/a |
| `FUN-COCINA-03` | Varias órdenes de una mesa en un solo ticket, la más antigua primero | Alta | e2e | cocina | Cocina | n/a |
| `FUN-COBRO-01` | Cobro exacto en efectivo cierra la cuenta y quita el ticket | Alta | e2e | ambos | Cobro | regla 6, regla 9 |
| `FUN-COBRO-02` | Cobro con cambio: total $80 pagado con $100 → «Cambio $20.00» | Alta | ambas | ambos | Cobro | regla 5, regla 6 |
| `FUN-COBRO-03` | Transferencia exige confirmar que el abono llegó | Alta | e2e | ambos | Cobro | regla 5 |
| `FUN-COBRO-04` | Pagar una cuenta no paga las demás de la misma mesa | Alta | ambas | ambos | Cobro | regla 4 |
| `FUN-COBRO-05` | Pago parcial efectivo + transferencia sin sacar la orden de cocina | Alta | ambas | ambos | Cobro | regla 5, regla 6 |
| `FUN-COBRO-06` | Devolución de un saldo negativo | Media | e2e | ambos | Cobro | regla 5, regla 9, regla 10 |
| `FUN-CATALOGO-01` | Alta de producto nuevo disponible para la mesera | Alta | e2e | ambos | Catálogo | n/a |
| `FUN-CATALOGO-02` | Apagar un producto lo marca agotado y lo quita de la captura | Alta | e2e | ambos | Catálogo | regla 7 |
| `FUN-CONEXION-01` | Conexión de los 2 teléfonos por IP manual y sincronización en vivo | Alta | e2e | ambos | Conexión | n/a |
| `FUN-LICENCIA-01` | Activación manual con un código válido deja la cocina operativa | Alta | e2e | cocina | Licencia | n/a |

### BOR — Borde / límite (19)

| ID | Título | Prioridad | Cobertura | Dispositivo | Módulo | Regla |
|---|---|---|---|---|---|---|
| `BOR-CAPTURA-01` | Tope de 99 piezas por grupo (UI y TCP) | Media | e2e | ambos | Captura | regla 1 |
| `BOR-CAPTURA-02` | Bajar de 1 quita el grupo y deselecciona el mosaico | Media | e2e | mesera | Captura | regla 1 |
| `BOR-CAPTURA-03` | Dividir 5 → 3+2 sin cambiar el total | Media | e2e | mesera | Captura | regla 1 |
| `BOR-CAPTURA-04` | «✂ Dividir preparación» deshabilitado con todos los grupos en 1 | Media | e2e | mesera | Captura | regla 1 |
| `BOR-CAPTURA-05` | Dividir repetido parte el mayor: 4 → 2+2 → 1+2+1 | Baja | e2e | mesera | Captura | regla 1 |
| `BOR-CAPTURA-06` | Máximo 60 grupos por orden; el 61 se rechaza | Baja | e2e | cocina | Captura | regla 1 |
| `BOR-CAPTURA-07` | Nota de 100 caracteres se conserva; 101 se rechaza por TCP | Media | ambas | ambos | Captura | n/a |
| `BOR-CATALOGO-01` | Precio 0 y tope de precio del catálogo | Media | e2e | cocina | Catálogo | n/a |
| `BOR-CATALOGO-02` | Límites de texto del catálogo y de la mesa personalizada | Baja | e2e | ambos | Catálogo | n/a |
| `BOR-COBRO-01` | Importe menor al saldo muestra «Quedará pendiente» | Media | e2e | ambos | Cobro | regla 5 |
| `BOR-COBRO-02` | «Exacto» deja recibido igual al importe y cambio $0.00 | Media | e2e | ambos | Cobro | regla 5 |
| `BOR-COBRO-03` | Recibido un centavo menos que el importe: «Falta $0.01» | Media | e2e | mesera | Cobro | regla 5 |
| `BOR-COBRO-04` | Importe igual al saldo se acepta; un centavo más se rechaza | Alta | e2e | ambos | Cobro | regla 5 |
| `BOR-COCINA-01` | Umbrales de color y pulso por tiempo de espera | Baja | manual | cocina | Cocina | n/a |
| `BOR-CUENTAS-01` | El número de orden reinicia en 1 cada día | Baja | manual | cocina | Cuentas | n/a |
| `BOR-LICENCIA-01` | Activa hasta validUntil exacto y vence justo después | Media | e2e | cocina | Licencia | n/a |
| `BOR-LICENCIA-02` | Aviso de vencimiento justo en los 3 días | Baja | e2e | cocina | Licencia | n/a |
| `BOR-QR-01` | Código suelto de 200 caracteres se acepta y 201 se rechaza | Baja | manual | cocina | QR | n/a |
| `BOR-QR-02` | URL de servidor de 500 caracteres se acepta y 501 se rechaza | Baja | manual | cocina | QR | n/a |

### VAL — Validación de entrada (26)

| ID | Título | Prioridad | Cobertura | Dispositivo | Módulo | Regla |
|---|---|---|---|---|---|---|
| `VAL-CAPTURA-01` | Dos opciones del mismo tipo exclusivo se rechazan | Alta | ambas | ambos | Captura | regla 2 |
| `VAL-CAPTURA-02` | Una modificación que no aplica al producto se rechaza | Alta | ambas | ambos | Captura | regla 2 |
| `VAL-CAPTURA-03` | Cantidad 0 o fuera de rango se rechaza | Alta | ambas | ambos | Captura | regla 1 |
| `VAL-CAPTURA-04` | Orden sin productos se rechaza | Media | e2e | cocina | Captura | regla 1 |
| `VAL-CAPTURA-05` | Producto inexistente o agotado sin pedido previo se rechaza | Alta | e2e | ambos | Captura | regla 7 |
| `VAL-CAPTURA-06` | Llave de idempotencia vacía o de más de 64 caracteres | Media | e2e | cocina | Captura | regla 3 |
| `VAL-CAPTURA-07` | No se puede editar una orden que ya salió de cocina | Alta | ambas | ambos | Captura | regla 7 |
| `VAL-CAPTURA-08` | Editar una orden que ya no existe | Media | e2e | cocina | Captura | regla 7 |
| `VAL-ORDENES-01` | Transiciones de estado de orden inválidas | Alta | e2e | ambos | Órdenes | regla 7, regla 8 |
| `VAL-CUENTAS-01` | Cancelar una cuenta que ya estaba cancelada | Media | e2e | cocina | Cuentas | regla 8, regla 10 |
| `VAL-CUENTAS-02` | Restaurar una cuenta que no está cancelada | Media | e2e | cocina | Cuentas | regla 8 |
| `VAL-CUENTAS-03` | Agregar una orden a una cuenta cancelada | Alta | e2e | cocina | Cuentas | regla 8, regla 10 |
| `VAL-CUENTAS-04` | Agregar una orden a una cuenta ya cerrada | Alta | e2e | cocina | Cuentas | regla 4, regla 9 |
| `VAL-CUENTAS-05` | Reutilizar una cuenta de otra mesa | Media | e2e | cocina | Cuentas | regla 4 |
| `VAL-CUENTAS-06` | Mesa vacía o de más de 20 caracteres | Media | e2e | cocina | Cuentas | n/a |
| `VAL-COBRO-01` | No se acepta pagar más que el saldo | Alta | e2e | ambos | Cobro | regla 5 |
| `VAL-COBRO-02` | La devolución no puede exceder lo que se debe regresar | Media | e2e | ambos | Cobro | regla 5 |
| `VAL-COBRO-03` | El cambio solo aplica a cobros en efectivo | Media | e2e | cocina | Cobro | regla 5 |
| `VAL-COBRO-04` | Efectivo recibido menor al importe | Alta | ambas | ambos | Cobro | regla 5 |
| `VAL-COBRO-05` | Importe recibido fuera del máximo razonable | Baja | e2e | cocina | Cobro | regla 5 |
| `VAL-COBRO-06` | Pago sobre una cuenta ya cerrada | Media | e2e | cocina | Cobro | regla 5, regla 9 |
| `VAL-COBRO-07` | Importe de pago en cero | Media | e2e | ambos | Cobro | regla 5 |
| `VAL-CATALOGO-01` | Producto sin nombre o con precio inválido no se guarda | Media | e2e | cocina | Catálogo | n/a |
| `VAL-CATALOGO-02` | Tipo de modificación sin nombre o sin opciones | Media | e2e | cocina | Catálogo | regla 2 |
| `VAL-CATALOGO-03` | Una opción no puede existir en dos tipos de modificación | Media | e2e | cocina | Catálogo | regla 2 |
| `VAL-CATALOGO-04` | Opción de modificación de más de 30 caracteres | Baja | e2e | cocina | Catálogo | regla 2 |

### ERR — Error / fallo (14)

| ID | Título | Prioridad | Cobertura | Dispositivo | Módulo | Regla |
|---|---|---|---|---|---|---|
| `ERR-PROTOCOLO-01` | JSON inválido por TCP se ignora y la conexión sigue viva | Media | e2e | cocina | Protocolo TCP | regla 3 |
| `ERR-PROTOCOLO-02` | Mensaje de más de 64 KB corta la conexión sin tumbar el servidor | Baja | e2e | cocina | Protocolo TCP | n/a |
| `ERR-PROTOCOLO-03` | Tipo de mensaje o enum desconocido se ignora | Media | e2e | cocina | Protocolo TCP | regla 3 |
| `ERR-CONEXION-01` | Sin respuesta de cocina: la mesera corta a los 8 s y conserva el borrador | Media | e2e | mesera | Conexión | regla 3 |
| `ERR-CONEXION-02` | Puerto 7777 ocupado: la cocina muestra el error y no acepta meseras | Media | e2e | cocina | Conexión | n/a |
| `ERR-CONEXION-03` | Se pierde la conexión durante el envío: error, borrador intacto | Alta | e2e | ambos | Conexión | regla 3 |
| `ERR-PERSISTENCIA-01` | taqueria.json corrupto se respalda y arranca con menú semilla | Alta | e2e | cocina | Persistencia | n/a |
| `ERR-PERSISTENCIA-02` | taqueria.json vacío o truncado se trata como corrupto | Media | e2e | cocina | Persistencia | n/a |
| `ERR-LICENCIA-01` | Activación con código inválido o revocado (401) | Alta | e2e | cocina | Licencia | n/a |
| `ERR-LICENCIA-02` | Activación sin internet | Alta | e2e | cocina | Licencia | n/a |
| `ERR-LICENCIA-03` | El servidor de licencias responde 500 | Media | e2e | cocina | Licencia | n/a |
| `ERR-LICENCIA-04` | Respuesta de licencia que no es JSON válido | Baja | e2e | cocina | Licencia | n/a |
| `ERR-LICENCIA-05` | Build release sin servidor de licencias configurado | Baja | manual | cocina | Licencia | n/a |
| `ERR-CAMARA-01` | Permiso de cámara denegado: la activación cae al código manual | Media | e2e | cocina | Cámara | n/a |

### UND — Deshacer / ventana de 30 s (10)

| ID | Título | Prioridad | Cobertura | Dispositivo | Módulo | Regla |
|---|---|---|---|---|---|---|
| `UND-CUENTAS-01` | Deshacer la cancelación de una cuenta desde la barra | Alta | e2e | ambos | Deshacer | regla 8, regla 10 |
| `UND-CUENTAS-02` | La barra de deshacer dura 6 s; la ventana del servidor 30 s | Alta | e2e | ambos | Deshacer | regla 8 |
| `UND-CUENTAS-03` | Ventana de 30 s: a los 29 s se puede restaurar y a los 31 s no | Alta | ambas | ambos | Deshacer | regla 8 |
| `UND-CUENTAS-04` | Tras 30 s la cuenta se archiva y ya no se puede deshacer | Alta | ambas | cocina | Deshacer | regla 8, regla 9 |
| `UND-ORDENES-01` | Deshacer la cancelación de una sola orden | Alta | e2e | ambos | Deshacer | regla 8 |
| `UND-ORDENES-02` | Deshacer la entrega de una orden | Alta | e2e | ambos | Deshacer | regla 8 |
| `UND-COBRO-01` | Deshacer un pago: primero se muestra el cambio y luego se anula | Alta | ambas | ambos | Deshacer | regla 6, regla 8 |
| `UND-COBRO-02` | Deshacer un pago lo anula sin borrarlo (renglón tachado) | Alta | ambas | ambos | Deshacer | regla 8 |
| `UND-COBRO-03` | Deshacer una devolución | Media | e2e | ambos | Deshacer | regla 5, regla 8 |
| `UND-COBRO-04` | Deshacer dos veces el mismo pago no duplica ni falla | Media | e2e | cocina | Deshacer | regla 3, regla 8 |

### IDEM — Idempotencia / reintentos / concurrencia (11)

| ID | Título | Prioridad | Cobertura | Dispositivo | Módulo | Regla |
|---|---|---|---|---|---|---|
| `IDEM-CAPTURA-01` | Reintento con la misma llave no duplica la orden | Alta | ambas | ambos | Idempotencia | regla 3 |
| `IDEM-CAPTURA-02` | Una llave distinta crea una orden nueva con el mismo contenido | Media | e2e | cocina | Idempotencia | regla 3 |
| `IDEM-CAPTURA-03` | Dos meseros envían a la vez la misma llave y solo se crea una orden | Alta | e2e | ambos | Idempotencia | regla 3 |
| `IDEM-CAPTURA-04` | Reintento automático del mesero tras reconectar no duplica | Alta | e2e | ambos | Idempotencia | regla 3 |
| `IDEM-CAPTURA-05` | Cada cambio del borrador cambia la llave (dos pedidos iguales a propósito) | Media | e2e | mesera | Idempotencia | regla 3 |
| `IDEM-COBRO-01` | El reintento del mismo pago no duplica el movimiento | Alta | e2e | ambos | Idempotencia | regla 3, regla 5 |
| `IDEM-COBRO-02` | Dos pagos simultáneos no exceden el saldo | Alta | e2e | cocina | Idempotencia | regla 5 |
| `IDEM-ORDENES-01` | Marcar entregada dos veces | Media | e2e | cocina | Idempotencia | regla 7, regla 8 |
| `IDEM-CUENTAS-01` | Cancelar dos veces la misma cuenta | Media | e2e | cocina | Idempotencia | regla 10 |
| `IDEM-CAPTURA-06` | Guardar la edición sin cambios no crea revisión nueva | Media | e2e | cocina | Idempotencia | regla 7 |
| `IDEM-CAPTURA-07` | Dos ediciones reales dejan dos revisiones en orden | Media | e2e | cocina | Idempotencia | regla 7 |

### PER — Persistencia / recuperación / reinicio (11)

| ID | Título | Prioridad | Cobertura | Dispositivo | Módulo | Regla |
|---|---|---|---|---|---|---|
| `PER-PERSISTENCIA-01` | Reiniciar la app de cocina conserva órdenes, cuentas y menú | Alta | ambas | cocina | Persistencia | n/a |
| `PER-PERSISTENCIA-02` | Primer arranque sin taqueria.json siembra el menú y crea el archivo | Alta | e2e | cocina | Persistencia | n/a |
| `PER-PERSISTENCIA-03` | Si falta taqueria.json se vuelve a sembrar sin tocar el historial | Media | e2e | cocina | Persistencia | regla 9 |
| `PER-PERSISTENCIA-04` | Guardado atómico: taqueria.json nunca queda a medias | Alta | manual | cocina | Persistencia | n/a |
| `PER-HISTORIAL-01` | La cuenta cerrada pasa a historial.jsonl a los 30 s | Alta | ambas | cocina | Historial | regla 9 |
| `PER-HISTORIAL-02` | El historial conserva cancelaciones y pagos de una cuenta cancelada | Alta | e2e | cocina | Historial | regla 9, regla 10 |
| `PER-HISTORIAL-03` | Una cuenta cancelada sin pagos también se archiva | Media | e2e | cocina | Historial | regla 9, regla 10 |
| `PER-PERSISTENCIA-05` | La mesera no guarda datos de negocio: reabrirla resincroniza | Alta | e2e | mesera | Persistencia | n/a |
| `PER-PERSISTENCIA-06` | La cache de licencia sobrevive al reinicio y funciona sin internet | Alta | e2e | cocina | Persistencia | n/a |
| `PER-PERSISTENCIA-07` | Acentos, emoji y notas sobreviven al reinicio | Media | e2e | ambos | Persistencia | n/a |
| `PER-PERSISTENCIA-08` | Una actualización del APK con la misma firma conserva los datos | Media | manual | cocina | Persistencia | n/a |

### RED — Red / conexión / reconexión / offline (13)

| ID | Título | Prioridad | Cobertura | Dispositivo | Módulo | Regla |
|---|---|---|---|---|---|---|
| `RED-CONEXION-01` | Conexión de mesera a cocina con IP manual y adb forward | Alta | e2e | ambos | Conexión | n/a |
| `RED-CONEXION-02` | Formato de IP manual: host, host:puerto y puerto alterno | Media | e2e | mesera | Conexión | n/a |
| `RED-RECONEXION-01` | Al detener la cocina, la mesera detecta el enlace muerto en ≤25 s | Alta | e2e | ambos | Reconexión | n/a |
| `RED-RECONEXION-02` | Reconexión automática y resincronización del estado | Alta | e2e | ambos | Reconexión | n/a |
| `RED-RECONEXION-03` | Servidor que acepta pero no envía nada: timeout de 25 s | Media | e2e | mesera | Reconexión | n/a |
| `RED-OFFLINE-01` | Sin conexión la UI bloquea las operaciones | Alta | e2e | mesera | Offline | n/a |
| `RED-OFFLINE-02` | Lo creado durante el corte aparece al reconectar | Alta | e2e | ambos | Offline | n/a |
| `RED-OFFLINE-03` | Wi-Fi del local sin internet: se mantiene la conexión local | Alta | e2e | ambos | Offline | n/a |
| `RED-CONEXION-03` | Dos meseras conectadas a la vez | Media | e2e | ambos | Conexión | n/a |
| `RED-CONEXION-04` | Cambiar de rol detiene el servidor y libera el puerto 7777 | Media | e2e | ambos | Conexión | n/a |
| `RED-PERMISO-01` | Permiso de red local en Android 17+: denegado y concedido | Alta | manual | ambos | Permiso | n/a |
| `RED-MDNS-01` | Descubrimiento automático de la cocina por mDNS | Alta | manual | ambos | mDNS | n/a |
| `RED-CONEXION-05` | IP equivocada: «Conectando…» y corrección sin reiniciar | Media | e2e | mesera | Conexión | n/a |

### LIC — Licencia / activación / QR / reloj (27)

| ID | Título | Prioridad | Cobertura | Dispositivo | Módulo | Regla |
|---|---|---|---|---|---|---|
| `LIC-LICENCIA-01` | Sin código guardado la cocina no está activada | Alta | unitaria | JVM | Licencia | n/a |
| `LIC-LICENCIA-02` | Activa funciona sin internet hasta su fecha | Alta | unitaria | JVM | Licencia | n/a |
| `LIC-LICENCIA-03` | Aviso cuando la licencia está por vencer | Alta | unitaria | JVM | Licencia | n/a |
| `LIC-LICENCIA-04` | Suspendida sigue bloqueada sin internet | Alta | unitaria | JVM | Licencia | n/a |
| `LIC-LICENCIA-05` | Código revocado bloquea la cocina | Alta | unitaria | JVM | Licencia | n/a |
| `LIC-LICENCIA-06` | Atrasar el reloj del teléfono no alarga la licencia | Alta | unitaria | JVM | Licencia | n/a |
| `LIC-LICENCIA-07` | Si el teléfono va atrasado se usa la hora del servidor | Alta | unitaria | JVM | Licencia | n/a |
| `LIC-LICENCIA-08` | Pantalla de bloqueo por suspensión con «Reintentar» | Alta | e2e | cocina | Licencia | n/a |
| `LIC-LICENCIA-09` | Pantalla de bloqueo por vencimiento sin conexión | Alta | e2e | cocina | Licencia | n/a |
| `LIC-LICENCIA-10` | Código revocado (401 en GET /license) y «Escribir otro código» | Alta | e2e | cocina | Licencia | n/a |
| `LIC-LICENCIA-11` | Con la cocina bloqueada, el servidor rechaza a las meseras | Alta | e2e | ambos | Licencia | n/a |
| `LIC-LICENCIA-12` | Al reactivar, las órdenes y el menú siguen intactos | Alta | e2e | ambos | Licencia | n/a |
| `LIC-LICENCIA-13` | Refresco de licencia al abrir, al validar la red y cada 6 h | Media | manual | cocina | Licencia | n/a |
| `LIC-LICENCIA-14` | «Escribir otro código» conserva la URL del servidor del QR | Media | e2e | cocina | Licencia | n/a |
| `LIC-QR-01` | QR del contrato con servidor y producto | Alta | unitaria | JVM | QR | n/a |
| `LIC-QR-02` | QR sin URL usa el servidor compilado | Alta | unitaria | JVM | QR | n/a |
| `LIC-QR-03` | QR sin producto es válido | Media | unitaria | JVM | QR | n/a |
| `LIC-QR-04` | QR con llaves extra las ignora | Baja | unitaria | JVM | QR | n/a |
| `LIC-QR-05` | La URL del QR pierde la diagonal final | Media | unitaria | JVM | QR | n/a |
| `LIC-QR-06` | QR de otro producto se rechaza con mensaje claro | Alta | unitaria | JVM | QR | n/a |
| `LIC-QR-07` | QR de otra versión o sin versión se rechaza | Media | unitaria | JVM | QR | n/a |
| `LIC-QR-08` | URL inválida en el QR | Media | unitaria | JVM | QR | n/a |
| `LIC-QR-09` | Código suelto axc_ se acepta y se recortan espacios | Media | unitaria | JVM | QR | n/a |
| `LIC-QR-10` | Basura, texto vacío y `axc_` suelto se rechazan | Media | unitaria | JVM | QR | n/a |
| `LIC-QR-11` | Código vacío dentro del JSON se rechaza | Baja | unitaria | JVM | QR | n/a |
| `LIC-QR-12` | Escanear un QR válido activa la cocina | Alta | manual | cocina | QR | n/a |
| `LIC-QR-13` | QR con URL http en build debug activa contra el mock local | Media | e2e | cocina | QR | n/a |

### UI — UI / gestos / accesibilidad / rotación / densidad (12)

| ID | Título | Prioridad | Cobertura | Dispositivo | Módulo | Regla |
|---|---|---|---|---|---|---|
| `UI-GESTOS-01` | Umbral de deslizamiento de 1/3 del ancho | Alta | e2e | mesera | Gestos | n/a |
| `UI-GESTOS-02` | Etiquetas del gesto según el estado de la cuenta | Alta | e2e | mesera | Gestos | n/a |
| `UI-GESTOS-03` | Tocar abre el detalle y el scroll vertical no dispara el gesto | Alta | e2e | mesera | Gestos | n/a |
| `UI-DESHACER-01` | La barra de deshacer dura 6 s y trunca el mensaje a una línea | Alta | e2e | mesera | Deshacer | n/a |
| `UI-ROTACION-01` | Rotar durante la captura: se pierde la pantalla de captura (comportamiento actual) | Alta | e2e | mesera | Rotación | n/a |
| `UI-ROTACION-02` | Rotar en cocina conserva la pestaña del menú y no afecta el monitor | Media | e2e | cocina | Rotación | n/a |
| `UI-DENSIDAD-01` | Escala de fuente 1.5× y tamaño de pantalla grande | Media | manual | ambos | Densidad | n/a |
| `UI-TAMANOS-01` | Columnas de la cuadrícula según el ancho del teléfono o tablet | Media | manual | ambos | Tamaños | n/a |
| `UI-ACCESIBILIDAD-01` | TalkBack y lectores de pantalla en las pantallas principales | Media | manual | ambos | Accesibilidad | n/a |
| `UI-NAVEGACION-01` | BackHandler y flujo de pasos de la captura | Alta | e2e | ambos | Navegación | n/a |
| `UI-PANTALLA-01` | La cocina mantiene la pantalla encendida y no se solapa con barras del sistema | Media | manual | ambos | Pantalla | n/a |
| `UI-TEMA-01` | Tema claro fijo y contraste de estados | Baja | manual | ambos | Tema | n/a |

### SEG — Seguridad / datos / privacidad (10)

| ID | Título | Prioridad | Cobertura | Dispositivo | Módulo | Regla |
|---|---|---|---|---|---|---|
| `SEG-AUTENTICACION-01` | Sin autenticación: cualquier equipo de la red puede crear órdenes | Alta | e2e | ambos | Seguridad | n/a |
| `SEG-AUTENTICACION-02` | Sin autenticación: cualquier cliente puede cancelar o anular pagos | Alta | e2e | ambos | Seguridad | n/a |
| `SEG-RESPALDO-01` | allowBackup=false: los datos de la app no salen en respaldos | Media | manual | cocina | Seguridad | n/a |
| `SEG-DATOS-01` | El historial no guarda datos personales de clientes | Media | e2e | cocina | Seguridad | regla 9 |
| `SEG-LICENCIA-01` | El token de licencia se guarda en claro y no aparece en logcat | Media | manual | cocina | Seguridad | n/a |
| `SEG-TRANSPORTE-01` | HTTP en claro solo en build debug | Alta | manual | cocina | Seguridad | n/a |
| `SEG-MDNS-01` | mDNS anuncia el servicio y el puerto a toda la red local | Media | manual | ambos | Seguridad | n/a |
| `SEG-PROTOCOLO-01` | Texto del usuario escapado: una nota no rompe el protocolo | Media | e2e | ambos | Seguridad | n/a |
| `SEG-CAMARA-01` | La cámara solo se usa para leer el QR y no guarda imágenes | Media | manual | cocina | Seguridad | n/a |
| `SEG-ALMACENAMIENTO-01` | Los datos viven solo en el almacenamiento interno de cocina | Media | e2e | ambos | Seguridad | n/a |
