# Alcance de la revisión

La revisión está centrada en el código propio y en la APK compilada. No es
una auditoría independiente, una prueba de penetración completa, un análisis
de todas las vulnerabilidades del sistema operativo o una homologación vial.

Comprobaciones estáticas:
- APK sin permisos solicitados, aplicación no depurable.
- Copia de seguridad y tráfico HTTP en claro desactivados en el manifiesto binario.
- Servicio de notificaciones protegido por BIND_NOTIFICATION_LISTENER_SERVICE.
- Firma APK verificada por apksigner (v2/v3).
- Sin bibliotecas nativas, dependencias de terceros ni código remoto.
- Sin lectura de notificaciones personales, WebView o ejecución de procesos.
- Dirección codificada al construir URI para los navegadores.

Comprobaciones dinámicas previstas en tests/src:
- Arranque con modo conducción activo y ajustes bloqueados.
- Ausencia de controles de música utilizables sin sesión.
- Manejo de ausencia de apps de navegación o reproductores.
- Recepción de título y artista desde una MediaSession de prueba.
- Envío de reproducir, pausar, anterior y siguiente a esa sesión.
- Desactivación de controles al terminar la sesión.
- Arranque en vertical y horizontal tras revocar acceso a notificaciones.

Los resultados reales, cuando estén disponibles, se adjuntan en los archivos
de resultados del directorio tests. La presencia de código de pruebas no
implica que se haya ejecutado con éxito. No se ha probado en el móvil de la
usuaria ni con las aplicaciones y cuentas reales de mapas o streaming.
