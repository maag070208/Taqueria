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
- Fotos por producto: subirlas, recortarlas y pesarlas. Los iconos del giro bastan para el MVP.
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

### Sprint D2 · Consola y QR

- [ ] Pestaña **Menú digital** en el cliente: encendido, slug, datos del local y vista previa.
- [ ] PDF de QR para mesas y mostrador.
- [ ] Probado con la Central local y un emulador: agotas en la cocina y en menos de un minuto la página lo
  muestra.

**Demo:** imprimes el QR de "Pizzas Don Luigi", lo escaneas y ves el menú; agotas la Hawaiana y desaparece.

### Sprint D3 · Producción

- [ ] Dominio `menu.axzy.dev` apuntando a la Central en Railway, **solo con tu OK**.
- [ ] Cuántas veces se abre cada menú, por día, visible en la consola (sin datos del comensal).
- [ ] Contrato: agregar el menú digital en `CONTRATO-VENTAS.md`.

**Demo:** el taquero real pega su QR y un comensal ve el menú desde la mesa.

## Decisiones

Tomadas el 2026-11-25:

1. **Dominio:** `menu.axzy.dev/<slug>`.
2. **Agotados:** se muestran tachados con "Agotado", no se ocultan.
3. **Logo:** no entra al MVP; la página lleva el nombre del local y el color del giro.

Pendiente antes del Sprint D3:

4. **Precio:** ¿va incluido en todos los planes o es un extra?
