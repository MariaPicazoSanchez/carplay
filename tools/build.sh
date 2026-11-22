#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
: "${ANDROID_HOME:?Set ANDROID_HOME to an Android SDK with platform 35 and build-tools 35.0.0}"
: "${RUTA_SIGNING_KEY:?Set RUTA_SIGNING_KEY to your private signing keystore}"
: "${RUTA_KEY_PASSWORD:?Set RUTA_KEY_PASSWORD without committing it}"
BT="$ANDROID_HOME/build-tools/35.0.0"
ANDROID_JAR="$ANDROID_HOME/platforms/android-35/android.jar"
rm -rf build
mkdir -p build/classes build/dex
"$BT/aapt2" compile --dir res -o build/resources.zip
"$BT/aapt2" link -o build/base.apk -I "$ANDROID_JAR" --manifest AndroidManifest.xml --java build/generated build/resources.zip
find src build/generated -name '*.java' > build/sources.txt
java com.sun.tools.javac.Main -source 8 -target 8 -encoding UTF-8 -bootclasspath "$ANDROID_JAR:$BT/core-lambda-stubs.jar" -d build/classes @build/sources.txt
java sun.tools.jar.Main --create --file build/classes.jar -C build/classes .
"$BT/d8" --min-api 26 --lib "$ANDROID_JAR" --output build/dex build/classes.jar
python3 - <<'PY'
import zipfile
with zipfile.ZipFile('build/base.apk','a',zipfile.ZIP_DEFLATED) as z:
    z.write('build/dex/classes.dex','classes.dex')
PY
"$BT/zipalign" -f -p 4 build/base.apk build/aligned.apk
"$BT/apksigner" sign --ks "$RUTA_SIGNING_KEY" --ks-pass env:RUTA_KEY_PASSWORD --ks-key-alias ruta --out build/Ruta-Panel-1.0.0.apk build/aligned.apk
"$BT/apksigner" verify --verbose build/Ruta-Panel-1.0.0.apk
