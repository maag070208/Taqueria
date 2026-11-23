# MVP multi-negocio: meta y sprints

Rama `feat/multi-negocio` en Taqueria y AxzyCentral. Todo se hace en local y no toca Railway ni `main` hasta que
lo apruebes.

- Detalle técnico: [PLAN_MULTI_NEGOCIO.md](PLAN_MULTI_NEGOCIO.md)
- Mock de pantallas: https://claude.ai/artifact/W9pruT3NEEMZnDLMrPBqSx

## Meta del MVP

> **Un negocio que no es taquería, por ejemplo un sushi, recibe su QR de licencia y la app se convierte en la suya:
> sus iconos, sus palabras y su menú, que tú administras desde la Central. El taquero que ya usa la 1.2.2 se
> actualiza sin perder nada.**

### Cómo sabemos que se cumplió

1. En la Central das de alta el giro **Sushi**: subes `iconos_makis.zip`, pones sus palabras (Sushero, Rollo) y su
   menú base.
2. Creas al cliente "Sushi Makis" y le generas su QR.
3. Un teléfono nuevo escanea el QR y, sin hacer nada más:
   - ve iconos de sushi;
   - dice "Sushero" donde antes decía "Taquero";
   - ya tiene el menú de rollos.
4. El mesero toma una orden con esos iconos y la barra la recibe, igual que hoy en la taquería.
5. Cambias el precio de un rollo en la Central y le llega a la barra. Si el admin del local lo cambia en el
   teléfono, lo ves en la Central con su historial.
6. Lo mismo funciona para **Pizzería** con `kit_pizzeria.zip`, sin compilar otra app.
7. El taquero con 1.2.2 instala la versión nueva encima y conserva:
   - menú, cuentas abiertas, historial y ventas;
   - PIN, estaciones y activación.

   Además, su menú aparece en la Central.

### Lo que NO entra al MVP

- Impresora térmica.
- Cobros en línea.
- Varias sucursales por cliente.
- Ícono del launcher distinto por giro: la app sigue siendo una sola y el logo del giro se ve adentro.
- Editor de iconos en la Central: los kits se hacen fuera y se suben como zip.
- Kit de Birria: el giro existe como borrador hasta que haya iconos.
- Publicar en Play Store: el APK se sigue repartiendo por Drive.

## Sprints

Cada sprint dura más o menos una semana y cierra con una **demo en tus dos teléfonos físicos** y en la Central
local. Un sprint no empieza hasta que apruebas la demo del anterior.

### Sprint 0 · Arranque ✅

- [x] Rama `feat/multi-negocio` en los dos repos.
- [x] Plan técnico y mock de Central y app con el diseño actual.
- [x] Llave de firma respaldada en Drive (`Respaldos/Tacomanda-firma`).
- [x] Regla de compatibilidad con la 1.2.2 escrita en el plan.

### Sprint 1 · La Central conoce los giros

**Objetivo:** dar de alta giros con sus iconos y palabras.

Backend:

- [x] Migración `20261123010000_multi_negocio`:
  - cada producto con palabras, partes, set de iconos y menú base;
  - tablas `icon_sets`, `icons` y `catalog_versions`.
  - La TAQUERIA existente queda con sus palabras y todas sus partes.
- [x] API de sets de iconos:
  - subir un kit, que agrega, cambia y quita iconos y sube la versión;
  - bajar el set desde la app, con ETag.
- [x] La licencia trae el giro, el set y la versión del menú. Solo agrega campos, así que la 1.2.2 sigue igual.
- [x] API del menú por cliente con versiones:
  - la cocina adopta el suyo la primera vez;
  - si algo cambió entre medio, responde 409 con la versión vigente.
- [x] Pruebas: 31 unitarias y 31 de integración en verde, en la base local.

Consola:

- [x] Página **Iconos**:
  - subir el zip, que se abre en el navegador;
  - ver los iconos por grupo con lo nuevo, lo que cambió y lo quitado;
  - el logo y el ícono de app del kit, pasados de SVG a PNG.
