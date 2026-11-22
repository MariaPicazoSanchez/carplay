#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

adb install -r build/Ruta-Panel-1.2.0.apk
adb install -r -t build/tests/tests.apk
adb shell cmd notification allow_listener es.maria.rutapanel/.MediaAccessService
adb shell am instrument -w -r es.maria.rutapanel.tests/.PanelTests | tr -d '\r' | tee build/instrumentation.txt
grep -Eq '^INSTRUMENTATION_RESULT: stream=OK: [1-9][0-9]* comprobaciones funcionales\.$' build/instrumentation.txt
grep -Fx 'INSTRUMENTATION_CODE: -1' build/instrumentation.txt
