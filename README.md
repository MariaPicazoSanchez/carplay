# Ruta · Panel 1.2.0

Panel personal para Android 8 o posterior con navegación de Google integrada y controles locales de música.

## Instalación

1. Copia `Ruta-Panel-1.2.0.apk` al móvil y ábrela desde Archivos.
2. Si Android lo pide, autoriza temporalmente a Archivos a instalar esta APK. Después revoca esa autorización.
3. Conserva Play Protect activado. Si Android detecta una amenaza, no ignores la alerta.
4. Abre **Ruta · Panel**, con el vehículo aparcado. La pantalla inicial explica las limitaciones.
5. Pulsa **He aparcado** y confirma. En **Ajustes y privacidad** puedes guardar casa y trabajo.
6. En la tarjeta **Ahora suena**, pulsa **Activar controles de música**. En los ajustes de Android, activa **Ruta · Control de música** y vuelve al panel. Android concede un permiso amplio. Ruta lo usa para consultar sesiones multimedia y no lee el contenido de las notificaciones.
7. Reproduce una canción en Spotify, YouTube Music u otro reproductor que publique una MediaSession. Regresa al panel para mostrar título, artista, carátula y controles disponibles.

Abre **Mapa** para ver Google Maps. La primera vez, pulsa **Permitir ubicación**, lee la explicación y concede ubicación precisa para usar el GPS y la orientación por rumbo.

Ruta muestra la canción, el artista y el reproductor que publica una sesión multimedia en Android. Cuando hay varios reproductores sonando a la vez, da prioridad a Spotify, luego a YouTube Music y después a los demás. Si pausas desde Ruta, el botón de reproducción retoma la misma sesión mientras no empiece a sonar otra. Los controles de pista anterior, siguiente, pausa y reproducción dependen de las acciones que publique el reproductor.

El sonido sale por la salida que ya tenga seleccionada Android (altavoz o Bluetooth del coche). Ruta no empareja dispositivos ni necesita modificar el coche.

## Navegación dentro de Ruta

**Mapa** abre el mapa integrado. **Casa** y **Trabajo** abren esa misma pantalla con la dirección guardada en el campo de destino.

En **Mapa**, el punto azul y la cámara siguen tu posición. Si arrastras o manipulas el mapa, pulsa **Centrar mapa** para recuperar el seguimiento. La etiqueta muestra la precisión y la velocidad cuando el proveedor las entrega. Con ubicación aproximada, la velocidad puede no estar disponible.

Debajo del mapa se muestra la canción del reproductor activo y los botones de pista anterior, reproducción y pista siguiente. La barra usa el mismo permiso de música que **Ahora suena** en Inicio.

Escribe una dirección o lugar en **Mapa**, pulsa **Buscar ruta** y confirma el resultado. Ruta usa tu posición GPS actual y la estrategia de Google que prioriza el menor tiempo de conducción estimado. El mapa muestra las indicaciones giro a giro junto a la barra de música. El SDK puede recalcular el itinerario durante el trayecto. La edición del destino se bloquea al superar 11 km/h. Pulsa **Finalizar ruta** para detener las indicaciones.

## Qué protege esta versión

- El mapa solicita Internet y ubicación. Ruta recibe coordenadas mientras la pantalla Mapa está en primer plano y no guarda el historial de ubicaciones.
- Control multimedia mediante la API MediaSession de Android; no inyecta pulsaciones ni usa accesibilidad.
- Servicio de acceso a notificaciones protegido por el permiso de enlace del sistema. No implementa callbacks que lean mensajes o notificaciones.
- El mapa usa el SDK oficial de Google Maps y requiere Servicios de Google Play.
- Direcciones en preferencias privadas locales, excluidas de copias de seguridad (`allowBackup=false`). Se pueden borrar desde ajustes o borrando los datos de la app.
- El servicio de navegación de Google recibe el origen y el destino para calcular el trayecto. Al salir de la pantalla Mapa, Ruta cancela la búsqueda o finaliza las indicaciones.
- Los metadatos musicales se consultan mientras el panel está visible.
- Los controles de reproducción se desactivan si la sesión no ofrece la acción correspondiente.
- Modo conducción manual activo en un arranque nuevo. Bloquea ajustes y escritura de destinos dentro de Ruta. No detecta movimiento, no impide cambiar de app y no es una medida de seguridad vial certificada.
- La compilación distribuida se firma con el almacén configurado o con una clave temporal de CI.

## Límites que debes conocer

No existe garantía de riesgo cero. Esta revisión no es una auditoría independiente ni una certificación. No se ha probado en tu teléfono real ni con tus cuentas de Spotify, YouTube Music o aplicaciones de navegación. Los resultados históricos de la versión 1.0.0 están en `tests/ESTADO.md`. Consulta el resultado del workflow para las pruebas de la versión actual.

