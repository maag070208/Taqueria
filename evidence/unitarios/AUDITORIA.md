# Auditoría de la suite unitaria — Tacomanda (Taquería)

- **Fecha:** 2026-11-21 12:24 (America/Tijuana, UTC-8)
- **Repo:** `~/DEV/AXZY/Taqueria` · paquete `com.axzy.taqueria` · commit auditado `4086f5d` (`main`, merge del PR #3 de landing)
- **Comando:** `./gradlew :app:testDebugUnitTest --console=plain --rerun-tasks`
- **Resultado:** `BUILD SUCCESSFUL in 32s` — **34/34 pruebas pasan**, 0 fallidas, 0 omitidas, 0 errores (~0.2 s de CPU de tests)
- **Log completo:** `evidence/unitarios/gradle-test.log` · XML de JUnit copiados en `evidence/unitarios/xml/`
- **Datos estructurados:** `evidence/unitarios/resumen.json` (34 tests + 12 áreas de cobertura + 25 huecos + estado de emuladores)

Resumen en una línea: la suite JVM es sana y rápida, pero cubre **solo las reglas de negocio del `OrderStore`, la lógica pura de licencia y el parser del QR**; quedan fuera el protocolo TCP/JSON entre los dos teléfonos, la lógica pura de captura (`WaiterDraft`), el formato de dinero/tiempo, el catálogo y la licencia de red. Además, **la APK instalada en los emuladores no corresponde al código actual** (se anuncia como "Taquería" y el código ya dice "Tacomanda").

---

## 1. Ejecución

```
$ ./gradlew :app:testDebugUnitTest --console=plain --rerun-tasks
...
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 32s
26 actionable tasks: 26 executed
Configuration cache entry reused.
```

Warnings del compilador (no rompen el build, pero **serán error con Kotlin 2.5** — KTLC-288):

```
w: app/src/main/java/com/axzy/taqueria/OrderStore.kt:195:23 Return in function with expression body and without explicit return type...
w: app/src/main/java/com/axzy/taqueria/OrderStore.kt:217:29 ...
w: app/src/main/java/com/axzy/taqueria/OrderStore.kt:255:48 ...
```

### Totales por clase

| Clase | Tests | Pasados | Fallidos | Omitidos | ms (suma) |
|---|---:|---:|---:|---:|---:|
| `OrderStoreTest` | 14 | 14 | 0 | 0 | 160 |
| `LicenseTest` | 7 | 7 | 0 | 0 | 7 |
| `QrPayloadTest` | 13 | 13 | 0 | 0 | 36 |
| **Total** | **34** | **34** | **0** | **0** | **203** |

La prueba más lenta es `cuenta cerrada pasa al historial…` (104 ms, escritura de `historial.jsonl`); el resto está por debajo de 25 ms.

---

## 2. Las 34 pruebas individuales

| # | Clase | Test | ms | Estado | Cubre |
|---:|---|---|---:|---|---|
| 1 | OrderStoreTest | cancelar cuenta se puede deshacer solo dentro del periodo | 4 | ✅ passed | R8, R10 |
| 2 | OrderStoreTest | cobrar en efectivo guarda con cuanto pagaron y entrega la orden | 8 | ✅ passed | R5, R6, R8 |
| 3 | OrderStoreTest | cuatro tacos de asada se dividen en dos grupos dentro de una sola orden | 3 | ✅ passed | R1 |
| 4 | OrderStoreTest | cuenta cerrada pasa al historial despues del periodo de deshacer | 104 | ✅ passed | R9, R8 |
| 5 | OrderStoreTest | cuentas separadas en la misma mesa con saldos independientes | 3 | ✅ passed | R4 |
| 6 | OrderStoreTest | deshacer pago lo anula sin borrarlo | 7 | ✅ passed | R5 |
| 7 | OrderStoreTest | editar 4 de asada a 2 asada 1 adobada 1 chorizo conserva la version anterior | 3 | ✅ passed | R7 |
| 8 | OrderStoreTest | grupos identicos se unen y las modificaciones combinables se aceptan | 2 | ✅ passed | R1, R2 |
| 9 | OrderStoreTest | los datos sobreviven a reiniciar | 5 | ✅ passed | Persistencia |
| 10 | OrderStoreTest | no se edita una orden entregada y se respeta lo agotado ya pedido | 6 | ✅ passed | R7 |
| 11 | OrderStoreTest | opciones exclusivas contradictorias o ajenas se rechazan | 5 | ✅ passed | R2, R1 |
| 12 | OrderStoreTest | pagar una cuenta no paga las demas de la mesa | 4 | ✅ passed | R4 |
| 13 | OrderStoreTest | pago parcial en efectivo y transferencia no cambia la preparacion | 4 | ✅ passed | R5, R6, R9 |
| 14 | OrderStoreTest | reintento con la misma llave no duplica | 2 | ✅ passed | R3 |
| 15 | LicenseTest | activaFuncionaSinInternetHastaSuFecha | 0 | ✅ passed | Licencia |
| 16 | LicenseTest | atrasarElRelojNoAlargaLaLicencia | 6 | ✅ passed | Licencia |
| 17 | LicenseTest | avisaCuandoEstaPorVencer | 1 | ✅ passed | Licencia |
| 18 | LicenseTest | revocadaBloquea | 0 | ✅ passed | Licencia |
| 19 | LicenseTest | sinCodigoNoEstaActivada | 0 | ✅ passed | Licencia |
| 20 | LicenseTest | suspendidaSigueBloqueadaSinInternet | 0 | ✅ passed | Licencia |
| 21 | LicenseTest | usaLaHoraDelServidorSiElTelefonoVaAtrasado | 0 | ✅ passed | Licencia |
| 22 | QrPayloadTest | basuraSeRechaza | 5 | ✅ passed | QR |
| 23 | QrPayloadTest | codigoSueltoAxcSeAcepta | 0 | ✅ passed | QR |
| 24 | QrPayloadTest | codigoSueltoRecortaEspacios | 0 | ✅ passed | QR |
| 25 | QrPayloadTest | codigoVacioEnJsonSeRechaza | 0 | ✅ passed | QR |
| 26 | QrPayloadTest | jsonConLlavesExtraLasIgnora | 20 | ✅ passed | QR |
| 27 | QrPayloadTest | jsonDelContratoConServidorYProducto | 0 | ✅ passed | QR |
| 28 | QrPayloadTest | jsonSinProductoEsValido | 1 | ✅ passed | QR |
| 29 | QrPayloadTest | jsonSinUrlUsaElServidorCompilado | 1 | ✅ passed | QR |
| 30 | QrPayloadTest | productoAjenoDaMensajeClaro | 0 | ✅ passed | QR |
| 31 | QrPayloadTest | quitaLaDiagonalFinalDeLaUrl | 3 | ✅ passed | QR |
| 32 | QrPayloadTest | urlInvalidaSeRechaza | 1 | ✅ passed | QR |
| 33 | QrPayloadTest | versionDistintaSeRechaza | 0 | ✅ passed | QR |
| 34 | QrPayloadTest | versionFaltanteSeRechaza | 5 | ✅ passed | QR |

Fuente de cada prueba: `app/src/test/java/com/axzy/taqueria/OrderStoreTest.kt`, `LicenseTest.kt` y `QrPayloadTest.kt`.

---

## 3. Matriz de cobertura por regla (CONTEXTO.md, reglas 1-10) + licencia + QR

| Área | Descripción | Estado | Tests (#) | Fuente | Hueco principal |
|---|---|---|---|---|---|
| R1 | Cantidad = suma de grupos; grupos idénticos se unen; cantidades 1-99 | 🟡 parcial | 3, 8, 11 | `OrderStore.kt:290-319` | Tope 99 (`:299`), tope 60 grupos (`:292`), nota en la clave de unión (`:313`) |
| R2 | Modificaciones validadas; exclusivas = máximo una opción | ✅ cubierta | 8, 11 | `OrderStore.kt:300-311` | Trim/duplicados (`:301`), reordenar mods (`:311`) |
| R3 | Reintento con el mismo `clientKey` no duplica | 🟡 parcial | 14 | `OrderStore.kt:38`, `:157` | Idempotencia de **pagos** sin prueba (`:157`) |
| R4 | Cuenta nueva = siguiente número; pagar una no afecta a otras | ✅ cubierta | 5, 12 | `OrderStore.kt:45-54` | Renumeración tras cerrar/cancelar; cuenta de otra mesa (`:52`) |
| R5 | Pagos parciales; no exceder saldo; `receivedCents ≥ importe` | 🟡 parcial | 2, 6, 13 | `OrderStore.kt:154-188` | Cero (`:162`), devolución/negativo (`:165`), `received>10M` (`:172`) |
| R6 | Cobrar = entregar; deshacer el pago regresa a cocina | ✅ cubierta | 2, 13 | `OrderStore.kt:180-186`, `:203-207` | Deshacer entrega manual (`:99-102`); cobro con `deliver=false` |
| R7 | Editar solo si `PENDING`; `revisions`; agotado ya pedido | ✅ cubierta | 7, 10 | `OrderStore.kt:75-87` | Edición idéntica sin revisión (`:83`); producto borrado (`:295-311`) |
| R8 | Deshacer aceptado durante 30 s (`UNDO_WINDOW_MS`) | 🟡 parcial | 1, 2, 6 | `OrderStore.kt:324-326` | Fuera de ventana para `voidPayment` (`:196`) y deshacer entrega (`:99-109`) |
| R9 | Cuenta cerrada = sin pendientes y saldo 0; archivo a `historial.jsonl` | 🟡 parcial | 4, 13 | `Model.kt:162-163`, `OrderStore.kt:213-222`, `:329-343` | Lote de varias cuentas, cuenta cancelada, reapertura, contenido completo del historial |
| R10 | Cancelar cuenta cancela lo pendiente; lo entregado se cobra | 🟡 parcial | 1 | `OrderStore.kt:119-134` | No se prueba que lo `DELIVERED` siga cobrándose (`Model.kt:146`) |
| Licencia | Bloqueo sin código, validez offline, suspensión/revocación, anti-reloj | 🟡 parcial | 15-21 | `License.kt:78-112` | Red/prefs: `activate`, `refresh`, 401→revoked, URL del QR, bloqueo del servidor |
| QR | Parser del QR de activación (contrato §6.1) | ✅ cubierta | 22-34 | `QrPayload.kt:41-105` | Escaneo con cámara y flujo UI (`ui/QrScanner.kt:232`) |

**Balance:** 5 áreas cubiertas (R2, R4, R6, R7, QR), 7 parciales (R1, R3, R5, R8, R9, R10, licencia) y **0 reglas de negocio sin ninguna prueba**. Lo que no tiene ninguna cobertura unitaria son áreas transversales: **protocolo TCP/JSON**, **`WaiterDraft`** (lógica pura de captura), **formato de dinero/tiempo**, **catálogo**, **UI Compose** (no existe `androidTest`) y **rol/alertas**.

---

## 4. Huecos de cobertura (25), con evidencia `archivo:línea`

### Prioridad alta (5)

| ID | Área | Qué falta | Evidencia | Riesgo |
|---|---|---|---|---|
| H21 | Protocolo TCP/JSON | `KitchenServer.handle` (incluye rechazo por licencia bloqueada), `readLineLimited`, `WaiterClient.request` (timeout 8 s), `session`/`clockOffset`, mDNS, `parseHost` y round-trip de `ClientMsg`/`ServerMsg` | `KitchenServer.kt:113-137`, `:120`, `:205-217`; `WaiterClient.kt:121-142`, `:144-168`, `:171-210`, `:224-230`; `Model.kt:174-228` | Es el contrato entre los dos teléfonos: un cambio de campo rompe cocina/mesera sin que la suite lo detecte |
| H22 | Licencia (red) | `activate`/`refresh` HTTP, `resolveApiUrl` (la URL del QR manda), 401→revoked, persistencia en prefs, bloqueo del servidor | `License.kt:188-215`, `:237-239`, `:242-272`, `:279-331`; `KitchenServer.kt:120` | Una activación que no persiste deja la app bloqueada tras reiniciar; el bloqueo del servidor no se verifica |
| H01 | Formato de dinero | `parseMoney` (texto → centavos: `80`, `80.5`, `80,50`; rechaza negativos, >2 decimales, ≥1,000,000) | `Model.kt:244-246`; consumo `ui/AccountScreen.kt:227` | Un fallo aquí cobra mal o bloquea el cobro: es la conversión crítica de efectivo/transferencia |
| H05 | `WaiterDraft.split` | ✂ dividir preparación (4→2+2, 5→3+2, invariante de total) | `App.kt:143-150`; consumo `ui/CaptureScreen.kt:476` | Acción de un toque; si pierde o duplica piezas el ticket sale mal |
| H11 | Regla 3 | Idempotencia de **pagos** por `clientKey` (reintento de cobro no duplica) | `OrderStore.kt:157` (solo se prueba `createOrder`, `OrderStoreTest.kt:60-67`) | Cobro duplicado con mala señal |

### Prioridad media (16)

| ID | Área | Qué falta | Evidencia |
|---|---|---|---|
| H02 | Formato de dinero | `money()` y `moneyInput()` (formato es-MX, relleno del campo) | `Model.kt:241`, `:248`; `ui/AccountScreen.kt:257` |
| H03 | Formato de tiempo | `elapsedText()` (espera `m:ss`) y umbrales de color | `Model.kt:250-253`; `ui/KitchenScreen.kt:189`; `ui/AccountScreen.kt:164` |
| H06 | `WaiterDraft` | `toggleMod` (exclusividad: con/sin/aparte en captura) | `App.kt:153-160`; `ui/CaptureScreen.kt:460` |
| H07 | `WaiterDraft` | `add`/`removeOne`/`setQty` (un toque = +1, tope 99, bajar de 1 quita el grupo) | `App.kt:125-140` |
| H08 | `WaiterDraft` | `retainAvailable` (quita agotados al capturar, conserva lo ya pedido al editar) | `App.kt:170-174` |
| H09 | `WaiterDraft` | `differsFrom` (normaliza producto+mods+nota para avisar cambios sin guardar) | `App.kt:114-118`; `ui/CaptureScreen.kt:87` |
| H10 | Estado derivado | `payStatus`/`isClosed`: ramas `CANCELLED`, saldo negativo y cuenta sin órdenes | `Model.kt:150-163`; solo indirecto en `OrderStoreTest.kt:86,90,104,126` |
| H12 | Regla 5 | Rechazos: importe cero, devolución que excede, `received>10M`, cambio solo en efectivo | `OrderStore.kt:160-173` |
| H13 | Regla 8 | Fuera de ventana para `voidPayment` y deshacer entrega/cancelación de orden | `OrderStore.kt:99-109`, `:191-210`, `:324-326` |
| H14 | Regla 10 | Cancelar cuenta con líneas entregadas: que sigan cobrándose | `OrderStore.kt:119-134`; `Model.kt:146` |
| H16 | Persistencia | `taqueria.json` corrupto → respaldo `.corrupt-<ts>` y semilla | `OrderStore.kt:345-356` |
| H18 | Validaciones | `clientKey` vacío/>64, mesa >20, cuenta cerrada/cancelada/de otra mesa, 60 grupos, nota >100 | `OrderStore.kt:36-54`, `:290-319` |
| H19 | Regla 7 | Editar orden con producto borrado (se conservan sus mods) | `OrderStore.kt:295-311` |
| H20 | Catálogo | `saveProduct` (precio 0..10M, emoji, grupos), `deleteProduct`, `moveProduct`, `saveModifierGroup` (opción repetida en otro tipo), `deleteModifierGroup`, `seed()` | `OrderStore.kt:227-282`, `:371-393` |
| H23 | Escaneo QR | CameraX/ZXing y flujo de activación en UI (el parser sí está cubierto 13/13) | `ui/QrScanner.kt:72`, `:145`, `:232`; `ui/LicenseScreen.kt:61-68` |
| H24 | UI Compose | No existe `app/src/androidTest`: `SwipeCard` (umbral 1/3), `payColor`, `waitColor`/`isCritical`, gestos y editor de grupos | `ui/Components.kt:151-190`, `:52`, `:61`; `ui/WaiterScreen.kt:272` |

### Prioridad baja (4)

| ID | Área | Qué falta | Evidencia |
|---|---|---|---|
| H04 | Formato | `clockText()` y `tableLabel()` | `Model.kt:256`, `:258` |
| H15 | Regla 9 | `archiveClosed` con lote/varias cuentas/cuenta cancelada/vacío/reapertura | `OrderStore.kt:213-222`, `:329-343` |
| H17 | Regla 4 | Numeración diaria de órdenes (`counterDate`/`counterLast`) | `OrderStore.kt:56-57`, `:70` |
| H25 | Rol y alertas | `applyRole`/`manualHost`/`restartNetwork`, `Alerts.beep/vibrate`, permiso de red local | `App.kt:37-60`, `:194-212`; `MainActivity.kt:22-30` |

---

## 5. Emuladores y APK instalada

### Estado verificado (solo lectura; snapshot 12:24, en `evidence/unitarios/emuladores.txt`)

| Dato | `emulator-5554` (COCINA) | `emulator-5556` (MESERA) |
|---|---|---|
| Estado adb | `device` | `device` |
| Android | 17 (API 37), `sdk_gphone16k_arm64` | 17 (API 37), `sdk_gphone16k_arm64` |
| Rol en `shared_prefs/taqueria.xml` | `KITCHEN` | `WAITER` |
| `manualHost` | — | `10.0.2.2` |
| `versionName` / `versionCode` | `1.0.1` / `2` | `1.0.1` / `2` |
| `lastUpdateTime` | 2026-11-21 11:11:02 | 2026-11-21 11:36:04 |
| `firstInstallTime` | 2026-11-20 21:52:10 | 2026-11-20 21:52:18 |
| APK `base.apk` md5 / bytes | `6a5a289baff01e3a4d8a785a2b9a7f4c` / 15,420,207 | **idéntica** (`6a5a289b…`) |
| Label que anuncia la APK | **Taquería** | **Taquería** |
| App en primer plano | Sí, `com.axzy.taqueria/.MainActivity` (pid 13137) | Sí, `com.axzy.taqueria/.MainActivity` (pid 14412) |
| IP local | `10.0.2.15/24` (eth0) | `10.0.2.17/24` (wlan0) |
| Datos de negocio | semilla: 15 productos, 5 tipos, **0 cuentas, 0 órdenes**, `counterDate=""`; sin `historial.jsonl` | no aplica (la mesera no guarda datos) |

`adb devices` → dos dispositivos `device`. `adb forward --list`:

```
emulator-5554 tcp:7777 tcp:7777
emulator-5554 tcp:17777 tcp:7777
```

Enlace TCP vivo en el momento del snapshot (evidencia de `/proc/net/tcp{,6}`):

- Cocina: `tcp6 :7777` (0x1E61) en estado `0A` = **LISTEN**, uid 10228 (la app).
- Mesera: conexión a `10.0.2.2:7777` (`…0202000A:1E61`) en estado `01` = **ESTABLISHED**, uid 10228.

Licencia (caché en cocina): `status=ACTIVE`, `tenant=Taquería El Fogón`, `validUntilMs=1795651200000` → **2026-11-26T00:00Z**, `nextDueDate=2026-11-21`. El último `refresh` falló a las 12:21 con `Failed to connect to /10.0.2.2:4100` (la API local de la Mac está apagada) y la app siguió operando con la caché — exactamente el comportamiento que prueban los `LicenseTest` 15-21.

### ⚠️ Hallazgo: la APK instalada NO corresponde al código actual

| | APK instalada (ambos emuladores) | Build local / código actual |
|---|---|---|
| md5 | `6a5a289baff01e3a4d8a785a2b9a7f4c` | `4ed325aa7bb20cd16ec9bd4c369c888b` |
| Tamaño | 15,420,207 bytes | 16,121,376 bytes |
| `application-label` | **Taquería** | **Tacomanda** |
| Iconos | `res/drawable/ic_launcher_bg.xml` + `ic_launcher_fg.xml` (vectores viejos), sin `ic_launcher_round` | `res/mipmap-{m,h,xh,xxh,xxxh}dpi-v4/*.png` + `ic_launcher_round.xml` |
| `versionName`/`versionCode` | 1.0.1 / 2 | 1.0.1 / 2 (**sin cambio**) |
| Fecha | instalada 11:11:02 (5554) y 11:36:04 (5556) | build 11:51; `:app:assembleDebug` quedó **UP-TO-DATE** a las 12:20, así que este APK sí corresponde al código actual |

Prueba forense: `aapt2 dump badging` de la APK instalada dice `application-label:'Taquería'` y no trae `ic_launcher_round`; el source actual dice `android:label="Tacomanda"` (`app/src/main/AndroidManifest.xml:25`) e incorpora los iconos nuevos en el commit `c81a83a` (*"feat(app): renombrar a Tacomanda con iconos nuevos"*, 12:01). Conclusión: **la APK en los emuladores es anterior a `c81a83a`** y el E2E se está ejecutando contra una build vieja, aunque `versionName`/`versionCode` coincidan con `gradle.properties:9-10` (no se incrementó la versión, así que la versión no sirve para distinguir builds).

Evidencia: `evidence/unitarios/apk-instalada-badging.txt` y `evidence/unitarios/apk-actual-badging.txt`.

### Nota de coordinación

Durante la auditoría el Lead ejecutó E2E sobre ambos emuladores: a las 12:19 el store de cocina tenía 3 cuentas/3 órdenes (`evidence/recon/state/taqueria_5554.json`); a las 12:24 aparece solo la semilla (0 cuentas/0 órdenes) y sin `historial.jsonl`, señal de que el estado se reinició y la app se relanzó a las ~12:21 (log de `License`: `pid 13137` a las 12:21:16). No es un fallo de la app: es el estado transitorio normal mientras corre el E2E del Lead.

---

## 6. Hallazgos

1. **La APK instalada está atrasada respecto al código** (label "Taquería" vs "Tacomanda", md5 y tamaño distintos). El E2E valida una build anterior a los iconos/nombre nuevos, y como la versión no cambió no es posible distinguirlas por `versionName`. Severidad: **media-alta** para la validez del E2E.
2. **El protocolo TCP/JSON no tiene ninguna prueba JVM** (H21). Es el contrato más frágil: serialización `sealed interface` + manejo de líneas + timeouts; un cambio de campo o del formato de línea rompe los dos teléfonos sin que la suite lo detecte.
3. **La lógica pura de captura (`WaiterDraft`) no tiene pruebas** (H05-H09): dividir preparación, exclusividad de salsas, +1/tope 99, quitar agotados y detección de cambios. Es lógica sin Android (se puede probar en JVM) y decide qué se manda a cocina.
4. **Dinero y tiempo sin pruebas** (H01-H04): `parseMoney`/`money` convierten la entrada del usuario a centavos y `elapsedText` alimenta los colores de espera de cocina.
5. **Derivados de estado apenas indirectos** (H10): `payStatus`/`isClosed` gobiernan gestos, cobro, devolución y archivado; sus ramas `CANCELLED`, saldo negativo y cuenta sin órdenes no se afirman nunca.
6. **Licencia: solo se prueba la lógica pura** (H22). La parte que puede bloquear el negocio (persistir la activación, 401→revocada, URL que trae el QR, rechazo de mensajes en el servidor) no tiene prueba.
7. **No hay `androidTest`**: nada de UI, gestos ni cámara se prueba automáticamente; solo E2E manual del Lead.
8. **Warnings que romperán el build** con Kotlin 2.5: tres `return` en funciones con cuerpo de expresión y sin tipo de retorno (`OrderStore.kt:195`, `:217`, `:255`).
9. La suite es **muy rápida** (~0.2 s de tests, 32 s de build) y el aislante es correcto: `TemporaryFolder` + reloj inyectado (`OrderStoreTest.kt:15-23`), sin dependencias de Android salvo `Log` (cubierto por `unitTests.isReturnDefaultValues`).

---

## 7. Recomendaciones priorizadas

1. **Protocolo (H21):** prueba de round-trip `AppJson` para los 7 `ClientMsg` y los 3 `ServerMsg`, prueba de `readLineLimited` (límite 64 KB y `\n` final) y de `KitchenServer.handle` inyectando el `isLocked`/store; conviene exponer `handle` como `internal` para probarlo.
2. **`WaiterDraft` (H05-H09):** es Kotlin puro; tests JVM de `split` (4→2+2, 5→3+2, total invariante), `toggleMod` (exclusiva vs combinable), `add`/`setQty` (tope 99, quitar en 0), `retainAvailable` (editar vs nueva) y `differsFrom` (sin importar el orden).
3. **Dinero y tiempo (H01-H04):** `parseMoney` (comas, 2 decimales, negativos, tope), `money`/`moneyInput`, `elapsedText` (negativos, horas), `tableLabel`.
4. **`OrderStore` (H11-H20):** idempotencia de pagos, rechazos (cero/devolución/10M), ventanas de deshacer, regla 10 con entregado, archivado en lote, archivo corrupto, numeración diaria, catálogo y `seed()`.
5. **Licencia (H22):** inyectar un `fetcher`/`HttpURLConnection` local para probar 200/401/red caída y la prioridad de la URL del QR; prueba de persistencia en prefs con `Robolectric` o extrayendo la lógica de resolución de URL.
6. **`androidTest` mínimo (H23-H24):** flujo mesa → productos → revisar → enviar (con un `Fake` de `WaiterClient`), swipe de cobrar/cancelar y `SwipeCard` con umbral 1/3.
7. **Proceso:** instalar la APK recién compilada antes del E2E (`:app:assembleDebug` + `adb install -r`) y hacer notar en el reporte que `versionName`/`versionCode` no cambian entre builds de debug.

---

## 8. Archivos de evidencia

| Archivo | Contenido |
|---|---|
| `evidence/unitarios/gradle-test.log` | Log completo de `./gradlew :app:testDebugUnitTest --console=plain --rerun-tasks` (`BUILD SUCCESSFUL in 32s`) |
| `evidence/unitarios/xml/TEST-*.xml` | Los 3 XML de JUnit tal como los deja Gradle (fuente de los totales) |
| `evidence/unitarios/resumen.json` | 34 tests (clase, nombre, estado, ms), 12 áreas de cobertura, 25 huecos y estado de ambos emuladores |
| `evidence/unitarios/emuladores.txt` | Snapshot crudo de `adb devices`, `adb forward`, `dumpsys package`, prefs, proceso/primer plano, `/proc/net/tcp`, store y licencia |
| `evidence/unitarios/apk-instalada-badging.txt` | `aapt2 dump badging` de la APK instalada (label "Taquería", iconos viejos, md5) |
| `evidence/unitarios/apk-actual-badging.txt` | `aapt2 dump badging` del `app-debug.apk` actual (label "Tacomanda", iconos nuevos, md5) |
| `evidence/unitarios/generar_resumen.py` | Script reproducible que regenera `resumen.json` desde los XML |

No se modificó código fuente, tests, ni la UI de los emuladores; toda la escritura quedó bajo `evidence/unitarios/`.