- [x] Editar un producto como giro: palabras, partes y set de iconos.
- [x] Tarjetas de Productos con el giro: palabras, iconos (primero los platillos), partes y menú base.
- [x] Probado en la Central local:
  - Makis: 41 iconos;
  - Pizzería: 49 iconos con su logo;
  - al volver a subir el mismo zip no cambia la versión;
  - Sushi quedó como "Sushero · Mesero · Rollo" y Pizzería como "Pizzero · Pizza".

**Demo:** subes `iconos_makis.zip` y `kit_pizzeria.zip`, creas los productos Sushi y Pizzería y les asignas su set.

**Hecho cuando:**
- los dos kits suben sin errores;
- al volver a subir el mismo zip no cambia la versión;
- la licencia de un cliente de Sushi trae `iconSet` y sus palabras.

### Sprint 2 · La app se viste del giro

**Objetivo:** el mismo APK se ve y habla como el giro de la licencia.

- [x] Leer `product.terms`, `product.features`, `iconSet` y `catalog` de la licencia. Si no vienen, la app es una
  taquería, como hoy.
- [x] `IconStore`:
  - baja el set cuando cambia la versión y lo guarda en `files/iconos/`;
  - sin internet usa lo último que bajó o los iconos que trae el APK;
  - la cocina se lo pasa a meseros y pantallas, sin que ellos necesiten internet.
- [x] `DishIcon` busca en este orden:
  1. la llave explícita del producto (`icon`);
  2. el set bajado, por el nombre del producto ("Dragon" → `rollo_dragon`);
  3. los iconos de taquería que trae el APK;
  4. el genérico del giro.
- [x] Palabras del giro en roles y textos: "Sushero", "Barra"; "Toca el producto" en lugar de "Toca la carne".
- [x] Partes del giro: sin `mixes` no aparece "mixto"; sin `meatColors` no aparece el color de carne (admin,
  captura y renglones); sin `freeExtras` no aparecen los extras gratis.
- [x] Pantalla "Preparando la app" con el logo del giro mientras bajan los iconos; "Seguir sin esperar" a los 8 s.
- [x] Probado en emuladores con la Central local: la cocina se activa como Sushi Makis, baja 41 iconos, el mesero
  los recibe de la cocina y una orden de Dragon y California llega a la "Barra".
- [ ] Demo en los dos teléfonos físicos.
- [ ] Flujo de Maestro que activa un cliente de Sushi con la Central falsa.

