/** Enlace de contacto para solicitar demo. Cambia el número o el texto aquí. */
const DEMO_URL =
  "https://wa.me/525530451099?text=" + encodeURIComponent("Hola, quiero probar Tacomanda para mi taquería.");

export const CONTACT_LINK = { href: DEMO_URL, target: "_blank", rel: "noopener noreferrer" } as const;
