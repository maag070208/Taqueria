import { useId, useState, type ReactNode } from "react";
import { FaWhatsapp } from "react-icons/fa";
import { LuArrowDown, LuCheck, LuChevronDown, LuMenu, LuSmartphone, LuWifi, LuX } from "react-icons/lu";
import { CONTACT_LINK } from "./config";

/* ------------------------------------------------------------------ */
/* Contenido                                                           */
/* ------------------------------------------------------------------ */

const NAV = [
  { href: "#como-se-usa", label: "Cómo se usa" },
  { href: "#que-necesitas", label: "Qué necesitas" },
  { href: "#preguntas", label: "Preguntas" },
];

/** Problemas de todos los días, escritos como comandas pegadas en la pared. */
const PAINS = [
  { text: "La comanda se perdió entre el comal y la plancha.", tilt: "-rotate-2" },
  { text: "¿Eran sin cebolla o con todo? Nadie le entiende a la letra.", tilt: "rotate-1" },
  { text: "La mesa 7 lleva 15 minutos esperando y nadie se dio cuenta.", tilt: "-rotate-1" },
  { text: "A la hora de cobrar, la cuenta no cuadra.", tilt: "rotate-2" },
];

type Step = {
  n: string;
  title: string;
  text: string;
  tip: string;
  img: string;
  alt: string;
  extra?: ReactNode;
};

const WAIT_COLORS = [
  { color: "bg-cilantro", label: "Va a tiempo", sub: "menos de 5 min" },
  { color: "bg-maiz", label: "Apúrale", sub: "de 5 a 10 min" },
  { color: "bg-salsa", label: "¡Ya urge!", sub: "más de 10 min" },
];

const STEPS: Step[] = [
  {
    n: "1",
    title: "La mesera toma el pedido",
    text: "Toca el taco y se suma. ¿Dos de asada y uno de pastor? Tres toquecitos y listo. Va viendo el total mientras anota.",
    tip: "¿Sin cebolla? Deja presionado el taco y escoge.",
    img: "/capturas/captura.png",
    alt: "Pantalla de la mesera con los tacos del menú y sus precios",
  },
  {
    n: "2",
    title: "La cocina lo ve al momento",
    text: "El pedido aparece en el teléfono de cocina con todo y sus indicaciones. Cada mesa muestra cuánto lleva esperando, así el taquero sabe qué sale primero.",
    tip: "El taquero no tiene que tocar nada, solo mirar.",
    img: "/capturas/cocina.png",
    alt: "Pantalla de cocina con las órdenes de cada mesa y su tiempo de espera",
    extra: (
      <ul className="mt-6 grid gap-2 sm:grid-cols-3">
        {WAIT_COLORS.map((w) => (
          <li key={w.label} className="flex items-center gap-2.5 rounded-2xl bg-papel px-3 py-2.5 ring-1 ring-raya">
            <span className={`h-4 w-4 shrink-0 rounded-full ${w.color}`} aria-hidden="true" />
            <span className="leading-tight">
              <span className="block text-[15px] font-extrabold">{w.label}</span>
              <span className="block text-xs text-carbon-soft">{w.sub}</span>
            </span>
          </li>
        ))}
      </ul>
    ),
  },
  {
    n: "3",
    title: "Cobras sin broncas",
    text: "Efectivo o transferencia, todo junto o en partes. Siempre sabes cuánto falta por pagar en cada mesa.",
    tip: "La transferencia se anota hasta que confirmas que llegó.",
    img: "/capturas/cobro.png",
    alt: "Pantalla de cobro con efectivo o transferencia y el saldo pendiente",
  },
];

