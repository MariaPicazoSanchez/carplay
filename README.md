# Ruta · Panel 1.0.0

Panel personal para Android 8 o posterior, en español, con navegación externa y controles locales de música. Proyecto original inspirado en la idea de un panel de coche. No contiene código ni recursos extraídos de CarDash. No es Apple CarPlay ni Android Auto, ni está certificado por Google, Apple o un fabricante de vehículos.

## Instalación

1. Copia `Ruta-Panel-1.0.0.apk` al móvil y ábrela desde Archivos.
2. Si Android lo pide, autoriza temporalmente a Archivos a instalar esta APK. Después revoca esa autorización.
3. Conserva Play Protect activado. Si Android detecta una amenaza, no ignores la alerta.
4. Abre **Ruta · Panel**, con el vehículo aparcado. La pantalla inicial explica las limitaciones.
5. Pulsa **He aparcado** y confirma. En **Ajustes y privacidad** elige Maps o Waze y, si quieres, guarda casa y trabajo.
6. Para controlar música, activa opcionalmente el acceso a notificaciones de Ruta desde sus ajustes. Android concede un permiso amplio; la app no procesa el contenido de notificaciones. Si tu móvil restringe ese permiso para apps instaladas desde un archivo, los botones de abrir reproductor siguen funcionando sin él. No hace falta desactivar protecciones para usar el panel básico.
7. Reproduce una canción en Spotify, YouTube Music u otro reproductor que publique una MediaSession. Regresa al panel para mostrar título, artista, carátula y controles disponibles.

El sonido sale por la salida que ya tenga seleccionada Android (altavoz o Bluetooth del coche). Ruta no empareja dispositivos ni necesita modificar el coche.

## Navegación y pantalla dividida

**Abrir navegación** abre Maps o Waze. **Casa** y **Trabajo** envían la dirección guardada al navegador. **Buscar destino** solo se puede abrir cuando desactivas el modo conducción al aparcar.

No hay mapa incrustado ni instrucciones de giro propias. Para ver música y GPS simultáneamente, abre la vista de aplicaciones recientes de Android, toca el icono de Ruta, elige pantalla dividida y selecciona Maps o Waze para la otra mitad. El procedimiento y la compatibilidad dependen del móvil. Android puede pausar o cerrar cualquiera de las apps; no se garantiza que una pareja concreta funcione en pantalla dividida.

## Qué protege esta versión

- Cero permisos solicitados por la APK: sin Internet, GPS, micrófono, contactos, calendario, cámara, almacenamiento o Bluetooth.
- Control multimedia mediante la API MediaSession de Android; no inyecta pulsaciones ni usa accesibilidad.
- Servicio de acceso a notificaciones protegido por el permiso de enlace del sistema. No implementa callbacks que lean mensajes o notificaciones.
- Sin dependencias de terceros, anuncios, analítica, WebView, descargas ni código remoto.
- Direcciones en preferencias privadas locales, excluidas de copias de seguridad (`allowBackup=false`). Se pueden borrar desde ajustes o borrando los datos de la app.
- La app no hace conexiones de red. Al abrir Maps o Waze, esas aplicaciones reciben el destino y usan sus propios permisos y conexiones. Los reproductores siguen sus propias políticas.
- Sin actividad en segundo plano propia salvo el servicio de acceso a notificaciones opcional gestionado por Android. Los metadatos musicales se consultan mientras el panel está visible.
- Los controles de reproducción se desactivan si la sesión no ofrece la acción correspondiente.
- Modo conducción manual activo en un arranque nuevo. Bloquea ajustes y escritura de destinos dentro de Ruta. No detecta movimiento, no impide cambiar de app y no es una medida de seguridad vial certificada.
- Firma propia RSA de 3072 bits, con esquemas APK v2 y v3; no se usa la clave de CarDash ni una clave de depuración.

## Límites que debes conocer

No existe garantía de riesgo cero. Esta revisión no es una auditoría independiente ni una certificación. No se ha probado en tu teléfono real ni con tus cuentas de Spotify, YouTube Music o aplicaciones de navegación. Han pasado 12 comprobaciones funcionales en Android 9 con una MediaSession real de prueba. Consulta `tests/ESTADO.md` para el alcance y las limitaciones; no se ha probado con los servidores de esos servicios.

El sistema puede entregar notificaciones personales al servicio si concedes el acceso especial, aunque esta versión no usa su contenido. Puedes revocarlo en cualquier momento. Una futura actualización debe revisarse antes de instalarla.

El modo conducción es manual: puedes desactivarlo incluso si te mueves; la aplicación no puede verificar que hayas aparcado. Las apps externas pueden mostrar teclados, búsquedas o pantallas que el panel no bloquea. Configura ruta, música y permisos antes de iniciar el trayecto. La pantalla encendida consume batería y puede calentar el dispositivo.

## Compilación revisable

### GitHub Actions

El workflow **Android APK** se ejecuta con cada push, pull request o lanzamiento manual. Compila y audita la APK, la instala en un emulador Android 9 y ejecuta las pruebas de instrumentación. Si todo pasa, descarga **Ruta-Panel-Android** desde **Actions → Android APK → Artifacts**, extrae el ZIP y abre **Ruta-Panel-1.0.0.apk** en el móvil.

Por defecto, cada ejecución usa una clave temporal. Una APK con esa firma se puede instalar desde cero, pero no actualizar una instalación con otra firma, incluida la APK de `dist/`. Para generar actualizaciones desde `main`, configura los secretos de Actions `RUTA_SIGNING_KEY_BASE64` (almacén PKCS12 codificado en Base64, alias `ruta`) y `RUTA_KEY_PASSWORD`. Usa el almacén original para actualizar la APK de `dist/`. Si eliges desinstalar para cambiar de firma, guarda antes las direcciones que quieras conservar, ya que se borran los datos locales.

Si Android rechaza la instalación, comprueba que la versión del móvil sea Android 8 (API 26) o posterior. Con el móvil conectado por USB y ADB disponible, `adb install -r Ruta-Panel-1.0.0.apk` muestra el código exacto del rechazo. `INSTALL_FAILED_UPDATE_INCOMPATIBLE` indica que la firma difiere de la de la instalación existente.

### Compilación local

Se incluye todo el código de la aplicación, sus recursos vectoriales y las pruebas. Se usan únicamente las APIs de plataforma de Android.

Requisitos: JDK con compilador Java, Python 3, Android SDK plataforma 35 y build-tools 35.0.0. El proyecto se compila sin Gradle ni dependencias descargadas por el proyecto.

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

Descomprime el contenido del ZIP en `C:\Users\maria\Documents\GitHub\carplay`. No se incluyen cambios ni credenciales de un repositorio existente. La APK lista para instalar está en `dist/`. El código queda en la raíz del proyecto.

Para recompilar en Windows, instala un JDK, Android SDK Platform 35 y Build Tools 35.0.0. Ejecuta `./build.ps1 -KeyStore C:\ruta\privada\ruta.p12` en PowerShell. La contraseña se pide de forma oculta. El script de Windows no se ha ejecutado en esta sesión Linux. No hace falta recompilar para instalar la APK de `dist/`.

## Licencia

MIT. El código de esta aplicación es original. Marcas como Google Maps, Waze, Spotify y YouTube Music pertenecen a sus titulares.
