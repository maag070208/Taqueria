# Taquería — Contexto del proyecto

App Android nativa para operar una taquería con **dos teléfonos en red Wi-Fi local, sin internet**: uno de **mesera** (captura, edita, cobra) y uno de **cocina** (monitor pasivo + servidor de datos). Prioridad: velocidad de captura, claridad y prevención de errores.

- Ruta: `~/DEV/AXZY/Taqueria` · Paquete: `com.axzy.taqueria`
- Especificación funcional de referencia: `FLUJO_TAQUERIA.md` (proporcionada por el dueño del producto).
- Cliente actual: **PerroOrejonAzteca**.

## Stack

- Kotlin + Jetpack Compose (artefactos Compose Multiplatform 1.12.2, Material3 1.12.0-alpha03), un solo módulo `:app`.
- AGP 9.1.1, Kotlin 2.4.21, Gradle 9.5.1, compile/target SDK 37, minSdk 26, JDK 17.
- kotlinx.serialization (JSON) y coroutines. **Sin dependencias de red de terceros**: sockets TCP de Java + `NsdManager` (mDNS) de Android.

## Arquitectura

```
Teléfono COCINA                                Teléfono MESERA
┌───────────────────────────────┐   Wi-Fi     ┌──────────────────────────┐
│ OrderStore (fuente de verdad) │◄── TCP ────►│ WaiterClient             │
│  taqueria.json + historial    │  JSON por   │  descubre por mDNS o IP  │
│ KitchenServer :7777 + mDNS    │  línea      │  reconecta solo          │
│ UI: monitor pasivo + catálogo │             │ UI: cuentas, captura,    │
└───────────────────────────────┘             │     cobro, edición       │
                                              └──────────────────────────┘
```

- **Una sola app**; el rol (Cocina / Mesero) se elige al abrirla y se guarda en preferencias.
- La red puede ser un router sin internet o el **hotspot del teléfono de cocina**.
- Cocina transmite el **estado completo** a cada mesera en cada cambio y un `ping` cada 10 s; la mesera considera la conexión muerta tras 25 s sin mensajes.
- La mesera nunca guarda datos de negocio: todo vive en cocina.

### Archivos clave

| Archivo | Responsabilidad |
|---|---|
| `Model.kt` | Modelo (`Product`, `ModifierGroup`, `Order`, `OrderLine`, `Account`, `Payment`, `AppState`), protocolo (`ClientMsg`/`ServerMsg`), formato de dinero/hora |
| `OrderStore.kt` | Todas las reglas de negocio y validación; guardado atómico; historial; semilla del menú |
| `KitchenServer.kt` | Servidor TCP + anuncio mDNS `_taqueria._tcp`; archiva cuentas cerradas |
| `WaiterClient.kt` | Descubrimiento, conexión, reintentos y llamadas request/reply |
| `App.kt` | Singletons, rol, `WaiterDraft` (orden en captura con grupos), alertas sonido/vibración |
| `ui/WaiterScreen.kt` | Lista de cuentas abiertas, gestos, deshacer |
| `ui/CaptureScreen.kt` | Mesa → cuenta → productos → resumen → editor de grupos; también edición de órdenes |
| `ui/AccountScreen.kt` | Detalle de cuenta, cobro (cambio, transferencia, entregar) |
| `ui/KitchenScreen.kt` | Monitor pasivo por mesa con colores de espera |
| `ui/AdminScreen.kt` | Catálogo: productos, orden, agotados, tipos de modificación |
| `ui/Theme.kt`, `ui/Components.kt` | Paleta, medidas únicas (`Ui`), cuadrícula de filas de igual altura, swipe, deshacer |
| `app/src/test/.../OrderStoreTest.kt` | 14 pruebas de reglas |

## Modelo de datos

