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
   - Toma la última versión del menú del cliente y deja fuera los productos inactivos o los marca como
     "Agotado" (decisión 3).
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

### Sprint D1 · Página pública

- [ ] Migración: `Client.menuSlug` (único, opcional), `menuEnabled` y `menuInfo` (horario, WhatsApp y
  dirección).
- [ ] `GET /m/:slug` con el HTML del menú: categorías, tamaños, opciones como texto y "Agotado".
- [ ] `GET /m/:slug/i/:key.png` con los iconos del giro.
- [ ] Pruebas: un slug apagado o inexistente da 404 con página amable; en el HTML no aparece nada interno; un
  agotado en la cocina se ve en la página después de la siguiente sincronización.

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

## Decisiones pendientes

1. **Dominio:** `menu.axzy.dev/<slug>` (recomendado: corto y para impresión) o `central.axzy.dev/m/<slug>`
   (sin configurar nada nuevo).
2. **Precio:** ¿va incluido en todos los planes o es un extra?
3. **Agotados:** ¿se ocultan o se muestran tachados con "Agotado"? Recomendado: tachados, para que el comensal no
   pregunte por algo que vio ayer.
4. **Logo del local:** ¿lo subimos por cliente o basta con el nombre y el color del giro para el MVP?
