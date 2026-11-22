"""Genera los íconos que faltaban, con el mismo estilo que el set entregado (lienzo 128,
mismos colores de tortilla, carne, verdura y queso). Escribe los SVG junto a los demás.

    python3 design/iconos/generar_faltantes.py
"""
from pathlib import Path

SVG = Path(__file__).parent / "svg"

TORTILLA = "#F6C85F"
TORTILLA_OSCURA = "#E0A53A"
TORTILLA_BRILLO = "#FBE3A0"
CARNE = "#7B4B2F"
CARNE_BORDE = "#5C3720"
CARNE_BRILLO = "#A06A47"
QUESO = "#FFD34D"
QUESO_BRILLO = "#FFEB99"
VERDES = ("#2F7D4F", "#3FA05F", "#5DBB7A")
CEBOLLA = "#F6F0DC"
ROJO = "#D9472B"


def doc(name: str, body: str) -> str:
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 128 128" width="128" height="128" '
        f'role="img" aria-label="{name}"><title>{name}</title>{body}</svg>'
    )


def cilantro(x: float, y: float) -> str:
    a, b, c = VERDES
    return (f'<circle cx="{x}" cy="{y}" r="3.2" fill="{a}"/><circle cx="{x + 3}" cy="{y - 1}" r="2.6" fill="{b}"/>'
            f'<circle cx="{x - 2.5}" cy="{y - 2}" r="2.3" fill="{c}"/>')


def trozo(cx: float, cy: float, rx: float, ry: float, rot: float = 0) -> str:
    return (f'<ellipse cx="{cx}" cy="{cy}" rx="{rx}" ry="{ry}" fill="{CARNE}" stroke="{CARNE_BORDE}" stroke-width="1.2" transform="rotate({rot} {cx} {cy})"/>'
            f'<ellipse cx="{cx - rx * .25}" cy="{cy - ry * .3}" rx="{rx * .5}" ry="{ry * .35}" fill="{CARNE_BRILLO}" opacity=".7" transform="rotate({rot} {cx} {cy})"/>')


def quesataco() -> str:
    """El taco de asada del set con queso derretido escurriendo sobre la tortilla."""
    base = (SVG / "taco_asada.svg").read_text()
    queso = (
        f'<path d="M15 63 Q64 56 113 63 L113 69 C109 74 105 82 100 74 C95 71 91 82 85 74 L71 75 '
        f'C67 88 59 88 57 75 L43 75 C39 84 31 84 29 74 C24 77 17 76 15 70 Z" fill="{QUESO}"/>'
        f'<ellipse cx="42" cy="65" rx="8" ry="2" fill="{QUESO_BRILLO}" opacity=".9"/>'
        f'<ellipse cx="88" cy="66" rx="7" ry="2" fill="{QUESO_BRILLO}" opacity=".9"/>'
    )
    # El queso va encima de la tortilla de enfrente para que se vea escurrir.
    end = base.rindex("</g>")
    out = base[:end] + queso + base[end:]
    return out.replace('aria-label="Asada"><title>Asada</title>', 'aria-label="Quesataco"><title>Quesataco</title>')


def burrito() -> str:
    relleno = "".join([
        f'<ellipse cx="30" cy="64" rx="9" ry="19" fill="{TORTILLA_OSCURA}"/>',
        f'<ellipse cx="31" cy="64" rx="6" ry="15" fill="{CARNE}"/>',
        f'<circle cx="31" cy="55" r="2.4" fill="{VERDES[1]}"/><circle cx="29" cy="62" r="2.2" fill="{ROJO}"/>',
        f'<circle cx="33" cy="68" r="2.4" fill="{VERDES[2]}"/><rect x="28" y="71" width="6" height="3.5" rx="1.7" fill="{CEBOLLA}"/>',
        f'<circle cx="32" cy="58" r="1.8" fill="{CEBOLLA}"/>',
    ])
    cuerpo = (
        f'<rect x="28" y="44" width="76" height="40" rx="20" fill="{TORTILLA}"/>'
        f'<path d="M40 50 C58 46 72 46 84 48" fill="none" stroke="{TORTILLA_BRILLO}" stroke-width="3.5" stroke-linecap="round"/>'
        f'<path d="M44 78 C60 82 74 82 88 79" fill="none" stroke="{TORTILLA_OSCURA}" stroke-width="2.5" stroke-linecap="round" opacity=".55"/>'
        f'<path d="M58 45 C54 56 54 72 58 83" fill="none" stroke="{TORTILLA_OSCURA}" stroke-width="2.5" stroke-linecap="round" opacity=".5"/>'
        # Papel aluminio en la mitad de atrás.
        f'<path d="M72 43.5 L84 43.5 A20 20 0 0 1 104 64 A20 20 0 0 1 84 84.5 L72 84.5 Z" fill="#C9CED6"/>'
        f'<path d="M78 46 L80 82 M88 46 L90 82 M97 50 L98 78" stroke="#E6E9EE" stroke-width="2.2" stroke-linecap="round"/>'
        f'<path d="M72 43.5 L76 50 L72 56 L76 63 L72 70 L76 77 L72 84.5" fill="none" stroke="#AEB4BD" stroke-width="1.6" stroke-linejoin="round"/>'
    )
    return doc("Burrito", f'<g transform="translate(64 64) scale(1.3) translate(-64 -64) rotate(-30 64 64)">{cuerpo}{relleno}</g>')


