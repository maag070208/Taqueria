#!/bin/bash
# Exporta los SVG de design/iconos/svg a PNG de 256 px para la app (drawable-nodpi/ico_*.png).
# Renderiza con Chrome sin ventana (fondo transparente, dibujo centrado). Si el diseño trae
# su PNG original (png/<nombre>.png), se usa ese tal cual.
#   design/iconos/exportar_png.sh
set -euo pipefail
cd "$(dirname "$0")"
CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
OUT=../../app/src/main/res/drawable-nodpi
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$OUT"
for f in svg/*.svg; do
  n=$(basename "$f" .svg)
  if [ -f "png/$n.png" ]; then cp "png/$n.png" "$OUT/ico_$n.png"; continue; fi
  printf '<html><body style="margin:0;background:transparent"><img src="%s" width="256" height="256" style="display:block"></body></html>' \
    "file://$PWD/$f" > "$TMP/$n.html"
  "$CHROME" --headless=new --disable-gpu --hide-scrollbars --allow-file-access-from-files \
    --default-background-color=00000000 --window-size=256,256 \
    --screenshot="$TMP/$n.png" "file://$TMP/$n.html" > /dev/null 2>&1
  cp "$TMP/$n.png" "$OUT/ico_$n.png"
done
ls "$OUT" | wc -l | xargs echo "PNG exportados:"
