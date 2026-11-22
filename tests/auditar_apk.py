#!/usr/bin/env python3
"""Checks the delivered APK, not just its source manifest."""
import os, pathlib, subprocess, xml.etree.ElementTree as ET, zipfile, hashlib
root=pathlib.Path(__file__).resolve().parents[1]
apk=root/'build/Ruta-Panel-1.0.0.apk'
bt=pathlib.Path(os.environ['ANDROID_HOME'])/'build-tools/35.0.0'
badging=subprocess.check_output([str(bt/'aapt'),'dump','badging',str(apk)],text=True)
assert 'uses-permission:' not in badging, badging
assert 'application-debuggable' not in badging
assert "sdkVersion:'26'" in badging
binary_manifest=subprocess.check_output([str(bt/'aapt'),'dump','xmltree',str(apk),'AndroidManifest.xml'],text=True)
assert 'android:allowBackup(0x01010280)=(type 0x12)0x0' in binary_manifest
assert 'android:usesCleartextTraffic(0x010104ec)=(type 0x12)0x0' in binary_manifest
assert 'android.permission.BIND_NOTIFICATION_LISTENER_SERVICE' in binary_manifest
manifest=ET.parse(root/'AndroidManifest.xml').getroot()
ns='{http://schemas.android.com/apk/res/android}'
app=manifest.find('application')
assert app.get(ns+'allowBackup')=='false'
assert app.get(ns+'usesCleartextTraffic')=='false'
assert not list(manifest.findall('uses-permission'))
service=app.find('service')
assert service.get(ns+'permission')=='android.permission.BIND_NOTIFICATION_LISTENER_SERVICE'
assert len(list(app.findall('activity')))==1
with zipfile.ZipFile(apk) as z:
 assert set(n for n in z.namelist() if n.endswith('.dex'))=={'classes.dex'}
 assert not any(n.startswith(('lib/','assets/')) for n in z.namelist())
subprocess.run([str(bt/'apksigner'),'verify','--verbose',str(apk)],check=True)
print('OK: sin permisos solicitados; no depurable; sin bibliotecas nativas ni assets ejecutables; firma válida.')
print('SHA-256:',hashlib.sha256(apk.read_bytes()).hexdigest())
