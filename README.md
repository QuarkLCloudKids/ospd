# Quarkl Cloud - Video Downloader

Una aplicación web moderna para descargar videos de YouTube y TikTok con una interfaz intuitiva y barra de progreso en tiempo real.

## 🚀 Características

- ✅ Descarga videos de YouTube y TikTok
- 📊 Barra de progreso en tiempo real
- 🎨 Interfaz moderna y responsive
- 💰 Integración con Google AdSense
- 🔧 Backend Node.js robusto
- 📱 Compatible con dispositivos móviles

## 🛠️ Tecnologías Utilizadas

- **Frontend**: HTML5, CSS3, JavaScript (Vanilla)
- **Backend**: Node.js, Express
- **Descarga**: yt-dlp
- **Monetización**: Google AdSense

## 📦 Instalación

1. Clona el repositorio:
```bash
git clone https://github.com/tu-usuario/quarkl-cloud-downloader.git
cd quarkl-cloud-downloader
```

2. Instala las dependencias:
```bash
cd preview
npm install
```

3. Inicia el servidor:
```bash
node server.js
```

4. Abre tu navegador en `http://localhost:5500`

## 🎯 Uso

1. Ingresa la URL del video de YouTube o TikTok
2. Haz clic en "Descargar Video"
3. Observa la barra de progreso mientras se procesa
4. El video se descargará automáticamente

## 📁 Estructura del Proyecto

```
opsdonwloader/
├── preview/           # Aplicación principal
│   ├── public/        # Archivos estáticos
│   ├── downloads/     # Videos descargados
│   ├── bin/          # Ejecutables (yt-dlp)
│   └── server.js     # Servidor Node.js
├── frontend/         # Frontend alternativo (React)
└── server/          # Servidor alternativo
```

## 🔧 Configuración

### Variables de Entorno

El proyecto utiliza las siguientes configuraciones:

- Puerto del servidor: `5500`
- Directorio de descargas: `./downloads`
- Ejecutable yt-dlp: `./bin/yt-dlp.bat` (Windows)

### AdSense

Para configurar AdSense, actualiza el ID del publisher en `public/index.html`:

```html
<script async src="https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js?client=ca-pub-TU-ID-AQUI"></script>
```

## 🚀 Deploy

### Netlify

El proyecto incluye configuración para Netlify en `netlify.toml`.

### Otros Servicios

Compatible con cualquier servicio que soporte Node.js como:
- Heroku
- Vercel
- Railway
- DigitalOcean App Platform

## 🤝 Contribuir

1. Fork el proyecto
2. Crea una rama para tu feature (`git checkout -b feature/AmazingFeature`)
3. Commit tus cambios (`git commit -m 'Add some AmazingFeature'`)
4. Push a la rama (`git push origin feature/AmazingFeature`)
5. Abre un Pull Request

## 📄 Licencia

Este proyecto está bajo la Licencia MIT. Ver el archivo `LICENSE` para más detalles.

## 👨‍💻 Desarrollado por

**Quarkl Cloud** - Soluciones tecnológicas innovadoras

---

⭐ ¡No olvides dar una estrella al proyecto si te fue útil!