# Plan · Menú digital para los clientes

Fecha: 2026-11-25 · Rama: se trabaja después de que `feat/multi-negocio` llegue a producción (Sprint 5).

## Qué es

Cada local tiene una página pública con su menú, por ejemplo `menu.axzy.dev/don-luigi`. El comensal la abre:

- con el QR pegado en la mesa o en el mostrador;
- con un enlace por WhatsApp, Instagram o Google Maps.

La página **no pide nada al comensal**: no hay app ni registro. Muestra categorías, productos, tamaños y precios con
los iconos del giro, y lo agotado. En el MVP es **solo para ver**: el pedido lo sigue tomando el mesero.

## Por qué sale casi gratis

Desde el Sprint 3, la Central ya tiene el menú de cada cliente y la cocina lo sincroniza cada 30 s:

- un cambio de precio en la cocina o en la consola llega a la Central en menos de un minuto;
- un producto agotado también, así que el menú digital lo muestra casi al momento;
- los iconos de cada giro ya viven en la Central (sets de iconos).

No hay que capturar el menú dos veces ni tocar la app de la cocina.

## Cómo funciona

```
Cocina ──(cada 30 s)──► Central: versión del menú ──► menu.axzy.dev/<slug> ──► celular del comensal
Consola ──(editar)───┘
```

1. **Central, ruta pública sin sesión**: `GET /m/:slug`.
   - Devuelve una página HTML ya armada en el servidor, sin framework, para que cargue rápido en celulares
     baratos y con datos móviles.
   - Toma la última versión del menú del cliente y marca los productos inactivos como "Agotado", tachados.
   - Lleva `ETag` y caché corta (30 s), y un límite de peticiones por IP.
2. **Iconos**: `GET /m/:slug/i/:key.png` sirve el PNG del set del giro, con caché larga porque la llave cambia
   cuando cambia el icono.
3. **Solo sale lo que el cliente quiere enseñar**: nombre del local, menú, horario, teléfono o WhatsApp y
   dirección. Nunca PIN, ventas, dispositivos, cobros ni notas internas.
4. **Si la cocina está sin internet**, la página sigue con el último menú y dice "Actualizado hace 2 h".

## En la consola

En el cliente, una pestaña **Menú digital**:

- encender o apagar el menú digital (apagado, `/m/:slug` responde "Este menú no está disponible");
- el **slug** (`don-luigi`), que la consola sugiere a partir del nombre; es único y se puede cambiar, pero el QR
  impreso deja de servir;
- los datos del local: horario, WhatsApp y dirección, todos opcionales;
- una **vista previa** igual a la del celular;
- **imprimir QR**: un PDF con QR grandes para mesas y uno para el mostrador, con el nombre del local.

## Lo que NO entra al MVP

- Pedir desde la mesa. Necesita que la Central le mande órdenes a la cocina, y hoy solo la cocina habla con la
  Central. Es otro proyecto: pagos, abuso y confirmación del mesero.
- Varios idiomas, promociones y programa de lealtad.
- Que el cliente edite su menú digital desde la web: lo edita la cocina o nosotros en la consola, como hoy.

## Sprints

### Sprint D1 · Página pública ✅ (en local, 2026-11-25)

- [x] Migración `20261125010000_menu_digital`: `Client.menuSlug` (único), `menuEnabled` (apagado por defecto) y
  `menuInfo` (horario, WhatsApp y dirección). La consola los guarda con `PATCH /clients/:id`: no se enciende sin
  dirección y una dirección repetida da `MENU_SLUG_TAKEN`.
- [x] `GET /m/:slug` (`api/src/routes/public-menu.ts` y `api/src/lib/public-menu.ts`):
  - HTML armado en el servidor, sin JavaScript, con modo oscuro;
  - categorías con atajos, tamaños con precio, "Por monto" en Órdenes y extras sin costo;
  - si todos los productos de una categoría tienen las mismas opciones, se dicen una vez arriba;
  - agotados tachados con "Agotado";
  - si la cocina lleva más de 30 min sin conectarse: "Menú actualizado hace 3 h; puede haber cambios";
  - caché de 30 s, límite de 1200 peticiones por minuto por IP y una CSP propia (sin scripts ni nada de fuera);
  - con el cliente suspendido, el menú apagado o sin menú en la Central: 404 con "Este menú no está
    disponible".
- [x] `GET /m/:slug/i/:key.png`: el icono se elige igual que en la app (llave del admin, por nombre en el set,
  por categoría y el genérico); la URL lleva el sha, así que se guarda en caché un año.
- [x] Pruebas: 5 unitarias (46 en total) y 3 de integración (35): validación de la dirección, nada interno en el
  HTML (notas, teléfono, código del dispositivo), nombres escapados, iconos y agotado después de sincronizar.
- [x] Probado con la Central local: `localhost:4100/m/don-luigi` en tamaño celular, con pizzas, tamaños e
  iconos. En la cocina del emulador se agotó Cabeza y a los 37 s la página ya lo mostraba tachado.

