#!/usr/bin/env bash
# Q1: tests instrumentados (SDK real de Firebase + reglas reales) contra Firebase Emulator Suite.
#
# Requisitos: `cd firebase && npm run emulators` y un emulador Android encendido.
# Uso: scripts/test-emulator.sh start && scripts/android-tests.sh   (emulador oculto: adb privado 5038)
#      ANDROID_SERIAL=emulator-5554 ADB_SERVER_PORT=5037 scripts/android-tests.sh   (CI u otro emulador)
#
# No usa `connectedAndroidTest`: ese instala en TODOS los dispositivos conectados
# (en la Mac de Carlos hay emuladores de otro proyecto).
set -euo pipefail
cd "$(dirname "$0")/.."
SERIAL="${ANDROID_SERIAL:-emulator-5700}"
ADB_BIN="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
ADB() { "$ADB_BIN" -P "${ADB_SERVER_PORT:-5038}" "$@"; }
MODULES=(":feature:auth:data" ":feature:catalog:data" ":feature:booking:data")

curl -sf http://127.0.0.1:8085 >/dev/null || { echo "Falta Firestore en 8085: cd firebase && npm run emulators"; exit 1; }
curl -sf http://127.0.0.1:9199 >/dev/null || { echo "Falta Auth en 9199: cd firebase && npm run emulators"; exit 1; }
ADB -s "$SERIAL" get-state >/dev/null || { echo "No hay dispositivo $SERIAL"; exit 1; }

./gradlew -q $(printf "%s:assembleDebugAndroidTest " "${MODULES[@]}")

failed=0
for m in "${MODULES[@]}"; do
  dir="${m#:}"; dir="${dir//://}"
  apk=$(ls "$dir"/build/outputs/apk/androidTest/debug/*.apk)
  pkg="com.barberia.${dir//\//.}.test"
  ADB -s "$SERIAL" install -r -t "$apk" >/dev/null
  out=$(ADB -s "$SERIAL" shell am instrument -w -r "$pkg/androidx.test.runner.AndroidJUnitRunner")
  if grep -q "^OK (" <<<"$out"; then
    echo "✔ $m: $(grep '^OK (' <<<"$out")"
  else
    failed=1
    echo "✘ $m"
    grep -E "INSTRUMENTATION_STATUS: (test|stack)=|Tests run" <<<"$out" | sed 's/INSTRUMENTATION_STATUS: /  /'
  fi
done
exit $failed