const EXTRAS = [
  {
    title: "Mitad y mitad",
    text: "¿Cuatro de asada pero dos sin cebolla? Se divide la orden y cada parte lleva su propia nota.",
    img: "/capturas/dividir.png",
    alt: "Pantalla para dividir una orden en dos grupos con distintas indicaciones",
  },
  {
    title: "Cada quien paga lo suyo",
    text: "Una mesa puede tener varias cuentas. Cada quien paga lo suyo y no se revuelve nada.",
    img: "/capturas/cuentas.png",
    alt: "Lista de cuentas abiertas por mesa con lo que debe cada una",
  },
  {
    title: "¿Te equivocaste? Se deshace",
    text: "Si cancelas o cobras por error, tienes unos segundos para deshacerlo. Sin pleitos con la caja.",
    img: "/capturas/detalle.png",
    alt: "Detalle de una cuenta con sus órdenes, pagos y botones para cancelar, editar o entregar",
  },
];

const MENU_BOARD = [
  { name: "Asada", price: "$20" },
  { name: "Pastor", price: "$18" },
  { name: "Suadero", price: "$18" },
  { name: "Campechano", price: "$20" },
  { name: "Tripa", price: "$22" },
];

const NEEDS = [
  {
    icon: LuSmartphone,
    title: "Dos teléfonos Android",
    text: "Uno para la mesera y otro para la cocina. Puede ser el que ya tienes.",
  },
  {
    icon: LuWifi,
    title: "El Wi-Fi de tu local",
    text: "No hace falta que tenga internet. También sirve la señal que comparte el teléfono de cocina.",
  },
  {
    icon: LuCheck,
    title: "Y ya, nada más",
    text: "Sin computadora, sin impresora de tickets y sin cables por toda la taquería.",
  },
];

const FAQ = [
  {
    q: "¿De verdad funciona sin internet?",
    a: "Sí. Los teléfonos se hablan entre ellos con el Wi-Fi de tu local, aunque no tenga internet. Internet solo se usa para confirmar que tu cuenta de Tacomanda está activa.",
  },
  {
    q: "¿Es difícil de aprender?",
    a: "Está hecha para usarse con prisa: botones grandes, un toque por cada taco y colores para saber qué mesa urge. La cocina solo mira la pantalla.",
  },
  {
    q: "¿Qué pasa si se corta la señal?",
    a: "La mesera se vuelve a conectar sola en cuanto regresa la señal. Todo queda guardado en el teléfono de cocina, así que no se pierde ninguna orden.",
  },
  {
    q: "¿Qué teléfonos sirven?",
    a: "Cualquier teléfono Android 8 o más nuevo. No necesitas comprar nada especial.",
  },
  {
    q: "¿Puedo poner mi propio menú?",
    a: "Claro. Desde el teléfono de cocina agregas tus tacos, especiales y bebidas con sus precios, y opciones como «sin cebolla», «salsa aparte» o «doble tortilla».",
  },
  {
    q: "¿Cuánto cuesta?",
    a: "Escríbenos por WhatsApp y te pasamos el precio para tu taquería.",
  },
];

/* ------------------------------------------------------------------ */
/* Detalles visuales                                                   */
/* ------------------------------------------------------------------ */

const PAPEL_COLORS = ["#e4407b", "#f2b93b", "#3e8e6c", "#ee8434", "#d9472b", "#7d5fb5"];