Para el D3: `menu.axzy.dev/<slug>` sin el `/m/` necesita que la Central reconozca ese dominio y sirva el menú en
la raíz.

**Demo:** abres `localhost:4100/m/don-luigi` en el celular y ves las pizzas con sus tamaños.

### Sprint D2 · Consola y QR ✅ (en local, 2026-11-25)

- [x] Página **Menú digital** del cliente (`/clientes/:id/menu-digital`), con una tarjeta en el detalle que
  dice si está encendido:
  - encendido, dirección (sugerida del nombre; avisa que cambiarla invalida los QR impresos), horario, WhatsApp
    y dirección del local;
  - vista previa en un marco de celular; la página pública solo deja que la muestre la consola
    (`frame-ancestors` con `WEB_ORIGIN`).
- [x] QR para imprimir en hoja carta: **Mesas** (6 tarjetas para recortar) y **Mostrador** (1 grande), con
  corrección de errores alta. Se abre el diálogo de impresión, que también guarda como PDF.
- [x] La base del QR sale de `GET /config` (`menuUrl`): `PUBLIC_MENU_URL` si está, si no `<API>/m`.
- [x] Probado con la Central local: guardar horario y WhatsApp los muestra en la página y en la vista previa;
  al reactivar Cabeza en la cocina del emulador, a los ~6 s deja de salir agotado.

**Demo:** imprimes el QR de "Pizzas Don Luigi", lo escaneas y ves el menú; agotas la Hawaiana y desaparece.

### Sprint D3 · Producción

Hecho en código (rama `feat/menu-digital-d3`, 2026-11-26):

- [x] **Dominio propio:** si `PUBLIC_MENU_URL` es la raíz de un dominio (`https://menu.axzy.dev`), ese dominio
  solo sirve menús en `/<slug>` y sus iconos; ahí no existe el API. Sin la variable sigue en `<API>/m/<slug>`.
- [x] **Visitas** por día de CDMX en la consola (hoy, 7 y 30 días), en la tabla `menu_visits` (migración
  `20261126010000_menu_visits`, aditiva). Solo cuenta navegadores: no las vistas previas de WhatsApp o Facebook,
  ni los bots, ni la vista previa de la consola. No guarda nada del comensal.
- [x] Contrato: secciones 9 (giro, iconos y menú) y 10 (menú digital) en `AxzyCentral/docs/CONTRATO-VENTAS.md`.
- [x] Kit de iconos de Tacos (`kit_tacos.zip`, 23 iconos de `design/iconos/png`), probado en la Central local:
  el menú digital de la taquería sale con sus iconos.

En producción (2026-11-26 y 27):

- [x] `menu.axzy.dev`: CNAME `menu` → Railway en el DNS de axzy.dev, certificado de Let's Encrypt y
  `PUBLIC_MENU_URL=https://menu.axzy.dev`; ese dominio solo sirve menús.
- [x] Kits de iconos Tacos (23), Makis (41) y Pizzería (49) subidos; giros Sushi y Pizzería creados con su set y su
  menú base; Taquería usa el set Tacos.
- [x] Clientes de demostración con su menú encendido: `menu.axzy.dev/demo-taqueria`, `/demo-sushi` y
  `/demo-pizzeria`, con 5 fotos libres de Pexels cada uno.

### Después del D3 · Diseño y fotos reales ✅ (en producción, 2026-11-27)

- [x] Carta con diseño por giro: portada de color con el nombre en letra de cartel e ilustraciones (o tres fotos
  del menú), secciones con remate, precios con puntos guía y dos columnas en pantallas anchas. Paletas: Taquería
  (verde, crema, chile), Sushi (carbón, crema, rojo), Pizzería (tomate, crema, albahaca); los demás giros usan la de
  la app. Solo fuentes del sistema: no se manda la IP del comensal a terceros.
- [x] Fotos reales por producto (`product_photos`): en la consola, Menú digital → Fotos de productos; el navegador
  las reduce a 1000 px en JPEG. El menú enseña la foto en lugar del icono. No viajan a la cocina.

Falta para el taquero real:

1. Revisar que su menú en la Central sea el suyo: se guardó desde la consola, no salió de su tableta. Al
   actualizar a la 1.3.0, su app pregunta con cuál quedarse.
2. Publicar el APK 1.3.0 después de la demo en teléfonos.
3. En su cliente → Menú digital: dirección, horario y WhatsApp; encender, subir sus fotos e imprimir los QR.

## Decisiones

1. **Dominio:** `menu.axzy.dev/<slug>`.
2. **Agotados:** se muestran tachados con "Agotado", no se ocultan.
3. **Logo:** no entra al MVP; la página lleva el nombre del local y el color del giro.
4. **Precio:** el menú digital va incluido en todos los planes (2026-11-27).
