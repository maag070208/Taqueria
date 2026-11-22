# Tacomanda: contexto del proyecto (21 nov 2026)

## Qué es

App Android (Kotlin + Jetpack Compose) para taquerías, con dos roles en la misma APK:

- **Cocina (taquero):** es la fuente de verdad. Guarda todo en `filesDir`: `taqueria.json` es el estado activo y `historial.jsonl` guarda las cuentas cerradas, una línea JSON por cuenta. Levanta un servidor TCP en el puerto **7777** y difunde `AppState` a las meseras.
- **Mesera:** se conecta por mDNS, por QR de conexión o por IP manual. No guarda datos de negocio.
- **Licencia:** la cocina se activa contra **AxzyCentral**, con QR o con código a mano. Sin licencia activa, `KitchenServer` rechaza todas las escrituras. La mesera no necesita licencia.
- **Precios:** todos en centavos.

## Piezas clave del código

| Archivo | Qué hace |
|---|---|
| `Model.kt` | Catálogo (`Category`, `Product`, `Variant`, `MeatColor`, `ModifierGroup`), órdenes (`OrderLine`, `Order`, `PersonExtras`), protocolo (`ClientMsg`/`ServerMsg`), lógica de preparación (`defaultMods`, `toggleMod`, `shortPrep`, `mixOptions`). |
| `OrderStore.kt` | Valida todo lo que llega de la red (`parseLines`, `parseExtras`), persiste de forma atómica, archiva y contiene el seed del menú y las operaciones de Admin. |
| `App.kt` | `WaiterDraft` / `DraftLine`: la orden en captura, por persona y por renglón. |
| `ui/CaptureScreen.kt` | Flujo de la mesera: mesa → cuenta → menú de categorías → captura por persona → resumen → enviar. |
| `ui/AdminScreen.kt` | Pestañas Productos, Preparación y Ajustes (mesas y extras gratis). |
| `ui/KitchenScreen.kt` | Tablero de cocina, con "Para llevar" separado. |
| `ui/DishIcon.kt` | Íconos ilustrados con fallback a emoji. |
| `Sales.kt` / `ui/SalesScreen.kt` | Ventas por día, semana y mes, historial de cobros y CSV. |

## Cambios de esta sesión (sin commit, rama `feat/conexion-qr-menu-semilla`)

### Menú y captura

- **Menú principal con 8 botones:** Tacos, Quesatacos, Quesadillas, Tortas, Burritos, Vampiros, Bebidas y Órdenes. Cada botón muestra cuántos van en la cuenta.
- **Captura con la carne primero:**
  - Tocar la carne suma +1 al renglón abierto de esa carne, o lo crea "Con todo" en la tortilla de siempre.
  - El renglón se despliega para configurarlo y se pliega con "✓ Listo".
  - "+ Otra de Asada" abre otro renglón para una preparación distinta.
  - Ejemplo: 3 asada con todo y 2 asada sin salsa ni mayonesa salen como dos renglones.
- **Personas:** cada tarjeta es una persona y "Agregar persona" agrega otra. Los renglones y los extras se guardan por persona.
- **Colores por carne:** Asada gris, Adobada rojo, Suadero café. Se configuran en Admin con `MeatColor`.
- **Presentaciones con precio propio (`Product.variants`):**
  - Tacos: Maíz $35 (Adobada $30) y Harina $100. Tripa solo se vende en maíz.
  - Tortas: Sencilla $80 y Con queso $100.
  - Si el renglón no trae presentación, el servidor usa la primera.
- **Mixto (`Product.mix`):** se eligen de 2 a 5 carnes de la misma categoría. Por defecto lleva las dos primeras (Asada y Adobada). En la orden sale como "Taco Mixto (Asada + Adobada)".
- **"Va" con atajos editables en Admin (`ModifierGroup.defaultOption` / `implies`):**
  - Arranca en "Con todo".
  - Puro verde marca Sin salsa, Sin cebolla y Sin cilantro.
  - Pura salsa marca Sin cebolla, Sin cilantro, Sin aguacate y Sin mayonesa.
  - Pura carne marca todos los "Sin".
  - Los ajustes se pueden mover a mano después. El resumen corto dice, por ejemplo, "Puro verde, con cilantro".
  - La mayonesa es un grupo exclusivo: Sin mayonesa o Con mayonesa.
- **Extras gratis por persona:** Cebollitas, Chiles y Tostadas. Se editan en Admin, pestaña Ajustes.
- **Órdenes por monto (`Category.byAmount`):** se elige la carne y luego $50, $100, $200 u otra cantidad. En la orden sale como "Orden de Asada $100".

### Mesas, para llevar y pantallas

- **Número de mesas configurable:** `AppState.tableCount`, 12 por defecto, se cambia en Admin, pestaña Ajustes.
- **Para llevar separado:**
  - En la selección de mesa es un botón aparte arriba.
  - En la mesera hay una pestaña "Mesas | Para llevar".
  - En cocina tiene su propia sección, con un ticket por cliente.
- **Íconos centrados:** `Glyph` / `centeredText` (sin el relleno de fuente) en botones, círculos y contadores.

### Iconografía personalizada

- **Fuentes en `design/iconos/`:**
  - `svg/`: 23 del diseñador más 4 generados (`esp_quesataco`, `esp_burrito`, `esp_vampiro`, `cat_ordenes`).
  - `png/`: los originales del diseñador.
  - `vista_previa.png`.
- **Scripts:**
  - `generar_faltantes.py` crea los 4 que faltaban con la misma paleta.
  - `exportar_png.sh` copia a `app/src/main/res/drawable-nodpi/ico_*.png`. Usa el PNG original si existe; si no, renderiza con Quick Look.
