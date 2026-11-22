# Pruebas e2e de Tacomanda

Hay dos niveles de pruebas e2e.

## 1. Protocolo cocina ↔ mesero (JVM, sin dispositivos)

`app/src/test/java/com/axzy/taqueria/KitchenWaiterE2ETest.kt` levanta el `KitchenServer` real, con su `OrderStore` en disco, y meseros `WaiterClient` reales conectados por TCP en localhost.

```bash
./gradlew :app:testDebugUnitTest
```

## 2. App completa en dos dispositivos (Maestro)

Los flujos de `e2e/maestro/` usan la app de verdad: una cocina y un mesero, cada uno en su emulador.

**Requisitos:** Maestro (`~/.maestro/bin/maestro`) y dos emuladores:

```bash
emulator -avd maestro_cocina -port 5560 -no-window -no-audio &
emulator -avd maestro_mesero -port 5562 -no-window -no-audio &
```

**Correr todo:**

```bash
e2e/run_maestro.sh
```

Para usar otros dispositivos:

```bash
e2e/run_maestro.sh <serial-cocina> <serial-mesero>
```

El script hace lo siguiente:

1. Compila un APK de debug que apunta a una **Central falsa** (`e2e/mock_central.py`, en `127.0.0.1:4999`). Esa Central acepta cualquier código, salvo los que empiezan con `revocado`, así la cocina se activa en la prueba sin tocar la Central real. También recibe las ventas (`POST /sales`).
2. Instala el APK en los dos emuladores y conecta al mesero con la cocina por `adb forward` (`10.0.2.2:7790` → cocina `:7777`).
3. Corre los flujos en orden, alternando entre dispositivos. Cada paso usa lo que dejó el anterior.

**Ojo:** los flujos `01_*` borran los datos de la app (`clearState`). No los corras en la cocina ni en el teléfono del local.

| Orden | Dispositivo | Flujo | Qué prueba |
|---|---|---|---|
| 1 | cocina | `01_activar` | Elegir rol; un código revocado se rechaza; uno válido activa |
| 2 | mesero | `01_conectar` | Conectarse con la IP a mano; el drawer ya no repite el QR |
| 3 | mesero | `02_orden_por_persona` | Stepper de personas, toques +1 y envío de la orden ($165) |
| 4 | cocina | `02_ticket_por_persona` | Ticket separado por persona, sin "Maíz" |
| 5 | mesero | `03_mesa_ocupada_y_quitar` | Mesa marcada con "1 cuenta", agregar a la cuenta, deslizar para quitar y Deshacer |
| 6 | cocina | `03_agotar` | El taquero agota Cabeza desde Disponibilidad, sin poder editar |
| 7 | mesero | `04_agotado_no_aparece` | Lo agotado ya no se ofrece |
| 8 | cocina | `04_reactivar` | Se vuelve a ofrecer |
| 9 | cocina | `05_admin_pin` | El taquero no ve Ventas; crear PIN, PIN que no coincide, PIN equivocado, entrar y salir; al reabrir la app vuelve a taquero |
| 10 | mesero | `05_cobrar_con_cambio` | Cobrar $270 con un billete de $500: cambio de $230 |
| 11 | cocina | `06_cobrada_sale` | Cobrar marcando "entregada" saca la orden de cocina |
| 12 | cocina | `07_precio_harina` | El admin (con PIN) cambia Asada en harina a $90 en Menú y precios |
| 13 | mesero | `06_harina_y_cancelar` | El mesero ve "Harina $90", pide una, cancela la cuenta deslizando y la regresa con Deshacer |
| 14 | cocina | `08_ticket_harina` | El ticket sí marca "· Harina" |

Al terminar, el runner revisa en la Central falsa (`GET /_sales`) que la cocina haya reportado al menos una cuenta cerrada con la versión de `gradle.properties`. La cocina sube las cuentas a la Central en ≤ 1 min después de archivarlas (30 s tras cerrarse). El flujo `05_admin_pin` también revisa que la versión se vea al pie del drawer.

Los reportes JUnit quedan en `e2e/build/*.xml`.

Si un emulador recién arrancado muestra "X isn't responding", `comun/cerrar_avisos.yaml` lo cierra.
