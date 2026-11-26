# Plan: una sola app para cualquier giro (tacos, birria, sushi, pizza…)

Rama `feat/multi-negocio` en **Taqueria** y en **AxzyCentral**. No va a `main` hasta que lo apruebes.

Mock de pantallas (Central y app): https://claude.ai/artifact/W9pruT3NEEMZnDLMrPBqSx

## La idea en una línea

En la Central cada **producto es un giro** (Taquería, Sushi, Birria, Pizza…) con su **set de iconos**, su
**menú base** y sus **palabras** (taquero / sushero / pizzero). La app es **una sola**: al escanear el QR de
licencia sabe de qué giro es el cliente, baja sus iconos y su menú y se adapta sola.

## Cómo queda hoy (lo que hay que cambiar)

| Hoy | Problema |
|---|---|
| `Product` en la Central = la app (TAQUERIA) | No hay giros: todo cliente es taquería |
| 32 iconos dentro del APK, elegidos por nombre (`ico_taco_asada`) | Un giro nuevo exige recompilar la app |
| Menú semilla fijo en `OrderStore.seed()` (tacos, quesatacos…) | No sirve para sushi o pizza |
| Conceptos de taquería en el modelo: color de carne, "mixto", "Va: con todo", cebollitas | Atados a tacos |
| El menú del cliente solo se edita en el teléfono de cocina | Tú no puedes darlo de alta ni corregirlo desde la Central |
| Editor de menú con pestañas "Productos / Preparación / Ajustes" | Confuso: no se entiende qué es variante, qué es opción y qué es extra |

## Fase 1 · Central: giros, iconos y menús (modelo)

1. **Giro = `Product`** (se queda la tabla, cambia el sentido). Campos nuevos:
   - `terms` (JSON): cómo se llama cada cosa en ese giro.
     `{ "kitchenRole": "Sushero", "waiterRole": "Mesero", "item": "Rollo", "kitchen": "Barra" }`.
   - `iconSetId`: el set de iconos del giro.
   - `features` (JSON): qué partes del modelo usa. Ejemplo: `{ "mixes": true, "meatColors": true, "freeExtras": true }`.
     Taquería lo prende todo; sushi apaga "mixto" y colores.
2. **`IconSet`** `{ id, name, version }` e **`Icon`** `{ setId, key, group, label, png256 (bytea), sha }`.
   - Se dan de alta **subiendo un zip**. Ya hay dos kits:
     - `iconos_makis.zip`: 45 iconos en `png/256`.
     - `kit_pizzeria.zip`: 49 iconos en `iconos_png/256`, más `logo_svg/` con el logo, el ícono de app y el favicon.
   - Se acepta `png/256` o `iconos_png/256`. El nombre del archivo es la llave y el prefijo es el grupo:
     `rollo_` → Rollos, `pizza_` → Pizzas, `ing_` → Ingredientes, `side_` → Acompañamientos, `beb_` → Bebidas,
     `serv_` y `extra_` → Extras.
   - Si el kit trae `logo_svg/icono_app.svg` y `logo_principal.svg`, se guardan como **logo del giro**: la app los
     muestra al activarse y en la pantalla de inicio. El SVG se pasa a PNG en la Central, porque Android no
     dibuja SVG.
   - Cada vez que cambia, sube la `version` y las apps lo vuelven a bajar.
   - El set de tacos actual (32 PNG del APK) se sube como el primer set, "Tacos".
3. **`CatalogTemplate`** por giro: el menú base (categorías, productos con su `iconKey`, presentaciones con
   precio, opciones para pedir, extras sin costo, estaciones sugeridas). El de Taquería sale de `OrderStore.seed()`.
4. **`ClientCatalog`** por cliente: su menú real, copiado de la plantilla al darlo de alta. Tiene `version`,
   `updatedAt` y `updatedFrom` (`central` | `cocina`), y se guarda su historial.

## Fase 2 · API

- La licencia (`GET /license`, `POST /activate`) responde además:
  `product { key, name, terms, features }`, `iconSet { id, version }` y `catalog { version }`.
- `GET /icon-set` (token del dispositivo): el zip de PNG del set, con `ETag` = versión.
- `GET /catalog`: el menú vigente del cliente. `PUT /catalog` lo sube la cocina cuando el admin lo edita en el
  teléfono y manda `baseVersion`. Si la Central ya va en otra versión, responde 409 con la vigente.
- En la consola: CRUD de giros, sets de iconos (subir zip), plantillas y menú por cliente.

## Fase 3 · App: una sola, que se adapta

1. **Activación por QR** (como hoy) y luego, en pantalla, "Preparando Sushi Makis…": baja iconos y menú.
   Sin internet en la primera vez cae al set de tacos que trae el APK.
2. **Iconos**: `IconStore` guarda el zip en `files/iconos/<set>/<llave>.png`.
   `DishIcon` busca en este orden:
   1. `product.icon`, la llave explícita de la Central;
   2. el set bajado;
   3. los iconos del APK;
   4. el genérico.
3. **Menú**: el teléfono de cocina aplica el menú de la Central cuando llega una versión nueva (en la revisión de
   licencia, cada pocas horas o con "Actualizar menú").
   - Al aplicarlo se respetan los **agotados** y no se tocan las cuentas abiertas: las órdenes ya copian sus renglones.
   - Lo que el admin edite en el teléfono se sube a la Central.
   - Si los dos cambiaron, el teléfono avisa "La Central cambió el menú" y deja elegir.