def vampiro() -> str:
    """Tortilla doradita y crujiente, aplanada, con queso gratinado y carne encima."""
    crujiente = "".join(
        f'<ellipse cx="{x}" cy="{y}" rx="{r}" ry="{r * .55}" fill="#B9741F" opacity=".55"/>'
        for x, y, r in ((24, 76, 3), (104, 74, 2.6), (40, 86, 2.4), (90, 86, 2.8), (66, 90, 2.2), (30, 68, 2))
    )
    carne = "".join(trozo(*t) for t in ((48, 68, 8, 5, -10), (66, 63, 9, 5.5, 8), (82, 70, 8, 5, 15), (60, 74, 8, 4.5, 0)))
    body = (
        f'<ellipse cx="64" cy="80" rx="54" ry="19" fill="#C98A2B"/>'
        f'<ellipse cx="64" cy="75" rx="54" ry="19" fill="#E8B04A"/>'
        f'<path d="M12 75 C16 70 20 77 26 72 C32 68 36 74 42 70" fill="none" stroke="#D9952F" stroke-width="2" stroke-linecap="round"/>'
        f'{crujiente}'
        f'<path d="M24 72 C30 62 48 58 64 58 C82 58 100 62 104 72 C100 80 96 76 92 81 C86 84 82 78 76 83 '
        f'C70 86 64 80 58 84 C52 86 46 79 40 82 C34 84 30 77 24 72 Z" fill="{QUESO}"/>'
        f'<ellipse cx="50" cy="64" rx="9" ry="2.4" fill="{QUESO_BRILLO}" opacity=".85"/>'
        f'{carne}{cilantro(58, 66)}{cilantro(76, 72)}'
        f'<rect x="69" y="60" width="7" height="4" rx="2" fill="{CEBOLLA}" transform="rotate(-20 72 62)"/>'
        f'<rect x="44" y="70" width="7" height="4" rx="2" fill="{CEBOLLA}" transform="rotate(15 48 72)"/>'
    )
    return doc("Vampiro", f'<g transform="translate(64 64) scale(1.15) translate(-64 -70)">{body}</g>')


def orden() -> str:
    """Orden de carne: plato con carne picada, limón y cebolla."""
    carne = "".join(trozo(*t) for t in (
        (44, 70, 9, 5.5, -10), (60, 64, 10, 6, 8), (76, 68, 9, 5.5, 15), (52, 76, 9, 5, 0),
        (70, 77, 9, 5, -8), (86, 76, 7, 4.5, 10), (62, 71, 8, 5, 20),
    ))
    body = (
        f'<ellipse cx="64" cy="82" rx="56" ry="22" fill="#D3D9D6"/>'
        f'<ellipse cx="64" cy="79" rx="56" ry="22" fill="#FFFFFF" stroke="#E6EAE8" stroke-width="1.5"/>'
        f'<ellipse cx="64" cy="79" rx="44" ry="15" fill="#F4F6F5"/>'
        f'{carne}{cilantro(54, 66)}{cilantro(74, 63)}{cilantro(66, 78)}'
        f'<rect x="78" y="70" width="7" height="4" rx="2" fill="{CEBOLLA}" stroke="#E2DAC0" stroke-width=".6" transform="rotate(-20 81 72)"/>'
        f'<rect x="42" y="74" width="7" height="4" rx="2" fill="{CEBOLLA}" stroke="#E2DAC0" stroke-width=".6" transform="rotate(15 45 76)"/>'
        # Limón partido.
        f'<path d="M92 84 A13 13 0 0 1 118 84 Z" fill="#5DBB7A"/>'
        f'<path d="M95 84 A10 10 0 0 1 115 84 Z" fill="#CDEB8B"/>'
        f'<path d="M105 84 L105 75 M105 84 L98.5 77.5 M105 84 L111.5 77.5" stroke="#9FD45E" stroke-width="1.4"/>'
    )
    return doc("Orden", f'<g transform="translate(64 64) scale(1.12) translate(-64 -76)">{body}</g>')


