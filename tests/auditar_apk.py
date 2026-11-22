#!/usr/bin/env python3
"""Checks the packaged APK after Gradle and osmdroid merge the manifests."""
import hashlib
import os
import re
import subprocess
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parents[1]
apk = root / 'build/Ruta-Panel-1.2.0.apk'
tools = Path(os.environ['ANDROID_HOME']) / 'build-tools/35.0.0'
aapt = tools / ('aapt.exe' if os.name == 'nt' else 'aapt')
apksigner = tools / ('apksigner.bat' if os.name == 'nt' else 'apksigner')

badging = subprocess.check_output([aapt, 'dump', 'badging', apk], text=True, encoding='utf-8')
permissions = set(re.findall(r"^uses-permission(?:-sdk-\d+)?: name='([^']+)'", badging, re.M))
required = {
    'android.permission.INTERNET',
    'android.permission.ACCESS_FINE_LOCATION',
    'android.permission.ACCESS_COARSE_LOCATION',
}
allowed = required | {
    'android.permission.ACCESS_NETWORK_STATE',
    'android.permission.ACCESS_WIFI_STATE',
    'android.permission.FOREGROUND_SERVICE',
    'android.permission.FOREGROUND_SERVICE_LOCATION',
    'android.permission.POST_NOTIFICATIONS',
    'android.permission.VIBRATE',
    'android.permission.WAKE_LOCK',
    'com.google.android.providers.gsf.permission.READ_GSERVICES',
    'es.maria.rutapanel.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION',
}
assert required <= permissions, required - permissions
assert permissions <= allowed, permissions - allowed
assert 'application-debuggable' not in badging
assert "sdkVersion:'26'" in badging

manifest = subprocess.check_output([aapt, 'dump', 'xmltree', apk, 'AndroidManifest.xml'],
                                   text=True, encoding='utf-8')
assert 'android:allowBackup(0x01010280)=(type 0x12)0x0' in manifest
assert 'android:usesCleartextTraffic(0x010104ec)=(type 0x12)0x0' in manifest
assert 'android.permission.BIND_NOTIFICATION_LISTENER_SERVICE' in manifest
assert 'com.google.android.geo.API_KEY' not in manifest

with zipfile.ZipFile(apk) as archive:
    assert 'classes.dex' in archive.namelist()

subprocess.run([apksigner, 'verify', '--verbose', apk], check=True)
print('OK: ubicación en primer plano, sin depuración y firma válida.')
print('SHA-256:', hashlib.sha256(apk.read_bytes()).hexdigest())