4. **Palabras y partes del giro**:
   - Los roles y los textos usan `terms`: "Sushero" en lugar de "Taquero".
   - Color de carne, mixtos y extras sin costo solo aparecen si `features` los prende.
5. **Nombre**: la app deja de decir "Tacomanda" en pantalla y muestra el nombre del cliente ("Sushi Makis").
   Por decidir: la marca general y si se cambia el ícono del launcher. El `applicationId` se queda igual, así los
   clientes actualizan sin reinstalar.

## Fase 4 · Administrar el menú: más claro (Central y app)

El editor nuevo usa **las mismas palabras en los dos lados**:

- **Categorías** a la izquierda, con su icono, y **productos** como tarjetas con icono, nombre y precio.
- Al abrir un producto hay tres bloques en lenguaje de mostrador:
  1. **¿Cómo se vende?** Presentaciones con precio: Maíz $35 / Harina $100, Chico / Grande, 5 pzas / 10 pzas.
  2. **¿Qué le pueden pedir?** Grupos de opciones reutilizables, como "Va" o "Picante", que se marcan o desmarcan
     con chips.
  3. **Icono**: se elige del set del giro, con vista previa.
- **En la Central** se suman tres cosas:
  - "Copiar de la plantilla del giro";
  - el historial ("cocina cambió el precio de Asada ayer 9:14");
  - el botón **Enviar a cocina**, que muestra qué cambia antes de mandar.
- **En la app** el admin ve lo mismo en versión de teléfono. "Preparación" pasa a llamarse **Opciones** y los
  extras sin costo se mueven a **Ajustes**.

## Fase 5 · Migración y pruebas

- El giro actual `TAQUERIA` se convierte en "Taquería":
  - con el set "Tacos" (los 32 PNG);
  - con la plantilla que sale de `seed()`;
  - con todos los `features` prendidos.
- A los clientes actuales se les arma su `ClientCatalog` a partir de su último respaldo, así no pierden su menú.
- Pruebas:
  - unitarias: armar el menú desde la plantilla, aplicar una versión nueva respetando agotados, conflicto 409;
  - integración en la Central: subir zip, licencia con giro, catálogo con versión;
  - Maestro en los dos teléfonos: activar un cliente de Sushi con la Central falsa y ver iconos y textos de sushi.
- Datos de prueba: giro **Sushi** con `iconos_makis.zip` y una plantilla de rollos, nigiris y extras.

## Compatibilidad con la 1.2.2 que ya usa el taquero (regla de todo el trabajo)

La 1.2.x se actualiza encima a la versión nueva **sin perder nada**: menú, cuentas abiertas, historial,
ventas pendientes de subir, PIN de admin, estaciones y activación.

1. **La misma app.** Mismo `applicationId` (`com.axzy.taqueria`) y **la misma llave de firma**: el
   `~/.android/debug.keystore` de esta Mac. Si esa llave se pierde, ningún APK nuevo se instala encima y habría
   que desinstalar, con lo que se borran los datos. **Hay que respaldarla hoy** fuera de la Mac.
2. **El archivo local solo crece.** `taqueria.json` ya se lee con `ignoreUnknownKeys` y cada campo nuevo
   (`icon`, `catalogVersion`…) trae un valor por defecto. No se renombra ni se quita nada; los conceptos de taquería
   (color de carne, mixto) siguen en el modelo y solo se ocultan si el giro no los usa.
3. **El menú del taquero manda la primera vez.** Al abrir la versión nueva, si la Central no tiene menú de ese
   cliente, la cocina **sube su menú actual** como versión 1. Nunca se sobrescribe con la plantilla.
4. **Iconos sin cambio visible.** Los productos viejos no tienen llave de icono, así que siguen usando la búsqueda
   por nombre (`taco_asada`…) y los 32 iconos que trae el APK. El set "Tacos" de la Central es el mismo dibujo.
5. **La activación sigue.** El token y la licencia guardados siguen sirviendo. La respuesta nueva de la licencia
   solo **agrega** campos, que la 1.2.2 ignora, así que las apps viejas siguen funcionando contra la Central nueva.
6. **Red de seguridad.** El respaldo de cada minuto en la Central ya existe. Antes de migrar al taquero se revisa
   que su respaldo esté al día.
7. **Prueba obligatoria antes de entregar.**
   1. Instalar la 1.2.2 en los dos teléfonos y generar datos (menú editado, cuentas abiertas, ventas, PIN, estaciones).
   2. Instalar la versión nueva **encima**.
   3. Revisar que todo siga igual y que su menú haya llegado a la Central.

   Va como flujo de Maestro.

## Orden sugerido

1. Central: modelo, migraciones, subir zip de iconos y licencia con giro (fases 1 y 2).
2. App: `IconStore`, `DishIcon` con la llave explícita, `terms` y `features`, y activación que baja todo (fase 3).
3. Menú en la Central, sincronización con la cocina y el editor nuevo en los dos lados (fases 3.3 y 4).
4. Migración de clientes actuales y pruebas en los teléfonos (fase 5).

## Por decidir contigo

1. **Marca de la app.** ¿Sigue "Tacomanda" o un nombre general? Puede ser "Comanda" con el nombre del cliente arriba.
2. **Quién manda en el menú.** ¿Puede el admin del local editarlo en el teléfono, o solo tú desde la Central?
   Recomiendo permitir las dos cosas, con sincronización.
3. **Plan.** ¿El giro cambia el precio del plan o los planes siguen por cantidad de teléfonos?
