import { useEffect, useRef, type ReactNode } from "react";
import lottie from "lottie-web";
import { ITAccordion, ITCard } from "@axzydev/axzy_ui_system";
import {
  LuArrowRight,
  LuCheck,
  LuChefHat,
  LuCreditCard,
  LuHandPlatter,
  LuMenu,
  LuRadio,
  LuScissors,
  LuSmartphone,
  LuTimer,
  LuUndo2,
  LuUtensils,
  LuWifiOff,
} from "react-icons/lu";
import type { IconType } from "react-icons";
import listoAnimation from "./assets/listo.json";
import { CONTACT_LINK } from "./config";

const NAV = [
  { href: "#como-funciona", label: "Cómo funciona" },
  { href: "#funciones", label: "Funciones" },
  { href: "#preguntas", label: "Preguntas" },
];

const FACTS = [
  { value: "0", label: "internet para operar", sub: "todo viaja por Wi-Fi local" },
  { value: "2", label: "teléfonos en la taquería", sub: "cocina y mesera" },
  { value: "3", label: "colores de espera", sub: "verde, amarillo y rojo en cocina" },
  { value: "+1", label: "por cada toque", sub: "captura rápida, sin formularios" },
];

type Feature = { icon: IconType; title: string; text: string; points: string[]; span: string };

const FEATURES: Feature[] = [
  {
    icon: LuHandPlatter,
    title: "Captura en segundos",
    text: "Cada toque suma un producto a la cuenta. Sin formularios, sin escribir cantidades.",
    points: ["Toca para sumar, mantén para ver opciones", "Cuentas abiertas por mesa", "Deshacer durante unos segundos"],
    span: "lg:col-span-2",
  },
  {
    icon: LuChefHat,
    title: "Cocina sin tocar nada",
    text: "El monitor de cocina muestra un ticket por mesa, la más antigua primero, con la hora de recepción.",
    points: ["Color según el tiempo de espera", "Alerta roja al pasar los 15 minutos", "El taquero solo mira"],
    span: "",
  },
  {
    icon: LuScissors,
    title: "Dividir preparaciones",
    text: "Cuatro tacos que salen en dos tandas: divídelos en 2 + 2, cada grupo con sus propias notas.",
    points: ["Modificaciones por grupo", "Notas independientes", "Sin perder el pedido original"],
    span: "",
  },
  {
    icon: LuCreditCard,
    title: "Cobro con prueba",
    text: "Efectivo o transferencia, con pagos parciales. Una transferencia se confirma antes de cerrar la cuenta.",
    points: ["Pagos parciales por cuenta", "Transferencia con confirmación", "Cuenta cerrada al pagar y entregar"],
    span: "lg:col-span-2",
  },
  {
    icon: LuWifiOff,
    title: "Funciona sin internet",
    text: "Los teléfonos se hablan por Wi-Fi local o por el hotspot del teléfono de cocina. Internet solo confirma la cuenta de la taquería.",
    points: ["Sin router con internet", "La mesera se reconecta sola", "La cocina conserva todo"],
    span: "",
  },
  {
    icon: LuUtensils,
    title: "Menú a tu medida",
    text: "Productos, precios y modificaciones se configuran desde cocina. Elige si cada modificación es exclusiva o combinable.",
    points: ["Modificaciones exclusivas o combinables", "Cambios al instante en todas las meseras", "Un solo catálogo"],
    span: "",
  },
];

const STEPS = [
  {
    icon: LuSmartphone,
    title: "Instala en dos teléfonos",
    text: "La app es para Android. Pásale el APK al teléfono de cocina y al de la mesera, y ábrela en cada uno.",
  },
  {
    icon: LuRadio,
    title: "Cocina abre el servidor",
    text: "Elige el rol Cocina. Su teléfono guarda la fuente de verdad y anuncia la taquería en la red local.",
  },
  {
    icon: LuUndo2,
    title: "Mesera toma, envía y cobra",
    text: "La mesera se conecta sola, toma la orden, la envía a cocina y cobra. Cada cambio aparece al instante.",
  },
];

