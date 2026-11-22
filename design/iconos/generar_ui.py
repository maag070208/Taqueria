"""Genera los íconos de acción de Tacomanda (botones, drawer, avisos) como VectorDrawable de Android.

Trazo de 2 px, puntas y esquinas redondeadas, lienzo 24×24. Se dibujan en negro y la app los
pinta con el color que toque (tint), así que sirven en cualquier botón.

    python3 design/iconos/generar_ui.py
"""
from pathlib import Path

OUT = Path(__file__).resolve().parents[2] / "app/src/main/res/drawable"

# nombre: (trazos, rellenos). Trazos = path data con línea de 2; rellenos = path data sólido.
ICONS: dict[str, tuple[list[str], list[str]]] = {
    "menu": (["M4,6h16M4,12h16M4,18h16"], []),
    "back": (["M19,12H5M11,6l-6,6 6,6"], []),
    "arrow_right": (["M5,12h14M13,6l6,6 -6,6"], []),
    "arrow_up": (["M12,19V5M6,11l6,-6 6,6"], []),
    "arrow_down": (["M12,5v14M6,13l6,6 6,-6"], []),
    "chevron_left": (["M15,5l-7,7 7,7"], []),
    "chevron_right": (["M9,5l7,7 -7,7"], []),
    "chevron_down": (["M5,9l7,7 7,-7"], []),
    "chevron_up": (["M5,15l7,-7 7,7"], []),
    "close": (["M6,6l12,12M18,6L6,18"], []),
    "check": (["M4,12.5l5,5L20,6.5"], []),
    "plus": (["M12,5v14M5,12h14"], []),
    "reset": (["M4,4.5v5h5", "M5.2,9.5a7.5,7.5 0,1 1,1.3 6.6"], []),
    "cash": (["M3,6.5h18v11H3z", "M12,9.5a2.5,2.5 0,1 1,0 5a2.5,2.5 0,1 1,0 -5"], ["M6,8.5m-1.2,0a1.2,1.2 0,1 0,2.4 0a1.2,1.2 0,1 0,-2.4 0", "M18,15.5m-1.2,0a1.2,1.2 0,1 0,2.4 0a1.2,1.2 0,1 0,-2.4 0"]),
    "camera": (["M4,8h3.2l2,-3h5.6l2,3H20v11H4z", "M12,9.8a3.4,3.4 0,1 1,0 6.8a3.4,3.4 0,1 1,0 -6.8"], []),
    "qr": (["M4,4h6v6H4zM14,4h6v6h-6zM4,14h6v6H4z"], ["M14,14h2.5v2.5H14zM17.5,17.5H20V20h-2.5zM14,18h2v2h-2zM18,14h2v2h-2z", "M6.2,6.2h1.6v1.6H6.2zM16.2,6.2h1.6v1.6h-1.6zM6.2,16.2h1.6v1.6H6.2z"]),
    "chart": (["M4,20h16M7,16v-5M12,16V6M17,16v-8"], []),
    "tag": (["M3.5,12.5V4h8.5l9,9 -8.5,8.5z"], ["M8,8.5m-1.6,0a1.6,1.6 0,1 0,3.2 0a1.6,1.6 0,1 0,-3.2 0"]),
    "orders": (["M6,3.5h12v17H6z", "M9,8h6M9,12h6M9,16h3.5"], []),
    "receipt": (["M6,3h12v18l-3,-2 -3,2 -3,-2 -3,2z", "M9,8h6M9,12h6"], []),
    "toggle": (["M7,7h10a5,5 0,0 1,0 10H7a5,5 0,0 1,0 -10z"], ["M17,12m-3,0a3,3 0,1 0,6 0a3,3 0,1 0,-6 0"]),
    "keypad": (["M3,6h18v12H3z"], ["M7.5,12m-1.4,0a1.4,1.4 0,1 0,2.8 0a1.4,1.4 0,1 0,-2.8 0", "M12,12m-1.4,0a1.4,1.4 0,1 0,2.8 0a1.4,1.4 0,1 0,-2.8 0", "M16.5,12m-1.4,0a1.4,1.4 0,1 0,2.8 0a1.4,1.4 0,1 0,-2.8 0"]),
    "swap": (["M4,8h15M15,4l4,4 -4,4", "M20,16H5M9,12l-4,4 4,4"], []),
    "lock": (["M5,11h14v10H5z", "M8,11V8a4,4 0,0 1,8 0v3"], []),
    "key": (["M8,11a4,4 0,1 1,0 8a4,4 0,1 1,0 -8", "M11,12.2l9,-9M16.5,6.7l3,3M13.8,9.4l2,2"], []),
    "wifi": (["M2.5,9a14,14 0,0 1,19 0", "M5.5,12.5a9.5,9.5 0,0 1,13 0", "M8.8,16a5,5 0,0 1,6.4 0"], ["M12,19.3m-1.4,0a1.4,1.4 0,1 0,2.8 0a1.4,1.4 0,1 0,-2.8 0"]),
    "warning": (["M12,3.5L21.5,20h-19z", "M12,10v4"], ["M12,17m-1.2,0a1.2,1.2 0,1 0,2.4 0a1.2,1.2 0,1 0,-2.4 0"]),
    "bag": (["M5,8h14l-1,13H6z", "M9,8V6.5a3,3 0,0 1,6 0V8"], []),
    "download": (["M12,4v11M7,10l5,5 5,-5M5,20h14"], []),
}


def vector(strokes: list[str], fills: list[str]) -> str:
    paths = [
        f'    <path android:pathData="{d}" android:strokeColor="#FF000000" android:strokeWidth="2" '
        f'android:strokeLineCap="round" android:strokeLineJoin="round" android:fillColor="#00000000"/>'
        for d in strokes
    ] + [f'    <path android:pathData="{d}" android:fillColor="#FF000000"/>' for d in fills]
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<!-- Generado por design/iconos/generar_ui.py; no editar a mano. -->\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">\n'
        + "\n".join(paths) + "\n</vector>\n"
    )


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for name, (strokes, fills) in ICONS.items():
        (OUT / f"ic_{name}.xml").write_text(vector(strokes, fills))
    print(f"{len(ICONS)} íconos en {OUT}")