VERDE_APP = "#3F8F6F"
VERDE_APP_OSCURO = "#2F7358"
PAPEL = "#FFF8E7"
PAPEL_SOMBRA = "#EFE3C4"
LINEA = "#C9BFA3"
KRAFT = "#C98B4E"
KRAFT_OSCURO = "#A86F38"
KRAFT_BRILLO = "#DDA66C"
METAL = "#C9CED6"
METAL_OSCURO = "#9AA1AB"
ORO = "#F2B632"
ORO_OSCURO = "#C98A12"
ORO_BRILLO = "#FFE08A"


def sombra(cx: float = 64, cy: float = 112, rx: float = 38) -> str:
    return f'<ellipse cx="{cx}" cy="{cy}" rx="{rx}" ry="5" fill="#000" opacity=".08"/>'


def rol_taquero() -> str:
    """Gorro de chef con banda verde y una espátula de plancha."""
    formas = ('<circle cx="42" cy="50" r="18"/><circle cx="86" cy="50" r="18"/><circle cx="64" cy="38" r="23"/>'
              '<rect x="38" y="50" width="52" height="38" rx="3"/>')
    gorro = (
        # Contorno: las mismas formas con trazo grueso debajo; encima, rellenas sin trazo, quedan unidas.
        f'<g fill="#C9D3CF" stroke="#C9D3CF" stroke-width="6" stroke-linejoin="round">{formas}</g>'
        f'<g fill="#FFFFFF">{formas}</g>'
        f'<path d="M30 54 A14 14 0 0 1 42 36" fill="none" stroke="#E6ECE9" stroke-width="3" stroke-linecap="round"/>'
        f'<path d="M52 62 V84 M64 60 V84 M76 62 V84" stroke="#E1E8E4" stroke-width="3.5" stroke-linecap="round"/>'
        f'<rect x="34" y="84" width="60" height="18" rx="6" fill="{VERDE_APP}"/>'
        f'<rect x="34" y="96" width="60" height="6" rx="3" fill="{VERDE_APP_OSCURO}" opacity=".6"/>'
        f'<ellipse cx="55" cy="27" rx="8" ry="3.5" fill="#EEF3F1" transform="rotate(-25 55 27)"/>'
    )
    espatula = (
        f'<g transform="rotate(35 100 92)">'
        f'<rect x="96" y="70" width="8" height="26" rx="4" fill="{CARNE}"/>'
        f'<rect x="91" y="94" width="18" height="20" rx="3" fill="{METAL}" stroke="{METAL_OSCURO}" stroke-width="1.5"/>'
        f'<path d="M95 99 V109 M100 99 V109 M105 99 V109" stroke="{METAL_OSCURO}" stroke-width="1.4" stroke-linecap="round"/>'
        f'</g>'
    )
    return doc("Taquero", sombra(64, 114, 34) + gorro + espatula)


def rol_mesero() -> str:
    """Libreta de comanda con lápiz."""
    libreta = (
        f'<rect x="30" y="22" width="58" height="84" rx="7" fill="{PAPEL_SOMBRA}"/>'
        f'<rect x="28" y="18" width="58" height="84" rx="7" fill="{PAPEL}" stroke="{LINEA}" stroke-width="1.5"/>'
        f'<rect x="28" y="18" width="58" height="16" rx="7" fill="{VERDE_APP}"/>'
        f'<rect x="28" y="28" width="58" height="6" fill="{VERDE_APP}"/>'
        + "".join(f'<circle cx="{x}" cy="18" r="3" fill="{METAL}" stroke="{METAL_OSCURO}" stroke-width="1.2"/>' for x in (40, 52, 64, 76))
        + "".join(f'<path d="M37 {y} H{77 - (i % 2) * 14}" stroke="{LINEA}" stroke-width="2.5" stroke-linecap="round"/>' for i, y in enumerate((48, 58, 68, 78, 88)))
    )
    lapiz = (
        f'<g transform="rotate(40 92 70)">'
        f'<rect x="86" y="30" width="12" height="56" rx="2" fill="{QUESO}"/>'
        f'<rect x="86" y="30" width="4" height="56" fill="{ORO}" opacity=".6"/>'
        f'<rect x="86" y="24" width="12" height="8" rx="2" fill="#E88FA0"/>'
        f'<rect x="86" y="31" width="12" height="4" fill="{METAL}"/>'
        f'<path d="M86 86 L92 100 L98 86 Z" fill="#F3D9B1"/>'
        f'<path d="M90.3 96 L92 100 L93.7 96 Z" fill="#3A3A3A"/>'
        f'</g>'
    )
    return doc("Mesero", sombra(62, 112, 32) + libreta + lapiz)