- **Product**: nombre, precio en **centavos**, categoría, activo/agotado, ícono (emoji), `modifierGroupIds`. El orden de la lista = orden de aparición.
- **ModifierGroup**: tipo de modificación (p. ej. *Salsa*: Con / Sin / Aparte). `exclusive=true` → solo una opción; `false` → combinables (*Extras*). Cada opción pertenece a un solo tipo.
- **Account** (cuenta dentro de una mesa: Cuenta 1, 2…): pagos, `cancelledAt`, `closedAt`.
- **Order**: pertenece a una cuenta; `lines` = **grupos de preparación**; estado de preparación `PENDING | DELIVERED | CANCELLED`; `revisions` (versiones anteriores al editar), `editedAt`, `deliveredByPayment`, `clientKey` (idempotencia).
- **OrderLine** (grupo): producto, cantidad, `mods`, `note`. Ej.: 4 asada = `2 × Asada [Con cebolla]` + `2 × Asada [Sin cebolla]` en **una sola orden**.
- **Payment**: importe (negativo = devolución), método `CASH | TRANSFER`, `receivedCents` (efectivo recibido, para el cambio), `voided` (deshacer sin borrar).
- Estado de pago derivado: `UNPAID | PARTIALLY_PAID | PAID | CANCELLED`. **Preparación y pago son independientes.**

## Reglas de negocio (en `OrderStore`)

1. La cantidad de un producto = suma de sus grupos (no hay total aparte que pueda descuadrarse). Grupos idénticos se unen; cantidades 1–99.
2. Modificaciones validadas contra los tipos del producto; en tipos exclusivos, máximo una opción.
3. Reintentos con el mismo `clientKey` devuelven la misma orden/pago (no duplican).
4. Cuenta nueva = siguiente número dentro de la mesa. Pagar una cuenta no afecta a las demás.
5. Pagos parciales permitidos; no se acepta pagar más que el saldo. Efectivo con `receivedCents` ≥ importe. Transferencia requiere confirmación en la UI.
6. **Cobrar = entregar** (opción marcada por defecto): al registrar el pago, lo pendiente de esa cuenta pasa a `DELIVERED`. Deshacer el pago lo regresa a cocina.
7. Editar orden solo si sigue `PENDING`; la versión anterior queda en `revisions`. Lo ya pedido se acepta aunque luego se haya agotado.
8. Deshacer (cancelar cuenta/orden, entregar, pagar) aceptado por cocina durante **30 s** (`UNDO_WINDOW_MS`); la barra de la mesera dura 6 s.
9. Cuenta **cerrada** = sin pendientes en cocina y saldo 0. Tras 30 s se archiva en `historial.jsonl` (se conservan cancelaciones y pagos; nada se borra).
10. Cancelar cuenta cancela lo pendiente; lo entregado sigue cobrándose.

## Flujo de la mesera

- Pantalla principal: **cuentas abiertas agrupadas por mesa** (tarjetas uniformes con estado de pago, consumo, tiempo en cocina y saldo).
- **+ Nueva orden** → mesa (cuadrícula) → cuenta existente o nueva → productos (**un toque = +1**, mantener presionado = modificar) → **Revisar** → **Enviar a cocina**. Se limpia solo después de la confirmación de cocina.
- En el resumen, tocar un producto abre el editor: cada grupo con cantidad −/+, opciones por tipo y nota; **✂ Dividir preparación** parte a la mitad el grupo más grande (4 → 2+2).
- Gestos en la tarjeta de cuenta: **tocar** = detalle · **← deslizar** = cancelar cuenta · **→ deslizar** = cobrar (si debe) / **entregar** (si ya pagó y sigue en cocina) / devolver (saldo negativo). Umbral: 1/3 del ancho.
- Cobro en efectivo: botones **Exacto · $100 · $200 · $300 · $500 · $1,000** u "Otra cantidad"; muestra el **cambio** grande. La barra de deshacer muestra "Cambio $X" primero.
- Detalle de cuenta: totales, órdenes (Cancelar · **Editar** · Entregada), pagos, *Agregar productos*, *Cobrar/Entregar*.

