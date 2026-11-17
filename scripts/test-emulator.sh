#!/usr/bin/env bash
# Emulador de pruebas de Barbería, ligero y oculto para Android Studio.
#
# - Sin ventana y con pocos recursos (en esta Mac suelen correr los emuladores de otro proyecto).
# - Se registra en un servidor adb PRIVADO (puerto 5038), no en el de Android Studio (5037), y usa
#   puertos fuera del rango que este escanea: no aparece en Device Manager ni en Running Devices.
#
# Uso: scripts/test-emulator.sh start | stop | status
#      Luego: adb -P 5038 -s emulator-5700 ...   (o ANDROID_SERIAL/ADB_SERVER_PORT en android-tests.sh)
set -euo pipefail
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$SDK/platform-tools/adb"
AVD="${AVD:-Medium_Phone_API_36.0}"
PORT=5038                    # servidor adb privado
SERIAL=emulator-5700         # consola 5700, adb 5701 (fuera del rango que escanea Android Studio)
adb_p() { "$ADB" -P "$PORT" "$@"; }

case "${1:-status}" in
  start)
    adb_p start-server >/dev/null 2>&1
    if adb_p -s "$SERIAL" get-state >/dev/null 2>&1; then echo "Ya está encendido ($SERIAL)"; exit 0; fi
    ANDROID_ADB_SERVER_PORT=$PORT nohup "$SDK/emulator/emulator" -avd "$AVD" -ports 5700,5701 \
      -no-window -cores 2 -memory 2048 -no-boot-anim -no-audio -no-snapshot-save >/dev/null 2>&1 &
    until [ "$(adb_p -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 3; done
    echo "Listo: $SERIAL en el adb privado ($PORT)"
    ;;
  stop)
    adb_p -s "$SERIAL" emu kill >/dev/null 2>&1 && echo "Apagado" || echo "No estaba encendido"
    ;;
  status)
    adb_p devices
    ;;
  *) echo "Uso: $0 start | stop | status"; exit 1 ;;
esac