const FAQ = [
  {
    id: "internet",
    title: "¿Necesito internet para usar Tacomanda?",
    content:
      "No. Los teléfonos se comunican por la red Wi-Fi de la taquería o por el hotspot del teléfono de cocina. Internet solo se usa para confirmar que la cuenta de la taquería está activa.",
  },
  {
    id: "equipo",
    title: "¿Qué equipo necesito?",
    content:
      "Un teléfono Android para cocina y uno para la mesera. Cocina es el que guarda los datos; el de la mesera no guarda nada de negocio.",
  },
  {
    id: "corte",
    title: "¿Qué pasa si se cae la red?",
    content:
      "La mesera detecta la desconexión y se reconecta sola. Como cocina transmite el estado completo en cada cambio, nada se pierde al volver la señal.",
  },
  {
    id: "dividir",
    title: "¿Puedo dividir un producto en dos tandas?",
    content:
      "Sí. En Revisar, toca el producto y usa Dividir preparación (por ejemplo 4 → 2 + 2). Cada grupo lleva sus propias modificaciones y notas.",
  },
  {
    id: "cancelar",
    title: "¿Me puedo equivocar al cancelar o cobrar?",
    content:
      "Sí, y se puede deshacer. Cancelar y cobrar muestran la opción Deshacer durante unos segundos. Las transferencias requieren confirmar que llegaron.",
  },
];

const SectionHead = ({ id, eyebrow, title, text }: { id?: string; eyebrow: string; title: string; text?: string }) => (
  <div id={id} className="mx-auto max-w-2xl scroll-mt-24 text-center">
    <p className="text-sm font-bold uppercase tracking-[0.18em] text-brand-green-deep">{eyebrow}</p>
    <h2 className="mt-3 text-3xl font-black leading-tight tracking-tight text-brand-ink sm:text-4xl">{title}</h2>
    {text && <p className="mx-auto mt-4 max-w-xl text-base leading-relaxed text-brand-muted">{text}</p>}
  </div>
);

const PointList = ({ items }: { items: string[] }) => (
  <ul className="mt-5 grid gap-2">
    {items.map((item) => (
      <li key={item} className="flex items-start gap-2 text-sm leading-snug text-brand-ink">
        <LuCheck size={16} strokeWidth={3} className="mt-0.5 shrink-0 text-brand-green" />
        <span>{item}</span>
      </li>
    ))}
  </ul>
);

/** Ticket de cocina de ejemplo: muestra cómo se ve el monitor, con los tres colores de espera. */
const KitchenTicket = ({ table, age, tone, lines }: { table: string; age: string; tone: "ok" | "warn" | "late"; lines: string[] }) => {
  const toneClass = {
    ok: "bg-brand-green text-white",
    warn: "bg-brand-gold text-brand-ink",
    late: "bg-brand-orange text-white anim-pulse-red",
  }[tone];
  return (
    <div className="overflow-hidden rounded-2xl bg-white shadow-[0_18px_40px_-20px_rgba(31,42,36,0.35)] ring-1 ring-brand-ink/5">
      <div className={`flex items-center justify-between gap-2 px-4 py-2.5 text-sm font-bold whitespace-nowrap ${toneClass}`}>
        <span>{table}</span>
        <span className="rounded-full bg-white/25 px-2.5 py-0.5 text-xs">{age}</span>
      </div>
      <ul className="grid gap-1.5 px-4 py-3 text-sm">
        {lines.map((line) => (
          <li key={line} className="text-brand-ink">
            {line}
          </li>
        ))}
      </ul>
    </div>
  );
};

/** Animación "listo" de la marca, reproducida una vez. */
const LottieBadge = () => {
  const container = useRef<HTMLSpanElement>(null);
  useEffect(() => {
    if (!container.current) return;
    const anim = lottie.loadAnimation({
      container: container.current,
      renderer: "svg",
      loop: false,
      autoplay: true,
      animationData: listoAnimation,
    });
    return () => anim.destroy();
  }, []);
  return <span ref={container} className="block h-14 w-14 shrink-0" aria-hidden="true" />;
};

