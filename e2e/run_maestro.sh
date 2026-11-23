#!/bin/bash
# Pruebas e2e de Tacomanda con Maestro: una cocina y un mesero en dos dispositivos.
#
#   e2e/run_maestro.sh [cocina-serial] [mesero-serial]
#
# Por defecto usa los emuladores maestro_cocina (emulator-5560) y maestro_mesero (emulator-5562):
#   emulator -avd maestro_cocina -port 5560 -no-window &
#   emulator -avd maestro_mesero -port 5562 -no-window &
#
# También corre en teléfonos físicos por USB (e2e/run_maestro.sh <serial-cocina> <serial-mesero>):
# todo viaja por adb reverse/forward, así que no hace falta que estén en la misma red.
#
# Compila un APK de debug apuntando a una Central falsa local (e2e/mock_central.py), así la
# cocina se activa en la prueba sin tocar la Central real. Los flujos borran los datos de la app.
set -euo pipefail
cd "$(dirname "$0")/.."

COCINA=${1:-emulator-5560}
MESERO=${2:-emulator-5562}
CENTRAL_PORT=4999
FORWARD_PORT=7790
MAESTRO=${MAESTRO:-$HOME/.maestro/bin/maestro}
APK=e2e/build/tacomanda-e2e.apk
FLOWS=e2e/maestro

echo "▸ APK de pruebas contra la Central falsa"
./gradlew -q :app:assembleDebug -Ptaqueria.apiUrl=http://127.0.0.1:$CENTRAL_PORT/api/v1
mkdir -p e2e/build
cp app/build/outputs/apk/debug/app-debug.apk "$APK"

echo "▸ Central falsa en :$CENTRAL_PORT"
# Siempre una nueva: el conteo de ventas que se revisa al final empieza en cero.
pkill -f "e2e/mock_central.py" 2>/dev/null || true
python3 -I e2e/mock_central.py $CENTRAL_PORT > e2e/build/central.log 2>&1 &
CENTRAL_PID=$!
trap 'kill $CENTRAL_PID 2>/dev/null || true' EXIT
sleep 1

for d in "$COCINA" "$MESERO"; do
  echo "▸ Instalando en $d"
  adb -s "$d" wait-for-device
  until [ "$(adb -s "$d" shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; do sleep 2; done
  # Desinstalar en vez de que Maestro borre los datos: ColorOS (Oppo) le niega `pm clear` a adb.
  adb -s "$d" uninstall com.axzy.taqueria > /dev/null 2>&1 || true
  # Por USB algunos teléfonos se reconectan al desinstalar: esperar y reintentar.
  for try in 1 2 3; do
    adb -s "$d" wait-for-device
    adb -s "$d" install -g "$APK" > /dev/null && break
    [ "$try" = 3 ] && { echo "✘ No se pudo instalar en $d"; exit 1; }
    sleep 3
  done
  # Emuladores sin ventana: que Android no tape la app con avisos de apps en segundo plano.
  [[ "$d" == emulator-* ]] && adb -s "$d" shell settings put secure anr_show_background 0
  # La Central falsa de la Mac, en 127.0.0.1 del teléfono (emulador o físico por USB).
  adb -s "$d" reverse tcp:$CENTRAL_PORT tcp:$CENTRAL_PORT > /dev/null
done
# El mesero llega a la cocina por la Mac: mesero 127.0.0.1:$FORWARD_PORT → Mac:$FORWARD_PORT → cocina:7777.
adb -s "$COCINA" forward tcp:$FORWARD_PORT tcp:7777 > /dev/null
adb -s "$MESERO" reverse tcp:$FORWARD_PORT tcp:$FORWARD_PORT > /dev/null

run() {
  local device=$1 flow=$2
  echo "▸ [$device] $flow"
  "$MAESTRO" --device "$device" test -e KITCHEN_HOST=127.0.0.1:$FORWARD_PORT "$FLOWS/$flow" \
    --format junit --output "e2e/build/$(basename "$flow" .yaml)-$(basename "$(dirname "$flow")").xml"
}

# Orden importa: cada paso usa lo que dejó el anterior en el otro dispositivo.
run "$COCINA" cocina/01_activar.yaml
run "$MESERO" mesero/01_conectar.yaml
run "$MESERO" mesero/02_orden_por_persona.yaml
run "$COCINA" cocina/02_ticket_por_persona.yaml
run "$MESERO" mesero/03_mesa_ocupada_y_quitar.yaml
run "$COCINA" cocina/03_agotar.yaml
run "$MESERO" mesero/04_agotado_no_aparece.yaml
run "$COCINA" cocina/04_reactivar.yaml
run "$COCINA" cocina/05_admin_pin.yaml
run "$MESERO" mesero/05_cobrar_con_cambio.yaml
run "$COCINA" cocina/06_cobrada_sale.yaml
run "$COCINA" cocina/07_precio_harina.yaml
run "$MESERO" mesero/06_harina_y_cancelar.yaml
run "$COCINA" cocina/08_ticket_harina.yaml

# Las cuentas cerradas se archivan a los 30 s y la cocina las reporta a la Central en ≤ 1 min;
# el respaldo del estado sube en el primer minuto.
echo "▸ Ventas y respaldo en la Central"
VERSION=$(grep -E '^taqueria.version(Name|Code)=' gradle.properties | sort | cut -d= -f2 | paste -sd' ' -)
EXPECTED="$(echo "$VERSION" | awk '{print $2" ("$1")"}')"
for _ in $(seq 1 40); do
  SALES=$(curl -s "http://127.0.0.1:$CENTRAL_PORT/_sales")
  python3 -c "import json,sys; s=json.loads(sys.argv[1]); sys.exit(0 if s['tickets']>=1 and s['appVersion']==sys.argv[2] and s.get('backupAt') else 1)" "$SALES" "$EXPECTED" && break
  sleep 5
done
echo "  $SALES"
python3 -c "import json,sys; s=json.loads(sys.argv[1]); sys.exit(0 if s['tickets']>=1 and s['appVersion']==sys.argv[2] and s.get('backupAt') else 1)" "$SALES" "$EXPECTED" \
  || { echo "✘ La cocina no reportó las ventas o el respaldo (se esperaba al menos 1 cuenta con v$EXPECTED y backupAt)"; exit 1; }
echo "✔ Todos los flujos pasaron"