- **Resolución por nombre (`DishIcons`):**
  - Categoría: `cat_<categoría>` o `esp_<singular>`.
  - Producto: `taco_<carne>` en Tacos, `beb_<bebida>` en Bebidas, y la de su categoría en lo demás.
  - Alias: mixto usa campechano y "agua de sabor" usa `agua_fresca`.
  - Si se agrega un PNG nuevo con el nombre correcto, aparece sin tocar código.

### Versión y ventas a la Central (1.1.0, código 4)

- **Versión:** sale de `gradle.properties` (`taqueria.versionName` y `taqueria.versionCode`). `App.versionLabel` la lee del paquete instalado (no de `BuildConfig`) y se ve como "Tacomanda v1.1.0 (4)" al pie del drawer y en la pantalla de rol. La cocina la manda a la Central en `X-App-Version` y en cada reporte de ventas; la consola la muestra junto a cada dispositivo.
- **Ventas:** `SalesSync.kt` sube a la Central las cuentas que la cocina ya archivó.
  - Usa `POST /sales` con el código del dispositivo. El contrato está en `AxzyCentral/docs/CONTRATO-VENTAS.md`.
  - Revisa cada minuto si hay cuentas nuevas, las manda en lotes de 20 y recuerda cuántas lleva en prefs `sales_sync`. Si falla, reintenta a los 10 minutos. Sin internet la operación no se afecta.
  - La Central no duplica. Las métricas salen de `GET /clients/:id/sales/metrics` (`api/src/lib/sales-metrics.ts`).

## Estado de entornos

- **AxzyCentral (Railway):**
  - API: `https://axzycentralapi-production.up.railway.app/api/v1`. La consola web `central.axzy.dev` usa esa misma API.
  - Datos: 0 clientes, 1 plan "Taquería" de $500 (RECURRING) y el admin `axzy`. La contraseña la tiene el dueño; no se guarda aquí.
- **Builds de prueba:** la URL de la Central sale de `taqueria.apiUrl` en `local.properties` (no va a git), ya fijada a Railway en esta Mac. Así cualquier compilación, también las del IDE, apunta a la misma Central. Con `-Ptaqueria.apiUrl=…` se cambia para una sola compilación. Sin ninguna de las dos, el debug usa la Central local (`10.0.2.2:4100`).
  - Antes las compilaciones alternaban entre local y Railway según quién compilara. La cocina le preguntaba a la Central local, esta respondía 401 y la cocina quedaba como "revocada". Ahora la app además guarda el servidor que aceptó el código y sigue consultando ese.
- **Emuladores:** `taq_cocina` (emulator-5554) es la cocina. Al 21/nov en la noche pide el código de activación otra vez: hay que pegarlo a mano.
- **Puente para el teléfono real:** `adb -s emulator-5554 forward tcp:7778 tcp:7777` más un relay Python en `0.0.0.0:7777`. El relay sigue corriendo; el forward se pierde cada vez que se reinicia el emulador.
- **Teléfono Samsung SM-A175F (192.168.10.103, depuración inalámbrica):** es el mesero y se conecta a la Mac (192.168.10.107).

## Pruebas

- `./gradlew :app:testDebugUnitTest`: 75 pruebas. `SalesSyncTest` cubre el reporte de ventas contra una Central de mentira. Las unitarias cubren tortilla y precio, mixto, órdenes por monto, personas y extras, atajos de "Va", Admin, edición, cobros, archivo, reinicio y el PIN de admin.
- `KitchenWaiterE2ETest` es de punta a punta: levanta el `KitchenServer` real con su `OrderStore` en disco y meseros `WaiterClient` reales por TCP en localhost (puerto libre, sin mDNS). Cubre:
  - menú al conectar;
  - orden por persona con broadcast a dos meseros;
  - reintentos sin duplicar;
  - agregar a la cuenta y editar;
  - cobrar, entregar y deshacer el pago;
  - cancelar y restaurar;
  - licencia bloqueada;
  - rechazos de validación;
  - reinicio de cocina con reconexión y persistencia.

- **Maestro (app completa en dos emuladores):** se corre con `e2e/run_maestro.sh`; los detalles están en `e2e/README.md`.
  - Son 14 flujos con cocina en `maestro_cocina` (emulator-5560) y mesero en `maestro_mesero` (emulator-5562).
  - Usan una Central falsa local (`e2e/mock_central.py`) para activar sin tocar Railway.
  - Al 21/nov los 14 pasan de corrido. Al final el runner revisa que la cocina haya reportado sus ventas a la Central falsa con la versión correcta.
- **URL de la Central:** ahora es un recurso (`R.string.license_api_url`), no `BuildConfig`. Kotlin copiaba la constante de `BuildConfig` dentro del código que la usa y la compilación incremental no la actualizaba, así que algunos APK salían con la URL de la compilación anterior.

## Pendientes

1. Volver a pegar el código de activación en la cocina del emulador.
2. Crear el PIN de admin (drawer → Entrar como admin) y probar Ventas.
3. Confirmar precios supuestos:
   - Harina $100 en todas las carnes.
   - Mixto $35/$100.
   - Quesatacos de Suadero, Cabeza y Mixto a $45.
   - Burritos, Vampiros y Quesadillas por carne.
4. Hacer commit y generar el APK release (`release.sh`) cuando se apruebe. La versión ya es 1.1.0 (4).
5. Desplegar la Central con la migración `20261122010000_sales`. Railway la aplica al arrancar (`prisma migrate deploy`), junto con la de usuarios y roles que sigue pendiente. Hasta entonces la cocina reintenta y no pierde nada.
6. Pendientes opcionales:
   - Íconos para Kitchen y Waiter en pantallas vacías.
   - Ilustraciones de pastor, chorizo y demás cuando se agreguen al menú (ya existen los PNG).
