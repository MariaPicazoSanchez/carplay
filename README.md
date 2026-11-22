# Ruta · Panel 1.1.0

Panel personal para Android 8 o posterior, en español, con Google Maps integrado, navegación externa y controles locales de música. Proyecto original inspirado en la idea de un panel de coche. No contiene código ni recursos extraídos de CarDash. No es Apple CarPlay ni Android Auto, ni está certificado por Google, Apple o un fabricante de vehículos.

## Instalación

1. Copia `Ruta-Panel-1.1.0.apk` al móvil y ábrela desde Archivos.
2. Si Android lo pide, autoriza temporalmente a Archivos a instalar esta APK. Después revoca esa autorización.
3. Conserva Play Protect activado. Si Android detecta una amenaza, no ignores la alerta.
4. Abre **Ruta · Panel**, con el vehículo aparcado. La pantalla inicial explica las limitaciones.
5. Pulsa **He aparcado** y confirma. En **Ajustes y privacidad** elige Maps o Waze y, si quieres, guarda casa y trabajo.
6. Para controlar música, activa opcionalmente el acceso a notificaciones de Ruta desde sus ajustes. Android concede un permiso amplio; la app no procesa el contenido de notificaciones. Si tu móvil restringe ese permiso para apps instaladas desde un archivo, los botones de abrir reproductor siguen funcionando sin él. No hace falta desactivar protecciones para usar el panel básico.
7. Reproduce una canción en Spotify, YouTube Music u otro reproductor que publique una MediaSession. Regresa al panel para mostrar título, artista, carátula y controles disponibles.

Abre **Mapa** para ver Google Maps. La primera vez, pulsa **Permitir ubicación**, lee la explicación y concede ubicación precisa para usar el GPS y la orientación por rumbo.

Ruta muestra la canción, el artista y el reproductor que publica una sesión multimedia en Android. Cuando hay varios reproductores sonando a la vez, da prioridad a Spotify, luego a YouTube Music y después a los demás. Si pausas desde Ruta, el botón de reproducción retoma la misma sesión mientras no empiece a sonar otra. Los controles de pista anterior, siguiente, pausa y reproducción dependen de las acciones que publique el reproductor.

El sonido sale por la salida que ya tenga seleccionada Android (altavoz o Bluetooth del coche). Ruta no empareja dispositivos ni necesita modificar el coche.

## Navegación y pantalla dividida

**Abrir navegación** abre Maps o Waze. **Casa** y **Trabajo** envían la dirección guardada al navegador. **Buscar destino** solo se puede abrir cuando desactivas el modo conducción al aparcar.

En **Mapa**, el punto azul y la cámara siguen tu posición. Si arrastras o manipulas el mapa, pulsa **Centrar mapa** para recuperar el seguimiento. La etiqueta muestra la precisión y la velocidad cuando el proveedor las entrega. Con ubicación aproximada, la velocidad puede no estar disponible.

Escribe un destino en **Mapa** y pulsa **Abrir ruta** para iniciar navegación en Google Maps. Con una velocidad medida superior a 11 km/h, el campo y el botón se desactivan.

Para ver música y GPS simultáneamente, abre la vista de aplicaciones recientes de Android, toca el icono de Ruta, elige pantalla dividida y selecciona Maps o Waze para la otra mitad. El procedimiento y la compatibilidad dependen del móvil. Android puede pausar o cerrar cualquiera de las apps; no se garantiza que una pareja concreta funcione en pantalla dividida.

## Qué protege esta versión

- El mapa solicita Internet y ubicación. Ruta recibe coordenadas mientras la pantalla Mapa está en primer plano y no guarda el historial de ubicaciones.
- Control multimedia mediante la API MediaSession de Android; no inyecta pulsaciones ni usa accesibilidad.
- Servicio de acceso a notificaciones protegido por el permiso de enlace del sistema. No implementa callbacks que lean mensajes o notificaciones.
- El mapa usa el SDK oficial de Google Maps y requiere Servicios de Google Play.
- Direcciones en preferencias privadas locales, excluidas de copias de seguridad (`allowBackup=false`). Se pueden borrar desde ajustes o borrando los datos de la app.
- Google Maps carga el mapa por Internet. Al abrir una ruta, Maps o Waze reciben el destino y usan sus propios permisos y conexiones.
- Sin actividad en segundo plano propia salvo el servicio de acceso a notificaciones opcional gestionado por Android. Los metadatos musicales se consultan mientras el panel está visible.
- Los controles de reproducción se desactivan si la sesión no ofrece la acción correspondiente.
- Modo conducción manual activo en un arranque nuevo. Bloquea ajustes y escritura de destinos dentro de Ruta. No detecta movimiento, no impide cambiar de app y no es una medida de seguridad vial certificada.
- La compilación distribuida se firma con el almacén configurado o con una clave temporal de CI.

