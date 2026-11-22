#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
: "${ANDROID_HOME:?Set ANDROID_HOME to an Android SDK with platform 35 and build-tools 35.0.0}"
: "${RUTA_SIGNING_KEY:?Set RUTA_SIGNING_KEY to your private signing keystore}"
: "${RUTA_KEY_PASSWORD:?Set RUTA_KEY_PASSWORD without committing it}"
BT="$ANDROID_HOME/build-tools/35.0.0"
gradle --no-daemon assembleRelease
set -- build/outputs/apk/release/*-unsigned.apk
test "$#" -eq 1 && test -f "$1"
"$BT/apksigner" sign --ks "$RUTA_SIGNING_KEY" --ks-pass env:RUTA_KEY_PASSWORD --ks-key-alias ruta --out build/Ruta-Panel-1.2.0.apk "$1"
"$BT/apksigner" verify --verbose build/Ruta-Panel-1.2.0.apk