## Pantalla de cocina

- Monitor **pasivo**: el taquero no toca nada. Un ticket por mesa, la más antigua primero; cada orden con cuenta, número, hora de recepción y sus grupos (producto en mayúsculas + modificaciones).
- Colores por espera (siempre con el tiempo en texto): verde < 5 min, amarillo ≥ 5, rojo ≥ 10, **rojo con pulso** ≥ 15.
- Orden nueva: "● NUEVA" 1 min + sonido/vibración. Orden editada: "● EDITADA" 3 min, grupos nuevos resaltados y "QUITAR: …" tachado; también suena.
- Pantalla siempre encendida. Botones solo de configuración: 📋 catálogo y ⇄ cambiar rol. Muestra IP y meseras conectadas.

## Diseño

- Tema claro, acento verde `#3F8F6F`, tarjetas blancas con borde suave, íconos emoji en círculo.
- **Cuadrícula estricta**: espacio 12 dp, esquinas 16/12 dp, controles de 52 dp, mosaicos cuadrados, filas de la cuadrícula con altura igual (`GridRow`), columnas fijas según ancho.
- Capturas de referencia en `docs/capturas/`.

## Build, pruebas y publicación

```bash
./gradlew :app:assembleDebug          # APK de prueba
./gradlew :app:testDebugUnitTest      # reglas de negocio (14 pruebas)
./release.sh                          # versión +1, pruebas, release y subida a Drive
./release.sh --client OtroCliente     # otra subcarpeta de cliente
./release.sh --no-upload              # solo compilar
```

- Versión en `gradle.properties` (`taqueria.versionName`, `taqueria.versionCode`); `release.sh` la incrementa y la guarda solo si el build salió bien. Última publicada: **1.0.1 (code 2)**.
- Destino: carpeta compartida de APKs de clientes en Google Drive (id `1fl61fCVRcqCWBDe52Wzy3cNeFa-_sIwt`) → `PerroOrejonAzteca/TAQUERIA_<versión>.apk`, vía `rclone` remote `gdrive`, con verificación de tamaño y md5. Basado en `~/DEV/CHECK/BARCELONA/APP/android/release.sh`.

## Consideraciones operativas

- **Android 17+** exige el permiso de red local (`ACCESS_LOCAL_NETWORK`); la app lo pide al abrir. Sin él, mDNS falla (ya no tumba la app).
- Si Android avisa "red sin internet", elegir **mantener conexión**.
- El APK se firma con la **llave debug de la Mac que compila**: publicar siempre desde la misma Mac o las actualizaciones no se instalarán encima.
- El remote `gdrive` de rclone usa el client_id compartido de rclone, que deja de funcionar en 2026: crear client_id propio.
- Cambios de formato de datos: si cocina no puede leer `taqueria.json`, lo respalda como `taqueria.json.corrupt-<ts>` y arranca con el menú semilla.
- Seguridad: no hay autenticación; cualquiera en la misma red podría conectarse. Usar Wi-Fi/hotspot con contraseña que no tengan los clientes.

## Pruebas manuales hechas

Dos emuladores (Android 17) conectados vía `adb forward tcp:7777` y IP manual `10.0.2.2` (mDNS no cruza emuladores). Verificado: dividir grupos, cuentas separadas, pago parcial efectivo + transferencia, cobro con cambio, cobrar-y-entregar, entregar con gesto, cancelar con deshacer, edición con resaltado en cocina, persistencia tras reinicio.

## Pendiente

- Probar en teléfonos físicos en red sin internet, incluido el descubrimiento automático.
- Mover productos entre cuentas (transferencia explícita, regla 8 de la especificación).
- Reportes/ventas del día a partir de `historial.jsonl`.
- Llave de firma propia del proyecto si se publicará desde varias computadoras.