El sistema puede entregar notificaciones personales al servicio si concedes el acceso especial, aunque esta versión no usa su contenido. Puedes revocarlo en cualquier momento. Una futura actualización debe revisarse antes de instalarla.

El modo conducción es manual: puedes desactivarlo incluso si te mueves; la aplicación no puede verificar que hayas aparcado. Las apps externas pueden mostrar teclados, búsquedas o pantallas que el panel no bloquea. Configura ruta, música y permisos antes de iniciar el trayecto. La pantalla encendida consume batería y puede calentar el dispositivo.

## Compilación revisable

### GitHub Actions

El workflow **Android APK** se ejecuta con cada push, pull request o lanzamiento manual. Compila y audita la APK, la instala en un emulador Android 9 y ejecuta las pruebas de instrumentación. En **Actions → Android APK → Artifacts**, descarga **Ruta-Panel-Android-con-rutas**, extrae el ZIP y abre **Ruta-Panel-1.2.0.apk** en el móvil. Si aparece **Ruta-Panel-Android-sin-clave-navegacion**, esa ejecución generó un APK de pruebas sin credenciales del mapa.

Por defecto, cada ejecución usa una clave temporal. Una APK con esa firma se puede instalar desde cero, pero no actualizar una instalación con otra firma, incluida la APK de `dist/`. Para generar actualizaciones desde `main`, configura los secretos de Actions `RUTA_SIGNING_KEY_BASE64` (almacén PKCS12 codificado en Base64, alias `ruta`) y `RUTA_KEY_PASSWORD`. Usa el almacén original para actualizar la APK de `dist/`. Si eliges desinstalar para cambiar de firma, guarda antes las direcciones que quieras conservar, ya que se borran los datos locales.

Si Android rechaza la instalación, comprueba que la versión del móvil sea Android 8 (API 26) o posterior. Con el móvil conectado por USB y ADB disponible, `adb install -r Ruta-Panel-1.2.0.apk` muestra el código exacto del rechazo. `INSTALL_FAILED_UPDATE_INCOMPATIBLE` indica que la firma difiere de la de la instalación existente.

### Activar Google Maps

1. Habilita **Navigation SDK** en un proyecto de Google Cloud con facturación habilitada.
2. Restringe la clave al SDK y a la app Android `es.maria.rutapanel`, con la huella SHA-1 de su certificado de firma. Puedes obtener la huella con `apksigner verify --print-certs Ruta-Panel-1.2.0.apk`.
3. Para compilar localmente, define `RUTA_MAPS_API_KEY` en el entorno. Para GitHub Actions, añade el secreto `RUTA_MAPS_API_KEY` junto con los dos secretos de firma estable. La clave queda incluida en la APK, por lo que la restricción por certificado se aplica en Google Cloud.

Las compilaciones de pull requests y las firmadas con clave temporal permiten probar el panel y la interfaz de ubicación sin credenciales. Para probar una ruta real, usa una compilación con la clave configurada y un teléfono con Servicios de Google Play, ubicación precisa y conexión a Internet.

Antes de distribuir el APK, incluye los archivos oficiales `NOTICE.txt` y `LICENSES.txt` de la versión del Navigation SDK en `assets/navigation/`. Se pueden consultar desde **Ajustes → Avisos de navegación**.

### Compilación local

Se incluye el código de Ruta, sus recursos y las pruebas. Gradle resuelve el SDK oficial de Google Maps desde el repositorio Maven de Google.

Requisitos: JDK 17, Gradle 8.11.1, Python 3, Android SDK plataforma 35 y build-tools 35.0.0.

```sh
export ANDROID_HOME=/ruta/al/android-sdk
export RUTA_SIGNING_KEY=/ruta/privada/ruta.p12
# Define RUTA_KEY_PASSWORD de forma privada, sin guardarla en el repositorio.
bash tools/build.sh
bash tools/build-tests.sh
python3 tests/auditar_apk.py
```

Para crear tu propia clave, usa `keytool -genkeypair` con alias `ruta`, RSA 3072 y un almacén PKCS12. Una clave diferente genera una app firmada de forma diferente: Android no permitirá instalarla como actualización sobre esta versión. No publiques ni compartas material de firma.

Las pruebas de instrumentación requieren un dispositivo de prueba con acceso de notificaciones habilitado y las APK de app y tests firmadas con la misma clave. En el móvil personal instala `Ruta-Panel-1.2.0.apk`.

## Proyecto local en Windows

La APK archivada en `dist/` corresponde a la versión 1.0.0. Para compilar 1.2.0 en Windows, instala JDK 17, Gradle 8.11.1, Android SDK Platform 35 y Build Tools 35.0.0. Ejecuta `./build.ps1 -KeyStore C:\ruta\privada\ruta.p12` en PowerShell. La contraseña se pide de forma oculta y el APK generado queda en `build/`.

## Licencia

MIT. El código de esta aplicación es original. Marcas como Google Maps, Waze, Spotify y YouTube Music pertenecen a sus titulares.