const Feature = ({ feature }: { feature: Feature }) => {
  const Icon = feature.icon;
  return (
    <ITCard className={`${feature.span} !rounded-3xl !border-brand-ink/10 !bg-white !p-6 !shadow-none sm:!p-7`}>
      <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-brand-green/12 text-brand-green-deep">
        <Icon size={22} />
      </span>
      <h3 className="mt-5 text-xl font-extrabold tracking-tight text-brand-ink">{feature.title}</h3>
      <p className="mt-2 text-[15px] leading-relaxed text-brand-muted">{feature.text}</p>
      <PointList items={feature.points} />
    </ITCard>
  );
};

const CtaLink = ({
  children,
  variant = "primary",
  href = CONTACT_LINK.href,
  external = true,
}: {
  children: ReactNode;
  variant?: "primary" | "ghost";
  href?: string;
  external?: boolean;
}) => {
  const base =
    "inline-flex items-center justify-center gap-2 rounded-full px-6 py-3.5 text-[15px] font-bold transition hover:-translate-y-0.5 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-green";
  const style =
    variant === "primary"
      ? "bg-brand-green text-white shadow-[0_12px_28px_-12px_rgba(62,142,108,0.8)] hover:bg-brand-green-deep"
      : "bg-white text-brand-ink ring-1 ring-brand-ink/15 hover:ring-brand-green";
  return (
    <a
      href={href}
      {...(external ? { target: "_blank", rel: "noopener noreferrer" } : {})}
      className={`${base} ${style}`}
    >
      {children}
    </a>
  );
};