Visto en la demo y pendiente para el Sprint 4: el menú semilla sigue siendo de taquería (Tacos, Cebollitas, "Con
todo"), aunque el giro sea Sushi. Lo resuelven las plantillas por giro.

**Demo:**
- el Oppo se activa como Sushi Makis y el Samsung como mesero;
- los dos ven iconos de sushi y "Sushero";
- el mismo APK activado como taquería se ve exactamente como la 1.2.2.

**Hecho cuando:**
- las pruebas unitarias de `IconStore` y de cómo se elige el icono pasan;
- un flujo de Maestro activa un cliente de Sushi con la Central falsa.

### Sprint 3 · El menú se administra desde la Central

**Objetivo:** tú cambias el menú de un cliente y le llega a su cocina, y al revés.

App (teléfono de cocina):

- [ ] Si la Central no tiene menú, sube el suyo como versión 1. Es la adopción y el paso clave de la migración.
- [ ] Si la licencia trae una versión mayor, baja y aplica el menú:
  - respeta los agotados;
  - no toca cuentas abiertas ni órdenes en cocina.
- [ ] Lo que el admin cambie en el teléfono se sube a la Central.
- [ ] Si hubo conflicto, avisa "La Central cambió el menú" y deja elegir cuál queda.

Consola: Cliente → **Menú**, como en el mock.

- [ ] Categorías y productos con icono.
- [ ] Editor de producto en tres bloques: **¿Cómo se vende?**, **¿Qué le pueden pedir?** e **Icono**.
- [ ] Historial de versiones (quién cambió qué y cuándo) y el botón **Enviar a cocina**.

**Demo:**
1. Cambias el precio del Dragón en la Central y en menos de un minuto la barra y el mesero ven el precio nuevo.
2. El admin agota un rollo en el teléfono, cambia otro precio y lo ves en la Central con "cocina".

**Hecho cuando:**
- la prueba de conflicto pasa: cambios en los dos lados terminan en el aviso y no se pierde nada;
- aplicar un menú nuevo con una cuenta abierta no altera esa cuenta.

### Sprint 4 · Menú más claro y plantillas por giro

**Objetivo:** el editor que hoy es confuso queda simple, y cada giro nuevo arranca con su menú.

- [ ] En la app, el admin usa los mismos tres bloques que la Central.
  - "Preparación" pasa a llamarse **Opciones** y los extras sin costo van a **Ajustes**.
  - Los precios bajan de renglón en lugar de cortarse.
- [ ] Plantillas de menú:
  - **Taquería**, sale del menú semilla de la app;
  - **Sushi**: rollos, nigiri, entradas y bebidas;
  - **Pizzería**: tamaños Chica, Mediana y Grande, y orillas como opciones.
- [ ] "Copiar de la plantilla" en el menú de un cliente.
- [ ] Un cliente nuevo arranca con la plantilla de su giro. La API ya lo hace; falta probarlo en la consola.

**Demo:** creas "Pizzas Don Luigi"; el teléfono se activa y ya trae pizzas con tamaños y sus iconos.

**Hecho cuando:** un mesero que no conoce la app agrega una pizza mediana con orilla de queso sin ayuda.

### Sprint 5 · Migración del taquero y entrega

**Objetivo:** pasar a producción sin que nadie pierda nada.

- [ ] Flujo de Maestro de migración en tus dos teléfonos:
  1. instalar la 1.2.2;
  2. generar datos: menú editado, cuentas abiertas, ventas, PIN y estaciones;
  3. instalar la versión nueva encima;
  4. revisar que todo siga igual y que el menú llegó a la Central.
- [ ] Revisar que el respaldo del taquero esté al día antes de migrarlo.
- [ ] Contrato actualizado en `CONTRATO-VENTAS.md`: giro, iconos y menú.
- [ ] Revisión de código de las dos ramas y PR a `main`, cuando tú lo apruebes.
- [ ] Producción, **solo con tu OK**:
  1. redeploy de Railway, que aplica la migración;
  2. subir los kits de Tacos, Makis y Pizzería;
  3. publicar el APK nuevo.

**Demo:** el taquero real actualiza en el local y sigue vendiendo sin notar nada, salvo que su menú ya se ve en
la Central.

## Estado

| Sprint | Estado |
|---|---|
| 0 · Arranque | ✅ Hecho |
| 1 · La Central conoce los giros | ✅ Hecho en local; espera tu demo |
| 2 · La app se viste del giro | 🟡 Hecho en emuladores; falta la demo en teléfonos y el flujo de Maestro |
| 3 · Menú desde la Central | ⏳ Pendiente; la API ya existe |
| 4 · Menú claro y plantillas | ⏳ Pendiente |
| 5 · Migración y entrega | ⏳ Pendiente |

## Decisiones pendientes

Hay que resolverlas antes del sprint que se indica:

1. **Antes del Sprint 2: marca de la app.** ¿Sigue "Tacomanda" o un nombre general, como "Comanda" con el nombre
   del cliente arriba?
2. **Antes del Sprint 3: quién manda en el menú.** Recomiendo que el admin del local también pueda editarlo y que
   se sincronice con la Central.
3. **Antes del Sprint 5: precio.** ¿El giro cambia el precio del plan o los planes siguen por cantidad de teléfonos?
