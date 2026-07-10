# Stream TV Test para Android TV

Aplicación de prueba para navegar y reproducir canales públicos del proyecto IPTV-org desde Android TV.

## Funciones de la versión 0.1.0

- Selector de país basado en `https://iptv-org.github.io/api/countries.json`.
- Argentina seleccionada de forma predeterminada.
- Descarga de la playlist oficial de cada país.
- Caché local de seis horas para abrir más rápido y funcionar ante caídas temporales.
- Búsqueda por nombre, categoría o país.
- Favoritos persistentes, incluso al cambiar de país.
- Navegación mediante control remoto y D-pad.
- Reproducción HLS mediante AndroidX Media3.
- Soporte de `User-Agent` y `Referer` definidos en M3U.
- Cambio automático a otra fuente cuando un stream falla.
- Respaldo desde el fork `yelao1992/iptv`.

## Uso con el control remoto

- **OK:** reproducir el canal.
- **Mantener OK:** agregar o quitar de favoritos.
- **Atrás:** salir del reproductor o regresar desde Favoritos.

## Compilar

El workflow `.github/workflows/build-android-tv.yml` genera el APK automáticamente.

También se puede compilar con Gradle 9.5 y Java 17:

```bash
gradle -p android-tv-app :app:assembleDebug
```

APK resultante:

```text
android-tv-app/app/build/outputs/apk/debug/app-debug.apk
```

## Fuentes

La app no aloja videos. Consume listas y metadatos públicos publicados por IPTV-org. La disponibilidad de cada canal depende del proveedor de la transmisión, la ubicación geográfica y el estado del enlace.