export default function App() {
  return (
    <div className="relative min-h-screen overflow-hidden">
      {/* ── Navegación ───────────────────────────────────────── */}
      <header className="sticky top-0 z-50 border-b border-brand-ink/10 bg-brand-paper/90 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-4 py-3 sm:px-6">
          <a href="#inicio" className="flex items-center gap-2.5" aria-label="Tacomanda, inicio">
            <img src="/android-chrome-192x192.png" alt="" width={36} height={36} className="h-9 w-9 rounded-xl" />
            <span className="text-lg font-black tracking-tight">Tacomanda</span>
          </a>
          <nav className="hidden items-center gap-7 text-sm font-semibold text-brand-muted md:flex" aria-label="Secciones">
            {NAV.map((item) => (
              <a key={item.href} href={item.href} className="transition hover:text-brand-ink">
                {item.label}
              </a>
            ))}
          </nav>
          <div className="flex items-center gap-2">
            <a
              {...CONTACT_LINK}
              className="hidden rounded-full bg-brand-green px-4 py-2 text-sm font-bold text-white transition hover:bg-brand-green-deep sm:inline-flex"
            >
              Solicitar demo
            </a>
            <button
              type="button"
              className="inline-flex h-10 w-10 items-center justify-center rounded-full text-brand-ink ring-1 ring-brand-ink/15 md:hidden"
              aria-label="Abrir menú"
              onClick={() => document.getElementById("menu-movil")?.toggleAttribute("hidden")}
            >
              <LuMenu size={18} />
            </button>
          </div>
        </div>
        <nav id="menu-movil" hidden className="border-t border-brand-ink/10 bg-brand-paper px-4 py-4 md:hidden" aria-label="Secciones (menú)">
          <div className="grid gap-3 text-[15px] font-semibold">
            {NAV.map((item) => (
              <a key={item.href} href={item.href}>
                {item.label}
              </a>
            ))}
          </div>
        </nav>
      </header>

      <main>
        {/* ── Hero ─────────────────────────────────────────────── */}
        <section id="inicio" className="mx-auto grid max-w-6xl gap-14 px-4 pb-20 pt-14 sm:px-6 lg:grid-cols-[1fr_1.05fr] lg:items-center lg:gap-12 lg:pb-28 lg:pt-24">
          <div className="anim-rise">
            <span className="inline-flex items-center gap-2 rounded-full bg-white px-3.5 py-1.5 text-sm font-semibold text-brand-green-deep ring-1 ring-brand-green/25">
              <span className="h-2 w-2 rounded-full bg-brand-green" aria-hidden="true" />
              Mesera, cocina y cobro en una sola app
            </span>
            <h1 className="mt-6 text-5xl font-black leading-[1.02] tracking-tight text-brand-ink sm:text-6xl">
              Tu taquería, <span className="text-brand-orange">en orden</span> desde la mesa hasta la cocina.
            </h1>
            <p className="mt-6 max-w-xl text-lg leading-relaxed text-brand-muted">
              Toma la orden en segundos, mándala a cocina al instante y cobra en efectivo o transferencia. Todo funciona
              en la red de tu taquería, aunque no haya internet.
            </p>
            <div className="mt-9 flex flex-col gap-3 sm:flex-row">
              <CtaLink>
                Solicitar demo <LuArrowRight size={18} />
              </CtaLink>
              <CtaLink variant="ghost" href="#como-funciona" external={false}>
                Cómo funciona
              </CtaLink>
            </div>
            <ul className="mt-9 flex flex-wrap gap-x-6 gap-y-2 text-sm font-semibold text-brand-ink">
              {["Sin internet para operar", "Cocina en tiempo real", "Cobros con confirmación"].map((item) => (
                <li key={item} className="flex items-center gap-1.5">
                  <LuCheck size={16} strokeWidth={3} className="text-brand-green" /> {item}
                </li>
              ))}
            </ul>
          </div>

          <div className="anim-rise relative mx-auto w-full max-w-lg lg:max-w-none" style={{ animationDelay: "120ms" }}>
            <div className="rounded-[28px] bg-brand-cream p-5 shadow-[0_30px_70px_-30px_rgba(31,42,36,0.35)] ring-1 ring-brand-gold/30 sm:p-7">
              <div className="mb-4 flex items-center justify-between">
                <p className="text-sm font-extrabold text-brand-ink">Monitor de cocina</p>
                <span className="flex items-center gap-1.5 text-xs font-semibold text-brand-green-deep">
                  <span className="h-2 w-2 rounded-full bg-brand-green" aria-hidden="true" /> En vivo
                </span>
              </div>
              <div className="grid gap-3 sm:grid-cols-2">
                <KitchenTicket
                  table="Mesa 4"
                  age="3 min"
                  tone="ok"
                  lines={["2× Taco al pastor", "1× Quesadilla de flor", "1× Agua de jamaica"]}
                />
                <KitchenTicket table="Mesa 1" age="9 min" tone="warn" lines={["4 → 2 + 2 · Tacos de bistec", "1× Refresco"]} />
                <KitchenTicket table="Mesa 7" age="16 min" tone="late" lines={["3× Tacos de cabeza", "2× Sopes"]} />
              </div>
            </div>

            <div className="anim-float absolute -bottom-6 right-3 flex sm:right-6 items-center gap-3 rounded-2xl bg-white px-4 py-3 shadow-[0_20px_40px_-18px_rgba(31,42,36,0.4)] ring-1 ring-brand-ink/10">
              <LottieBadge />
              <div className="min-w-0">
                <p className="text-sm font-extrabold leading-tight">Enviado a cocina</p>
                <p className="text-xs leading-tight text-brand-muted">Mesa 4 · 2 productos · ahora</p>
              </div>
            </div>
          </div>
        </section>

        {/* ── Cifras ───────────────────────────────────────────── */}
        <section className="mx-auto max-w-6xl px-4 sm:px-6" aria-label="Datos clave">
          <dl className="grid grid-cols-2 overflow-hidden rounded-3xl border border-brand-ink/10 bg-white lg:grid-cols-4">
            {FACTS.map((fact) => (
              <div key={fact.label} className="px-5 py-6 sm:px-7">
                <dt className="sr-only">{fact.label}</dt>
                <dd className="text-4xl font-black leading-none tracking-tight text-brand-ink sm:text-5xl">{fact.value}</dd>
                <p className="mt-2 text-sm font-bold">{fact.label}</p>
                <p className="text-[13px] text-brand-muted">{fact.sub}</p>
              </div>
            ))}
          </dl>
        </section>

        {/* ── Cómo funciona ────────────────────────────────────── */}
        <section id="como-funciona" className="mx-auto max-w-6xl scroll-mt-24 px-4 py-24 sm:px-6 lg:py-32">
          <SectionHead
            eyebrow="Cómo funciona"
            title="De la mesa a la cocina en tres pasos"
            text="Dos teléfonos y una red local. Sin configuración de servidores ni cuentas por empleado."
          />
          <ol className="mt-14 grid gap-5 md:grid-cols-3">
            {STEPS.map((step, index) => {
              const Icon = step.icon;
              return (
                <li key={step.title} className="rounded-3xl bg-white p-7 ring-1 ring-brand-ink/10">
                  <div className="flex items-center justify-between">
                    <span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-brand-green text-white">
                      <Icon size={22} />
                    </span>
                    <span className="text-4xl font-black text-brand-ink/10">0{index + 1}</span>
                  </div>
                  <h3 className="mt-6 text-xl font-extrabold tracking-tight">{step.title}</h3>
                  <p className="mt-2 text-[15px] leading-relaxed text-brand-muted">{step.text}</p>
                </li>
              );
            })}
          </ol>
        </section>

        {/* ── Funciones ────────────────────────────────────────── */}
        <section id="funciones" className="mx-auto max-w-6xl scroll-mt-24 px-4 pb-24 sm:px-6 lg:pb-32">
          <SectionHead
            eyebrow="Funciones"
            title="Pensado para la prisa de una taquería"
            text="Cada detalle está para evitar errores en hora pico: captura rápida, cocina clara y cobros con prueba."
          />
          <div className="mt-14 grid gap-5 lg:grid-cols-3">
            {FEATURES.map((feature) => (
              <Feature key={feature.title} feature={feature} />
            ))}
          </div>
        </section>

        {/* ── Preguntas ────────────────────────────────────────── */}
        <section id="preguntas" className="mx-auto max-w-3xl scroll-mt-24 px-4 pb-24 sm:px-6 lg:pb-32">
          <SectionHead eyebrow="Preguntas" title="Lo que más preguntan las taquerías" />
          <div className="mt-12">
            <ITAccordion items={FAQ} variant="separated" />
          </div>
        </section>

        {/* ── CTA final ────────────────────────────────────────── */}
        <section className="mx-auto max-w-6xl px-4 pb-20 sm:px-6">
          <div className="relative overflow-hidden rounded-[32px] bg-brand-green-deep px-6 py-14 text-center text-white sm:px-12 sm:py-20">
            <div className="pointer-events-none absolute -right-24 -top-24 h-72 w-72 rounded-full bg-brand-green/40 blur-3xl" aria-hidden="true" />
            <LuTimer className="mx-auto text-brand-gold" size={28} aria-hidden="true" />
            <h2 className="relative mx-auto mt-5 max-w-2xl text-3xl font-black leading-tight tracking-tight sm:text-5xl">
              Lista tu próximo servicio sin perder ni una orden.
            </h2>
            <p className="relative mx-auto mt-5 max-w-xl text-base leading-relaxed text-white/80">
              Te mostramos la app en tu taquería o en una demo de 15 minutos.
            </p>
            <div className="relative mt-9 flex flex-col items-center justify-center gap-3 sm:flex-row">
              <a
                {...CONTACT_LINK}
                className="inline-flex items-center gap-2 rounded-full bg-brand-cream px-7 py-3.5 text-[15px] font-bold text-brand-ink transition hover:-translate-y-0.5"
              >
                Solicitar demo <LuArrowRight size={18} />
              </a>
            </div>
          </div>
        </section>
      </main>

      <footer className="border-t border-brand-ink/10">
        <div className="mx-auto flex max-w-6xl flex-col items-center justify-between gap-3 px-4 py-8 text-sm text-brand-muted sm:flex-row sm:px-6">
          <p className="flex items-center gap-2 font-semibold text-brand-ink">
            <img src="/android-chrome-192x192.png" alt="" width={24} height={24} className="h-6 w-6 rounded-md" />
            Tacomanda
          </p>
          <p>© {new Date().getFullYear()} Tacomanda. Todos los derechos reservados.</p>
        </div>
      </footer>
    </div>
  );
}