## Límites que debes conocer

No existe garantía de riesgo cero. Esta revisión no es una auditoría independiente ni una certificación. No se ha probado en tu teléfono real ni con tus cuentas de Spotify, YouTube Music o aplicaciones de navegación. Los resultados históricos de la versión 1.0.0 están en `tests/ESTADO.md`. Consulta el resultado del workflow para las pruebas de la versión actual.

El sistema puede entregar notificaciones personales al servicio si concedes el acceso especial, aunque esta versión no usa su contenido. Puedes revocarlo en cualquier momento. Una futura actualización debe revisarse antes de instalarla.

El modo conducción es manual: puedes desactivarlo incluso si te mueves; la aplicación no puede verificar que hayas aparcado. Las apps externas pueden mostrar teclados, búsquedas o pantallas que el panel no bloquea. Configura ruta, música y permisos antes de iniciar el trayecto. La pantalla encendida consume batería y puede calentar el dispositivo.

## Compilación revisable

### GitHub Actions

El workflow **Android APK** se ejecuta con cada push, pull request o lanzamiento manual. Compila y audita la APK, la instala en un emulador Android 9 y ejecuta las pruebas de instrumentación. Si todo pasa, descarga **Ruta-Panel-Android** desde **Actions → Android APK → Artifacts**, extrae el ZIP y abre **Ruta-Panel-1.1.0.apk** en el móvil.

Por defecto, cada ejecución usa una clave temporal. Una APK con esa firma se puede instalar desde cero, pero no actualizar una instalación con otra firma, incluida la APK de `dist/`. Para generar actualizaciones desde `main`, configura los secretos de Actions `RUTA_SIGNING_KEY_BASE64` (almacén PKCS12 codificado en Base64, alias `ruta`) y `RUTA_KEY_PASSWORD`. Usa el almacén original para actualizar la APK de `dist/`. Si eliges desinstalar para cambiar de firma, guarda antes las direcciones que quieras conservar, ya que se borran los datos locales.

Si Android rechaza la instalación, comprueba que la versión del móvil sea Android 8 (API 26) o posterior. Con el móvil conectado por USB y ADB disponible, `adb install -r Ruta-Panel-1.1.0.apk` muestra el código exacto del rechazo. `INSTALL_FAILED_UPDATE_INCOMPATIBLE` indica que la firma difiere de la de la instalación existente.

### Activar Google Maps

1. Habilita **Maps SDK for Android** en un proyecto de Google Cloud con facturación habilitada.
2. Restringe la clave al SDK y a la app Android `es.maria.rutapanel`, con la huella SHA-1 de su certificado de firma. Puedes obtener la huella con `apksigner verify --print-certs Ruta-Panel-1.1.0.apk`.
3. Para compilar localmente, define `RUTA_MAPS_API_KEY` en el entorno. Para GitHub Actions, añade el secreto `RUTA_MAPS_API_KEY` junto con los dos secretos de firma estable. La clave queda incluida en la APK, por lo que la restricción por certificado se aplica en Google Cloud.

Las compilaciones de pull requests y las firmadas con clave temporal permiten probar el panel y la interfaz de ubicación sin credenciales de Maps. Para verificar que cargan los mapas, usa una compilación con la clave configurada en un teléfono con Servicios de Google Play.

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

Las pruebas de instrumentación requieren una instalación de Android de prueba con acceso de notificaciones habilitado para el servicio y las APK de app y tests firmadas con la misma clave. Solo se instala `Ruta-Panel-1.0.0.apk` en el móvil personal; el APK de pruebas no forma parte de la entrega.

## Proyecto local en Windows

La APK archivada en `dist/` corresponde a la versión 1.0.0. Para compilar 1.1.0 en Windows, instala JDK 17, Gradle 8.11.1, Android SDK Platform 35 y Build Tools 35.0.0. Ejecuta `./build.ps1 -KeyStore C:\ruta\privada\ruta.p12` en PowerShell. La contraseña se pide de forma oculta y el APK generado queda en `build/`.

## Licencia

MIT. El código de esta aplicación es original. Marcas como Google Maps, Waze, Spotify y YouTube Music pertenecen a sus titulares.
