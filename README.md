# Taquería

App Android para tomar órdenes y verlas en cocina **sin internet**. Los dos teléfonos se hablan por la red WiFi local. Internet solo se usa para confirmar que la cuenta de la taquería está activa.

- **Mesera**: ve las cuentas abiertas por mesa. **+ Nueva orden** → mesa → cuenta (o cuenta nueva) → toca productos (cada toque = +1) → **Revisar** → **Enviar a cocina**.
  - Para preparar distinto, en *Revisar* toca el producto y usa **✂ Dividir preparación** (4 → 2 + 2); cada grupo lleva sus propias modificaciones y nota. También sirve mantener presionado un producto.
  - En la lista: tocar una cuenta = detalle · deslizar ← = cancelar cuenta · deslizar → = cobrar. Todo con **Deshacer** unos segundos.
  - Cobro en efectivo o transferencia, con pagos parciales; transferencia exige confirmar que llegó. Pagar no saca la orden de cocina.
  - En el detalle se marca cada orden como **Entregada**. Una cuenta pagada y entregada se cierra sola.
- **Cocina**: monitor pasivo, el taquero no toca nada. Un ticket por mesa (la más antigua primero) con cuenta, número, hora de recepción, tiempo de espera y cada grupo de preparación. Color por espera: verde < 5 min, amarillo 5–10, rojo ≥ 10, rojo con pulso ≥ 15. El menú y los tipos de modificación (exclusivas o combinables) se configuran en 📋.

## Instalar

```bash
./gradlew :app:assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`. Pásalo a los dos teléfonos (cable con `adb install`, o por WhatsApp/USB) y ábrelo; Android pedirá permitir "instalar apps desconocidas".

Al abrir la app por primera vez se elige el rol de ese teléfono. Se puede cambiar después (Cocina → **Rol**, Mesero → **Ajustes**).

## Activar la cocina (una sola vez, con internet)

Las taquerías se administran en **Axzy Central** (repo `AxzyCentral`): alta, plan, cobros y activar/suspender. Los meseros no se activan.

1. En la Central, abre el detalle de la taquería → **Licencias** → **Generar QR de activación**. El código solo se muestra una vez y el QR lo lleva dentro, junto con la dirección del servidor.
2. En el teléfono de cocina elige el rol **Cocina** y toca **Escanear QR de la Central**. Acepta el permiso de cámara (solo se usa para leer el QR). La app hace `POST /activate`, da de alta la tablet y queda activa.
3. Si no hay cámara, se niega el permiso o el QR no se lee, queda el **respaldo manual**: escribe el código `axc_…` en la misma pantalla.

El escáner usa **CameraX + ZXing core**, sin Google Play Services.

Servidor de licencias:
- **El QR trae la dirección del API**: la app la guarda y la usa también en las consultas de `GET /license`. Por eso, activando con QR, el build release **ya no depende** de `-Ptaqueria.apiUrl`.
- Sin QR (código escrito a mano) se usa la URL compilada en la app:
  - Debug: `http://10.0.2.2:4100/api/v1` (API local visto desde el emulador; en debug se permite http). En un teléfono real: `-Ptaqueria.apiUrl=http://<ip-de-la-mac>:4100/api/v1`.
  - Release: `./gradlew :app:assembleRelease -Ptaqueria.apiUrl=https://<api-de-axzy-central>/api/v1`; si no se pasa y se activa a mano, la cocina avisa que no tiene servidor.
- Un QR de otro producto (`p` distinto de `TAQUERIA`), de otra versión o con una URL inválida se rechaza con un mensaje claro y no se activa.

## Publicar una versión en Drive

```bash
./release.sh
```

Incrementa la versión (en `gradle.properties`), corre las pruebas, compila el APK release y lo sube a la carpeta de APKs de clientes en Drive como `PerroOrejonAzteca/TAQUERIA_<versión>.apk`, verificando tamaño y md5. Otro cliente: `./release.sh --client NombreCliente`. Solo compilar: `./release.sh --no-upload`. Requiere `rclone` con el remote `gdrive`.

El APK se firma con la llave debug de la Mac que lo compila: publica siempre desde la misma Mac para que cada versión se instale encima de la anterior sin borrar datos.

## Conectar los teléfonos (sin internet)

Opción A, WiFi del local: los dos teléfonos en la misma red; no necesita tener internet.
Opción B, sin router: activa el **hotspot** del teléfono de cocina (funciona aunque no tenga datos) y conecta el del mesero a ese hotspot.

El mesero encuentra la cocina solo. Si no la encuentra, en **Ajustes** escribe la IP que aparece arriba en la pantalla de cocina.

Notas:
- Si Android avisa "esta red no tiene internet", elige **mantener conexión**.
- En Android 17+ la app pide el permiso de **red local**; hay que aceptarlo en los dos teléfonos.
- El teléfono de cocina debe quedarse con la app abierta (la pantalla no se apaga mientras está en Cocina) y de preferencia conectado al cargador.

## Cómo funciona

- `OrderStore`: datos en el teléfono de cocina (`taqueria.json`, guardado atómico) y todas las reglas: grupos de preparación, cuentas, pagos, deshacer (30 s). Las cuentas cerradas se guardan en `historial.jsonl` (cancelaciones y pagos incluidos).
- `KitchenServer`: servidor TCP puerto 7777 + anuncio mDNS `_taqueria._tcp`. Manda el estado completo a cada mesera en cada cambio.
- `WaiterClient`: busca la cocina por mDNS (o IP fija), reconecta sola; órdenes y pagos llevan llave única para no duplicarse al reintentar.
- `LicenseManager` (`License.kt`): solo en cocina. Al activar llama `POST /activate` (la Central da de alta el modelo de la tablet) y después consulta `GET /license` de Axzy Central al abrir, cuando Android detecta una red con internet validado y cada 6 h. La URL base es la que trajo el QR (guardada en prefs) o la compilada. Guarda la última respuesta: **suspendida** bloquea aunque se pierda la conexión; **activa** vale hasta su `validUntil` (vencimiento del siguiente pago + gracia, entre 3 y 45 días). Usa la hora del servidor y nunca retrocede, así que atrasar el reloj del teléfono no alarga la licencia. Faltando 3 días avisa en la pantalla de cocina.
- Bloqueada (suspendida, código revocado o vencida sin conexión): cocina muestra el aviso con **Reintentar** y `KitchenServer` rechaza lo que mandan las meseras. Las órdenes y el menú no se borran.
- Pruebas: `./gradlew :app:testDebugUnitTest` (reglas de órdenes, cuentas, pagos y licencia).