/** Tira de papel picado: banderitas caladas colgando de un hilo. */
const PapelPicado = ({
  count = 18,
  className = "",
  colors = PAPEL_COLORS,
}: {
  count?: number;
  className?: string;
  colors?: string[];
}) => {
  const id = useId().replace(/:/g, "");
  const maskId = `pp-${id}`;
  return (
    <div className={`pointer-events-none relative overflow-hidden ${className}`} aria-hidden="true">
      <svg width="0" height="0" className="absolute">
        <defs>
          <mask id={maskId} maskUnits="userSpaceOnUse" x="0" y="0" width="60" height="70">
            <rect width="60" height="70" fill="white" />
            {/* Flor central */}
            <circle cx="30" cy="30" r="4" fill="black" />
            {[0, 60, 120, 180, 240, 300].map((deg) => (
              <ellipse
                key={deg}
                cx="30"
                cy="21"
                rx="3"
                ry="5"
                fill="black"
                transform={`rotate(${deg} 30 30)`}
              />
            ))}
            {/* Rombitos de las esquinas */}
            {[
              [10, 12],
              [50, 12],
              [10, 46],
              [50, 46],
            ].map(([x, y]) => (
              <rect key={`${x}-${y}`} x={x - 3} y={y - 3} width="6" height="6" fill="black" transform={`rotate(45 ${x} ${y})`} />
            ))}
            {/* Puntitos de abajo */}
            {[18, 30, 42].map((x) => (
              <circle key={x} cx={x} cy="52" r="1.8" fill="black" />
            ))}
          </mask>
        </defs>
      </svg>
      <div className="absolute inset-x-0 top-[5px] h-[2px] bg-carbon/25" />
      <div className="flex justify-center gap-1.5">
        {Array.from({ length: count }, (_, i) => (
          <svg
            key={i}
            viewBox="0 0 60 70"
            className="papel-flag h-14 w-12 shrink-0 sm:h-16 sm:w-14"
            style={{ ["--tilt" as string]: `${i % 2 ? 2.5 : -2.5}deg`, animationDelay: `${(i % 5) * -0.9}s` }}
          >
            <path
              d="M0 4 H60 V58 L55 64 L50 58 L45 64 L40 58 L35 64 L30 58 L25 64 L20 58 L15 64 L10 58 L5 64 L0 58 Z"
              fill={colors[i % colors.length]}
              mask={`url(#${maskId})`}
            />
          </svg>
        ))}
      </div>
    </div>
  );
};

/** Subrayado a mano, como plumón. */
const Scribble = ({ className = "" }: { className?: string }) => (
  <svg viewBox="0 0 300 20" preserveAspectRatio="none" className={className} aria-hidden="true">
    <path d="M4 14 C 60 4, 120 4, 170 10 S 260 16, 296 6" fill="none" stroke="currentColor" strokeWidth="7" strokeLinecap="round" />
  </svg>
);

/** Flechita dibujada a mano. */
const HandArrow = ({ className = "", flip = false }: { className?: string; flip?: boolean }) => (
  <svg viewBox="0 0 90 60" className={className} style={flip ? { transform: "scaleX(-1)" } : undefined} aria-hidden="true">
    <path d="M6 8 C 30 6, 62 14, 74 44" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
    <path d="M62 38 L75 47 L80 31" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
);

/** Teléfono con una captura real de la app. */
const Phone = ({
  src,
  alt,
  className = "",
  eager = false,
}: {
  src: string;
  alt: string;
  className?: string;
  eager?: boolean;
}) => (
  <div className={`rounded-[2.1rem] bg-carbon p-[7px] shadow-[0_28px_60px_-24px_rgba(43,29,20,0.55)] ${className}`}>
    <img
      src={src}
      alt={alt}
      width={1080}
      height={2400}
      loading={eager ? "eager" : "lazy"}
      decoding="async"
      className="block h-auto w-full rounded-[1.75rem]"
    />
  </div>
);

const WhatsAppButton = ({ children, tone = "green" }: { children: ReactNode; tone?: "green" | "cream" }) => (
  <a
    {...CONTACT_LINK}
    className={`inline-flex items-center justify-center gap-2.5 whitespace-nowrap rounded-full px-6 py-4 text-[16px] font-extrabold sm:px-7 sm:text-[17px] shadow-[0_6px_0_0_rgba(43,29,20,0.9)] transition active:translate-y-1 active:shadow-[0_2px_0_0_rgba(43,29,20,0.9)] hover:-translate-y-0.5 focus-visible:outline-3 focus-visible:outline-offset-4 focus-visible:outline-maiz ${
      tone === "green" ? "bg-cilantro text-white hover:bg-cilantro-deep" : "bg-masa text-carbon hover:bg-white"
    }`}
  >
    <FaWhatsapp size={22} aria-hidden="true" />
    {children}
  </a>
);

const Logo = ({ size = 40 }: { size?: number }) => (
  <span className="flex items-center gap-2.5">
    <img src="/android-chrome-192x192.png" alt="" width={size} height={size} className="rounded-xl" />
    <span className="font-display text-2xl tracking-wide text-carbon">Tacomanda</span>
  </span>
);

