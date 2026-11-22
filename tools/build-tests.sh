#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
BT="$ANDROID_HOME/build-tools/35.0.0"
JAR="$ANDROID_HOME/platforms/android-35/android.jar"
mkdir -p build/tests/classes build/tests/dex
"$BT/aapt2" link -o build/tests/base.apk -I "$JAR" --manifest tests/AndroidManifest.xml
find tests/src -name '*.java' > build/tests/sources.txt
java com.sun.tools.javac.Main -source 8 -target 8 -encoding UTF-8 -bootclasspath "$JAR:$BT/core-lambda-stubs.jar" -d build/tests/classes @build/tests/sources.txt
java sun.tools.jar.Main --create --file build/tests/classes.jar -C build/tests/classes .
"$BT/d8" --min-api 26 --lib "$JAR" --output build/tests/dex build/tests/classes.jar
python3 - <<'PY'
import zipfile
with zipfile.ZipFile('build/tests/base.apk','a',zipfile.ZIP_DEFLATED) as z:z.write('build/tests/dex/classes.dex','classes.dex')
PY
"$BT/zipalign" -f -p 4 build/tests/base.apk build/tests/aligned.apk
"$BT/apksigner" sign --ks "$RUTA_SIGNING_KEY" --ks-pass env:RUTA_KEY_PASSWORD --ks-key-alias ruta --out build/tests/tests.apk build/tests/aligned.apk
