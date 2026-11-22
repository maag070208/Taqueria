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

### Pruebas

- 57 pruebas unitarias JVM pasan (`./gradlew :app:testDebugUnitTest`).
- Cubren tortilla y precio, mixto, órdenes por monto, personas y extras, atajos de "Va", Admin (mesas y extras), edición, cobros, archivo y reinicio.

## Estado de entornos

- **AxzyCentral (Railway):**
  - API: `https://axzycentralapi-production.up.railway.app/api/v1`. La consola web `central.axzy.dev` usa esa misma API.
  - Datos: 0 clientes, 1 plan "Taquería" de $500 (RECURRING) y el admin `axzy`. La contraseña la tiene el dueño; no se guarda aquí.
- **Builds de prueba:** se compilan con `./gradlew :app:assembleDebug -Ptaqueria.apiUrl=<API Railway>`.
- **Emuladores:**
  - `taq_cocina` (emulator-5554) tiene la cocina sin activar, en la pantalla "Activa esta cocina".
  - `taq_mesero` (emulator-5556) tiene la mesera conectada.
- **Puente para teléfonos reales:** `adb forward tcp:7778 tcp:7777` más un relay Python en `0.0.0.0:7777` dentro del scratchpad de la sesión. La mesera en un teléfono usa la IP de la Mac (192.168.10.107).
- **Teléfono Samsung (192.168.10.103):** no estaba conectado por adb, ni por USB ni por depuración inalámbrica.

## Pendientes

1. Crear el cliente en Central con el plan Taquería y activar la cocina con QR o código.
2. Conectar el teléfono por USB o por depuración inalámbrica para instalar la mesera.
3. Confirmar precios supuestos:
   - Harina $100 en todas las carnes.
   - Mixto $35/$100.
   - Quesatacos de Suadero, Cabeza y Mixto a $45.
   - Burritos, Vampiros y Quesadillas por carne.
4. Hacer commit, subir la versión y generar el APK release (`release.sh`) cuando se apruebe.
5. Pendientes opcionales:
   - Íconos para Kitchen y Waiter en pantallas vacías.
   - Ilustraciones de pastor, chorizo y demás cuando se agreguen al menú (ya existen los PNG).
