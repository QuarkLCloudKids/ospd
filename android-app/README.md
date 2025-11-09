# OpSDonwloades - Android App

Una aplicación Android moderna para descargar videos de TikTok con funcionalidad de Quick Settings Tile.

## Características

- 🎵 **Descarga de videos de TikTok** - Interfaz simple y elegante
- 🎯 **Quick Settings Tile** - Acceso rápido desde el centro de control
- 🔄 **Descargas en segundo plano** - No interrumpe tu experiencia
- 🔔 **Notificaciones de progreso** - Mantente informado del estado
- 🤖 **Detección automática** - Detecta cuando TikTok está activo
- 📱 **Material Design 3** - Interfaz moderna y adaptativa

## Requisitos

- Android 7.0 (API 24) o superior
- Android Studio Arctic Fox o superior
- JDK 11 o superior
- Gradle 8.4

## Instalación y Compilación

### 1. Clonar el repositorio
```bash
git clone <repository-url>
cd opsdonwloader/android-app
```

### 2. Abrir en Android Studio
1. Abre Android Studio
2. Selecciona "Open an existing project"
3. Navega a la carpeta `android-app`
4. Espera a que Gradle sincronice

### 3. Configurar el servidor backend
Asegúrate de que el servidor backend esté ejecutándose en `http://localhost:3000` o actualiza la URL en:
```kotlin
// TikTokDownloader.kt
private const val SERVER_URL = "http://tu-servidor:puerto"
```

### 4. Compilar la aplicación
```bash
# Compilar debug
./gradlew assembleDebug

# Compilar release
./gradlew assembleRelease

# Instalar en dispositivo conectado
./gradlew installDebug
```

## Estructura del Proyecto

```
android-app/
├── app/
│   ├── src/main/
│   │   ├── java/com/quarlcloud/opsdonwloades/
│   │   │   ├── MainActivity.kt              # Actividad principal
│   │   │   ├── ui/
│   │   │   │   ├── screens/
│   │   │   │   │   ├── DownloadScreen.kt    # Pantalla de descarga
│   │   │   │   │   └── InfoScreen.kt        # Pantalla de información
│   │   │   │   └── theme/                   # Tema Material Design 3
│   │   │   ├── service/
│   │   │   │   ├── DownloadService.kt       # Servicio de descarga
│   │   │   │   └── TikTokDownloaderTileService.kt # Quick Settings Tile
│   │   │   ├── network/
│   │   │   │   └── TikTokDownloader.kt      # Cliente de red
│   │   │   └── utils/
│   │   │       └── TikTokDetector.kt        # Detector de TikTok
│   │   ├── res/                             # Recursos (layouts, strings, etc.)
│   │   └── AndroidManifest.xml              # Manifiesto de la app
│   └── build.gradle                         # Configuración de la app
├── build.gradle                             # Configuración del proyecto
├── settings.gradle                          # Configuración de Gradle
└── gradle.properties                        # Propiedades de Gradle
```

## Uso

### Descarga desde la aplicación
1. Abre OpSDonwloades
2. Pega la URL del video de TikTok
3. Presiona "Descargar Video"
4. El video se guardará en `Downloads/OpSDonwloades/`

### Quick Settings Tile
1. Ve a Configuración > Centro de control
2. Agrega "OpSDonwloades" a los tiles activos
3. Abre TikTok y ve un video
4. Desliza hacia abajo y toca el tile de OpSDonwloades
5. La descarga comenzará automáticamente

## Permisos Requeridos

- `INTERNET` - Para descargar videos
- `WRITE_EXTERNAL_STORAGE` - Para guardar archivos
- `POST_NOTIFICATIONS` - Para mostrar progreso
- `FOREGROUND_SERVICE` - Para descargas en segundo plano
- `WAKE_LOCK` - Para mantener descargas activas

## Configuración del Quick Settings Tile

Para usar el Quick Settings Tile:

1. **Agregar el tile:**
   - Configuración → Centro de control → Editar
   - Busca "OpSDonwloades" y agrégalo

2. **Usar el tile:**
   - Abre TikTok
   - Ve cualquier video
   - Desliza hacia abajo para abrir el centro de control
   - Toca el tile de OpSDonwloades

## Desarrollo

### Arquitectura
- **MVVM** con Jetpack Compose
- **Coroutines** para operaciones asíncronas
- **Material Design 3** para UI/UX
- **Foreground Services** para descargas
- **Quick Settings Tile API** para acceso rápido

### Dependencias principales
- Jetpack Compose
- Material Design 3
- Coroutines
- OkHttp/Retrofit
- WorkManager

## Troubleshooting

### El servidor no está disponible
- Verifica que el servidor backend esté ejecutándose
- Actualiza la URL del servidor en `TikTokDownloader.kt`
- Verifica la conectividad de red

### El Quick Settings Tile no funciona
- Asegúrate de haber agregado el tile al centro de control
- Verifica que TikTok esté instalado y activo
- Revisa los permisos de la aplicación

### Errores de compilación
- Limpia el proyecto: `./gradlew clean`
- Sincroniza Gradle en Android Studio
- Verifica la versión de JDK (requiere JDK 11+)

## Desarrollado por

**Quarl Cloud** - Soluciones innovadoras para dispositivos móviles

- 📧 info@quarlcloud.com
- 🌐 www.quarlcloud.com

## Licencia

Este proyecto es desarrollado por Quarl Cloud. Todos los derechos reservados.