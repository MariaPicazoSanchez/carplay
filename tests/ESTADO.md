# Estado de validación

## Comprobado

- Compilación del código propio con Android SDK 35.
- Firma v2/v3 de la APK verificada con apksigner.
- Cero permisos solicitados y app no depurable; comprobaciones en `auditoria-apk.txt`.
- Instalación real en un emulador Android 9 (API 28).
- Las 12 comprobaciones funcionales de instrumentación han pasado; resultado en `resultados-instrumentacion.txt`.
- Título y artista recibidos desde una MediaSession real de prueba.
- Reproducir, pausar, siguiente y anterior enviados a la sesión y recibidos por sus callbacks.
- Modo conducción inicial, bloqueo manual, ausencia de permisos y manejo de apps ausentes.
- Desactivación de los controles al cerrar la sesión de música.
- Arranque vertical tras revocar el acceso a notificaciones y captura visual revisada.

## Pendiente o limitado

- La comprobación visual horizontal NO se ha completado: dos intentos de UiAutomator no obtuvieron una raíz de interfaz tras la rotación. No hay una captura horizontal válida ni se da por validada esa orientación.
- La primera imagen de Android 15 (API 35) falló en el arranque del propio sistema antes de instalar la app; ese intento no valida ni invalida el funcionamiento de Ruta.
- No se ha probado en el móvil real de la usuaria, con las apps/cuentas reales de Spotify o YouTube Music, ni con navegación real de Maps/Waze.
- El script PowerShell no se ha ejecutado en Windows.
- No existe garantía de riesgo cero, auditoría independiente ni certificación de seguridad vial.
