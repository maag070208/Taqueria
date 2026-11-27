#!/usr/bin/env bash
set -euo pipefail

# Compila el APK release de Taquería, incrementa la versión y lo sube a Drive.
#
# Uso:
#   ./release.sh                          # sube a la carpeta del cliente por defecto
#   ./release.sh --client OtroCliente     # otra subcarpeta de cliente en Drive
#   ./release.sh --no-upload              # solo compila
#
# La versión SIEMPRE se incrementa sola (versionCode 1 -> 2, versionName 1.0.0 -> 1.0.1)
# y se guarda en gradle.properties solo si el build salió bien.
#
# Salida local:  app/build/outputs/apk/release/KOMANDERA_<versión>.apk
# Destino Drive: <carpeta compartida>/<cliente>/KOMANDERA_<versión>.apk
#
# Requisitos: Java 17, Android SDK y rclone con el remote "gdrive" configurado.
# Nota: se firma con la llave debug de esta Mac (instalable directo, no para Play Store).
# Para que un APK nuevo se instale encima del anterior, publica siempre desde la misma Mac.

cd "$(dirname "$0")"

REMOTE="${DRIVE_REMOTE:-gdrive}"
# Carpeta compartida "APKs de clientes" en Drive (su id viene del enlace).
FOLDER_ID="${DRIVE_FOLDER_ID:-1fl61fCVRcqCWBDe52Wzy3cNeFa-_sIwt}"
CLIENT="${DRIVE_CLIENT:-PerroOrejonAzteca}"
# Servidor de licencias por defecto del APK release (el QR de la Central puede
# traer otro y manda sobre este). Sirve para activar escribiendo el código a mano.
API_URL="${TAQUERIA_API_URL:-https://axzycentralapi-production.up.railway.app/api/v1}"
UPLOAD=1

while [[ $# -gt 0 ]]; do
  case "$1" in
    --no-upload) UPLOAD=0 ;;
    --client) CLIENT="${2:?Falta el nombre del cliente}"; shift ;;
    -h|--help) awk 'NR>1 && /^#/ {print} /^cd / {exit}' "$0"; exit 0 ;;
    *) echo "Argumento no válido: $1 (usa --client NOMBRE o --no-upload)" >&2; exit 1 ;;
  esac
  shift
done

PROPS="gradle.properties"
prop() { grep -E "^$1=" "$PROPS" | head -1 | cut -d= -f2; }
CURRENT_VERSION="$(prop taqueria.versionName)"
CURRENT_CODE="$(prop taqueria.versionCode)"

if [[ ! "$CURRENT_VERSION" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
  echo "taqueria.versionName '$CURRENT_VERSION' no tiene formato X.Y.Z" >&2
  exit 1
fi
VERSION="${BASH_REMATCH[1]}.${BASH_REMATCH[2]}.$((BASH_REMATCH[3] + 1))"
NEW_CODE=$((CURRENT_CODE + 1))

echo "Versión: $CURRENT_VERSION ($CURRENT_CODE) -> $VERSION ($NEW_CODE)"
echo "Pruebas y compilación..."
# La versión nueva se pasa por línea de comandos; el archivo no cambia si algo falla.
./gradlew :app:testDebugUnitTest :app:assembleRelease \
  -Ptaqueria.versionCode="$NEW_CODE" -Ptaqueria.versionName="$VERSION" \
  -Ptaqueria.apiUrl="$API_URL" --console=plain

OUT_DIR="app/build/outputs/apk/release"
APK="$OUT_DIR/app-release.apk"
[[ -f "$APK" ]] || { echo "No se generó $APK" >&2; exit 1; }

perl -pi -e "s/^taqueria\.versionCode=.*/taqueria.versionCode=$NEW_CODE/; s/^taqueria\.versionName=.*/taqueria.versionName=$VERSION/" "$PROPS"

NAME="KOMANDERA_${VERSION}.apk"
LOCAL_FILE="$OUT_DIR/$NAME"
mv -f "$APK" "$LOCAL_FILE"
echo "APK: $LOCAL_FILE"

[[ "$UPLOAD" == "1" ]] || exit 0

TARGET="${REMOTE}:${CLIENT}/${NAME}"
DRIVE=(--drive-root-folder-id "$FOLDER_ID")
echo "Subiendo a Drive: ${CLIENT}/${NAME} ..."
rclone copyto "$LOCAL_FILE" "$TARGET" "${DRIVE[@]}" \
  --drive-chunk-size 128M --drive-upload-cutoff 128M --retries 2 --low-level-retries 10 -v

# Verifica que lo que quedó en Drive es idéntico al APK local.
LOCAL_SIZE=$(stat -f%z "$LOCAL_FILE" 2>/dev/null || stat -c%s "$LOCAL_FILE")
REMOTE_SIZE=$(rclone lsf "$TARGET" "${DRIVE[@]}" --format "s" --files-only 2>/dev/null | head -1)
LOCAL_MD5=$(md5 -q "$LOCAL_FILE" 2>/dev/null || md5sum "$LOCAL_FILE" | awk '{print $1}')
REMOTE_MD5=$(rclone hashsum md5 "$TARGET" "${DRIVE[@]}" 2>/dev/null | awk '{print $1}')
if [[ "$LOCAL_SIZE" != "$REMOTE_SIZE" || "$LOCAL_MD5" != "$REMOTE_MD5" ]]; then
  echo "Verificación falló: local=$LOCAL_SIZE/$LOCAL_MD5 remoto=$REMOTE_SIZE/$REMOTE_MD5" >&2
  exit 1
fi
echo "OK: Drive ${CLIENT}/${NAME} (${LOCAL_SIZE} bytes, md5 ${LOCAL_MD5})"