/* ------------------------------------------------------------------ */
/* Página                                                              */
/* ------------------------------------------------------------------ */

export default function App() {
  const [menuOpen, setMenuOpen] = useState(false);

  return (
    <div className="relative min-h-screen overflow-x-hidden">
      <PapelPicado className="pt-1" />

      {/* ── Encabezado ─────────────────────────────────────────── */}
      <header className="sticky top-0 z-40 bg-masa/95 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-4 py-3 sm:px-6">
          <a href="#inicio" aria-label="Tacomanda, inicio">
            <Logo />
          </a>
          <nav className="hidden items-center gap-8 text-[16px] font-bold text-carbon-soft md:flex" aria-label="Secciones">
            {NAV.map((item) => (
              <a key={item.href} href={item.href} className="transition hover:text-carbon">
                {item.label}
              </a>
            ))}
          </nav>
          <div className="flex items-center gap-2">
            <a
              {...CONTACT_LINK}
              className="hidden items-center gap-2 rounded-full bg-cilantro px-5 py-2.5 text-[15px] font-extrabold text-white transition hover:bg-cilantro-deep sm:inline-flex"
            >
              <FaWhatsapp size={18} aria-hidden="true" /> Escríbenos
            </a>
            <button
              type="button"
              className="inline-flex h-11 w-11 items-center justify-center rounded-full bg-papel text-carbon ring-1 ring-raya md:hidden"
              aria-label={menuOpen ? "Cerrar menú" : "Abrir menú"}
              aria-expanded={menuOpen}
              onClick={() => setMenuOpen((open) => !open)}
            >
              {menuOpen ? <LuX size={20} /> : <LuMenu size={20} />}
            </button>
          </div>
        </div>
        {menuOpen && (
          <nav className="border-t border-raya px-4 pb-5 pt-3 md:hidden" aria-label="Secciones (menú)">
            <div className="grid gap-1 text-lg font-bold">
              {NAV.map((item) => (
                <a key={item.href} href={item.href} className="rounded-xl px-2 py-2.5" onClick={() => setMenuOpen(false)}>
                  {item.label}
                </a>
              ))}
            </div>
          </nav>
        )}
      </header>

      <main>
        {/* ── Portada ──────────────────────────────────────────── */}
        <section id="inicio" className="mx-auto grid max-w-6xl gap-14 px-4 pb-20 pt-10 sm:px-6 lg:grid-cols-[1.05fr_1fr] lg:items-center lg:gap-10 lg:pb-28 lg:pt-16">
          <div>
            <p className="font-hand text-2xl text-salsa">¡Hecha para taquerías!</p>
            <h1 className="mt-3 font-display text-[3.2rem] leading-[0.98] text-carbon sm:text-7xl lg:text-[4.1rem] xl:text-[4.6rem]">
              Que no se pierda{" "}
              <span className="relative inline-block whitespace-nowrap">
                ni una orden.
                <Scribble className="absolute -bottom-2 left-0 h-4 w-full text-maiz" />
              </span>
            </h1>
            <p className="mt-7 max-w-xl text-xl leading-relaxed text-carbon-soft">
              La mesera anota en su teléfono y el taquero lo ve en la cocina al momento. Sin papelitos, sin gritos y con
              las cuentas claras a la hora de cobrar.
            </p>
            <div className="mt-9 flex flex-col items-start gap-5 sm:flex-row sm:items-center">
              <WhatsAppButton>Quiero probarla</WhatsAppButton>
              <a href="#como-se-usa" className="inline-flex items-center gap-2 px-2 text-[17px] font-bold text-carbon underline decoration-maiz decoration-4 underline-offset-[6px]">
                Ver cómo se usa <LuArrowDown size={18} aria-hidden="true" />
              </a>
            </div>
            <ul className="mt-10 flex flex-wrap gap-x-6 gap-y-3 text-[16px] font-bold">
              {["Funciona sin internet", "Solo dos teléfonos", "Cuentas claras"].map((item) => (
                <li key={item} className="flex items-center gap-2">
                  <span className="grid h-6 w-6 place-items-center rounded-full bg-cilantro text-white">
                    <LuCheck size={14} strokeWidth={3.5} aria-hidden="true" />
                  </span>
                  {item}
                </li>
              ))}
            </ul>
          </div>

          {/* Los dos teléfonos: mesera y cocina */}
          <div className="relative mx-auto w-full max-w-[460px] pb-6 pt-4 sm:pt-10">
            <div className="absolute inset-x-6 bottom-0 top-16 rounded-[3rem] bg-maiz/35" aria-hidden="true" />
            <div className="relative flex items-start justify-center">
              <div className="relative z-10 w-[47%] -rotate-6">
                <Phone src="/capturas/captura.png" alt="La mesera toma el pedido tocando cada taco" eager />
              </div>
              <div className="relative -ml-6 mt-14 w-[47%] rotate-[5deg]">
                <Phone src="/capturas/cocina.png" alt="La cocina ve cada mesa con su tiempo de espera" eager />
              </div>
            </div>

            <div className="absolute -left-2 -top-4 hidden text-carbon sm:block lg:-left-10">
              <p className="font-hand text-xl leading-tight">La mesera anota…</p>
              <HandArrow className="ml-8 h-10 w-14" />
            </div>
            <div className="absolute right-0 top-6 hidden text-right text-carbon sm:block">
              <p className="font-hand text-xl leading-tight">…y la cocina lo ve</p>
              <HandArrow className="ml-auto mr-6 h-10 w-14" flip />
            </div>

            <div className="sticker absolute -bottom-2 left-0 z-20 grid h-28 w-28 place-items-center rounded-full bg-salsa text-center text-white shadow-[0_10px_24px_-10px_rgba(43,29,20,0.6)] sm:-left-4 sm:h-32 sm:w-32">
              <span className="absolute inset-2 rounded-full border-2 border-dashed border-white/60" aria-hidden="true" />
              <span className="font-display text-lg leading-[1.05] sm:text-xl">
                ¡Sin
                <br />
                internet!
              </span>
            </div>
          </div>
        </section>

        {/* ── ¿Te ha pasado? ───────────────────────────────────── */}
        <section className="bg-papel py-20 lg:py-28">
          <div className="mx-auto max-w-6xl px-4 sm:px-6">
            <h2 className="text-center font-display text-4xl text-balance text-carbon sm:text-5xl">¿Te ha pasado?</h2>
            <div className="mt-14 grid gap-8 sm:grid-cols-2 lg:grid-cols-4 lg:gap-6">
              {PAINS.map((pain) => (
                <div key={pain.text} className={`tape comanda-paper relative min-h-44 rounded-sm px-5 pb-6 pl-12 pt-7 shadow-[0_12px_24px_-14px_rgba(43,29,20,0.45)] ${pain.tilt}`}>
                  <p className="font-hand text-[22px] leading-[28px] text-[#1f3b7a]">{pain.text}</p>
                </div>
              ))}
            </div>
            <p className="mx-auto mt-16 max-w-2xl text-center font-display text-3xl leading-tight text-cilantro-deep sm:text-4xl">
              Con Tacomanda, todo eso se acaba.
            </p>
          </div>
        </section>

        {/* ── Cómo se usa ──────────────────────────────────────── */}
        <section id="como-se-usa" className="mx-auto max-w-6xl scroll-mt-24 px-4 py-20 sm:px-6 lg:py-28">
          <div className="mx-auto max-w-2xl text-center">
            <p className="font-hand text-2xl text-salsa">Así de fácil</p>
            <h2 className="mt-2 font-display text-4xl text-balance text-carbon sm:text-5xl">De la mesa a la cocina, en tres pasos</h2>
          </div>

          <ol className="mt-16 grid gap-20 lg:gap-24">
            {STEPS.map((step, index) => (
              <li key={step.n} className="grid items-center gap-10 md:grid-cols-2 md:gap-16">
                <div className={`relative mx-auto w-full max-w-[290px] ${index % 2 ? "md:order-2" : ""}`}>
                  <div className={`absolute inset-0 -z-10 translate-x-5 translate-y-5 rounded-[2.4rem] ${["bg-maiz/40", "bg-rosa/20", "bg-cilantro/25"][index]}`} aria-hidden="true" />
                  <Phone src={step.img} alt={step.alt} className={index % 2 ? "rotate-2" : "-rotate-2"} />
                </div>
                <div>
                  <span className="grid h-16 w-16 place-items-center rounded-full bg-carbon font-display text-3xl text-maiz">{step.n}</span>
                  <h3 className="mt-5 font-display text-3xl text-carbon sm:text-4xl">{step.title}</h3>
                  <p className="mt-4 text-lg leading-relaxed text-carbon-soft">{step.text}</p>
                  {step.extra}
                  <p className="mt-6 inline-flex items-start gap-2 rounded-2xl bg-maiz/25 px-4 py-3 font-hand text-xl leading-snug text-carbon">
                    <span aria-hidden="true">✍️</span> {step.tip}
                  </p>
                </div>
              </li>
            ))}
          </ol>
        </section>

        {/* ── Detallitos ───────────────────────────────────────── */}
        <section className="bg-cilantro-deep py-20 text-masa lg:py-28">
          <div className="mx-auto max-w-6xl px-4 sm:px-6">
            <div className="mx-auto max-w-2xl text-center">
              <p className="font-hand text-2xl text-maiz">Y otros detallitos</p>
              <h2 className="mt-2 font-display text-4xl text-balance sm:text-5xl">Pensada para el ajetreo de la taquería</h2>
            </div>

            <div className="mt-14 grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
              {EXTRAS.map((extra) => (
                <article key={extra.title} className="flex flex-col overflow-hidden rounded-3xl bg-papel text-carbon">
                  <div className="h-56 overflow-hidden bg-[#eef1ef] px-6 pt-6">
                    <img
                      src={extra.img}
                      alt={extra.alt}
                      width={1080}
                      height={2400}
                      loading="lazy"
                      decoding="async"
                      className="mx-auto w-full max-w-[200px] rounded-t-2xl shadow-[0_10px_30px_-12px_rgba(43,29,20,0.45)]"
                    />
                  </div>
                  <div className="p-6">
                    <h3 className="font-display text-2xl">{extra.title}</h3>
                    <p className="mt-2 text-[16px] leading-relaxed text-carbon-soft">{extra.text}</p>
                  </div>
                </article>
              ))}

              {/* Pizarrón con el menú */}
              <article className="flex flex-col overflow-hidden rounded-3xl bg-papel text-carbon">
                <div className="h-56 bg-[#8a5a3a] p-3">
                  <div className="pizarron h-full rounded-xl px-5 py-4 text-masa">
                    <p className="text-center font-hand text-xl text-maiz">Tacos</p>
                    <ul className="mt-1 grid gap-0.5 font-hand text-lg">
                      {MENU_BOARD.map((item) => (
                        <li key={item.name} className="flex items-baseline gap-2">
                          <span>{item.name}</span>
                          <span className="flex-1 border-b-2 border-dotted border-masa/30" aria-hidden="true" />
                          <span className="text-maiz">{item.price}</span>
                        </li>
                      ))}
                    </ul>
                  </div>
                </div>
                <div className="p-6">
                  <h3 className="font-display text-2xl">Tu menú, tus precios</h3>
                  <p className="mt-2 text-[16px] leading-relaxed text-carbon-soft">
                    Pones tus tacos, especiales y bebidas tal como los vendes. Los cambias cuando quieras.
                  </p>
                </div>
              </article>
            </div>
          </div>
        </section>

        {/* ── Qué necesitas ────────────────────────────────────── */}
        <section id="que-necesitas" className="mx-auto max-w-6xl scroll-mt-24 px-4 py-20 sm:px-6 lg:py-28">
          <div className="mx-auto max-w-2xl text-center">
            <p className="font-hand text-2xl text-salsa">Sin complicaciones</p>
            <h2 className="mt-2 font-display text-4xl text-balance text-carbon sm:text-5xl">¿Qué necesitas para empezar?</h2>
          </div>
          <div className="mt-14 grid gap-6 md:grid-cols-3">
            {NEEDS.map((need, index) => {
              const Icon = need.icon;
              return (
                <div key={need.title} className="rounded-3xl bg-papel p-8 ring-1 ring-raya">
                  <span className={`grid h-16 w-16 place-items-center rounded-2xl text-white ${["bg-chile", "bg-rosa", "bg-cilantro"][index]}`}>
                    <Icon size={30} strokeWidth={2.4} aria-hidden="true" />
                  </span>
                  <h3 className="mt-6 font-display text-2xl text-carbon">{need.title}</h3>
                  <p className="mt-2 text-[17px] leading-relaxed text-carbon-soft">{need.text}</p>
                </div>
              );
            })}
          </div>
        </section>

        {/* ── Preguntas ────────────────────────────────────────── */}
        <section id="preguntas" className="mx-auto max-w-3xl scroll-mt-24 px-4 pb-20 sm:px-6 lg:pb-28">
          <div className="text-center">
            <p className="font-hand text-2xl text-salsa">Lo que más nos preguntan</p>
            <h2 className="mt-2 font-display text-4xl text-balance text-carbon sm:text-5xl">Preguntas</h2>
          </div>
          <div className="mt-12 grid gap-3">
            {FAQ.map((item) => (
              <details key={item.q} className="group rounded-2xl bg-papel ring-1 ring-raya open:ring-cilantro/40">
                <summary className="flex cursor-pointer items-center justify-between gap-4 rounded-2xl px-6 py-5 text-lg font-extrabold focus-visible:outline-3 focus-visible:outline-maiz">
                  {item.q}
                  <LuChevronDown size={22} className="faq-chevron shrink-0 text-cilantro transition-transform" aria-hidden="true" />
                </summary>
                <p className="px-6 pb-6 text-[17px] leading-relaxed text-carbon-soft">{item.a}</p>
              </details>
            ))}
          </div>
        </section>

        {/* ── Llamado final ────────────────────────────────────── */}
        <section className="px-4 pb-16 sm:px-6">
          <div className="relative mx-auto max-w-6xl overflow-hidden rounded-[2.5rem] bg-salsa px-6 pb-16 text-center text-white sm:px-12">
            <PapelPicado count={14} className="-mx-6 sm:-mx-12" colors={["#f2b93b", "#3e8e6c", "#fbf4e6", "#7d5fb5", "#e4407b"]} />
            <p className="mt-8 font-hand text-2xl text-maiz">¿Qué dices?</p>
            <h2 className="mx-auto mt-2 max-w-2xl font-display text-4xl leading-tight text-balance sm:text-6xl">¿Le damos una probadita?</h2>
            <p className="mx-auto mt-5 max-w-xl text-xl leading-relaxed text-white/90">
              Mándanos un WhatsApp y te mostramos cómo funcionaría en tu taquería.
            </p>
            <div className="mt-10">
              <WhatsAppButton tone="cream">Escríbenos por WhatsApp</WhatsAppButton>
            </div>
          </div>
        </section>
      </main>

      <footer className="border-t border-raya">
        <div className="mx-auto flex max-w-6xl flex-col items-center justify-between gap-3 px-4 py-8 text-[15px] text-carbon-soft sm:flex-row sm:px-6">
          <Logo size={32} />
          <div className="flex flex-col items-center gap-1 sm:items-end">
            <p>Comandas para taquerías · © {new Date().getFullYear()} Tacomanda</p>
            <p>
              Powered by{" "}
              <a
                href="https://axzy.dev"
                target="_blank"
                rel="noopener"
                className="font-extrabold text-carbon underline decoration-maiz decoration-2 underline-offset-4 transition hover:text-cilantro-deep"
              >
                axzy.dev
              </a>
            </p>
          </div>
        </div>
      </footer>
    </div>
  );
}