def rol_admin() -> str:
    """Llave dorada con etiqueta verde: la del dueño."""
    llave = (
        f'<g transform="rotate(-35 64 64)">'
        f'<circle cx="64" cy="36" r="20" fill="{ORO}" stroke="{ORO_OSCURO}" stroke-width="2.5"/>'
        f'<circle cx="64" cy="36" r="8" fill="#F4F6F5" stroke="{ORO_OSCURO}" stroke-width="2.5"/>'
        f'<ellipse cx="56" cy="27" rx="6" ry="3" fill="{ORO_BRILLO}" transform="rotate(-30 56 27)"/>'
        f'<rect x="59" y="54" width="10" height="52" rx="3" fill="{ORO}" stroke="{ORO_OSCURO}" stroke-width="2.5"/>'
        f'<path d="M69 82 h10 v7 h-10 M69 94 h14 v7 h-14" fill="{ORO}" stroke="{ORO_OSCURO}" stroke-width="2.5" stroke-linejoin="round"/>'
        f'</g>'
    )
    etiqueta = (
        f'<path d="M30 70 L50 70 L58 80 L50 90 L30 90 Z" fill="{VERDE_APP}"/>'
        f'<circle cx="51" cy="80" r="2.6" fill="#FFFFFF"/>'
        f'<path d="M51 80 C 58 76 62 74 66 70" stroke="{METAL_OSCURO}" stroke-width="1.6" fill="none"/>'
        f'<path d="M35 77 H45 M35 83 H42" stroke="#FFFFFF" stroke-width="2.4" stroke-linecap="round" opacity=".85"/>'
    )
    return doc("Admin", sombra(64, 114, 30) + llave + etiqueta)


def bolsa_llevar() -> str:
    """Bolsa de papel para llevar con un taco de sello."""
    bolsa = (
        f'<path d="M48 34 C48 20 80 20 80 34" fill="none" stroke="{KRAFT_OSCURO}" stroke-width="5" stroke-linecap="round"/>'
        f'<path d="M30 38 H98 L94 108 H34 Z" fill="{KRAFT}"/>'
        f'<path d="M30 38 H98 L97 50 H31 Z" fill="{KRAFT_OSCURO}"/>'
        f'<path d="M31 50 l6 -6 6 6 6 -6 6 6 6 -6 6 6 6 -6 6 6 6 -6 6 6 6 -6 6 6" fill="none" stroke="{KRAFT_BRILLO}" stroke-width="2"/>'
        f'<path d="M40 58 V100" stroke="{KRAFT_BRILLO}" stroke-width="3" stroke-linecap="round" opacity=".7"/>'
        f'<circle cx="68" cy="78" r="17" fill="{PAPEL}"/>'
        f'<path d="M55 81 Q68 77 81 81 C81 90 75 93 68 93 C61 93 55 90 55 81 Z" fill="{TORTILLA_OSCURA}"/>'
        f'<path d="M57 80 C60 72 76 72 79 80 Z" fill="{CARNE}"/>'
        f'<circle cx="62" cy="76" r="2" fill="{VERDES[1]}"/><circle cx="70" cy="74" r="2" fill="{VERDES[2]}"/><circle cx="75" cy="77" r="1.8" fill="{CEBOLLA}"/>'
    )
    return doc("Para llevar", sombra(64, 112, 34) + bolsa)


def candado() -> str:
    """Candado: cocina sin licencia activa."""
    c = (
        f'<path d="M44 58 V44 a20 20 0 0 1 40 0 V58" fill="none" stroke="{METAL_OSCURO}" stroke-width="10" stroke-linecap="round"/>'
        f'<path d="M44 58 V44 a20 20 0 0 1 40 0 V58" fill="none" stroke="{METAL}" stroke-width="5" stroke-linecap="round"/>'
        f'<rect x="32" y="56" width="64" height="52" rx="10" fill="{ORO}" stroke="{ORO_OSCURO}" stroke-width="2.5"/>'
        f'<rect x="38" y="61" width="52" height="6" rx="3" fill="{ORO_BRILLO}" opacity=".8"/>'
        f'<circle cx="64" cy="80" r="7" fill="#5C3720"/><path d="M61 82 H67 L66 95 H62 Z" fill="#5C3720"/>'
    )
    return doc("Bloqueado", sombra(64, 114, 34) + c)


if __name__ == "__main__":
    for name, make in {
        "esp_quesataco": quesataco, "esp_burrito": burrito, "esp_vampiro": vampiro, "cat_ordenes": orden,
        "rol_taquero": rol_taquero, "rol_mesero": rol_mesero, "rol_admin": rol_admin,
        "bolsa_llevar": bolsa_llevar, "candado": candado,
    }.items():
        (SVG / f"{name}.svg").write_text(make())
        print("ok", name)
