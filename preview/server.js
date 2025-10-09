const http = require('http');
const https = require('https');
const fs = require('fs');
const path = require('path');
const { spawn, spawnSync } = require('child_process');

const PORT = process.env.PORT || 5500;
const PUBLIC_DIR = path.join(__dirname, 'public');
const DOWNLOAD_DIR = path.join(__dirname, 'downloads');
const BIN_DIR = path.join(__dirname, 'bin');
const YTDLP_EXE = path.join(BIN_DIR, process.platform === 'win32' ? 'yt-dlp.bat' : 'yt-dlp');

if (!fs.existsSync(DOWNLOAD_DIR)) fs.mkdirSync(DOWNLOAD_DIR, { recursive: true });
if (!fs.existsSync(BIN_DIR)) fs.mkdirSync(BIN_DIR, { recursive: true });

const ALLOWED_HOSTS = new Set([
  'youtube.com', 'www.youtube.com', 'm.youtube.com', 'youtu.be',
  'tiktok.com', 'www.tiktok.com', 'm.tiktok.com', 'vm.tiktok.com', 'vt.tiktok.com'
]);

function isAllowedUrl(raw) {
  try {
    const u = new URL(raw);
    return ALLOWED_HOSTS.has(u.hostname);
  } catch (e) {
    return false;
  }
}

// Función para extraer ID de video de YouTube
function extractVideoId(url) {
  // Para yt-dlp, no necesitamos extraer un ID específico
  // Solo verificamos que la URL sea válida y esté permitida
  try {
    const urlObj = new URL(url);
    return urlObj.href; // Retornamos la URL completa
  } catch (e) {
    return null;
  }
}

function ensureYtDlp() {
  return new Promise((resolve, reject) => {
    if (fs.existsSync(YTDLP_EXE)) return resolve(YTDLP_EXE);
    const primary = process.platform === 'win32'
      ? 'https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe'
      : 'https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp';
    const mirrors = process.platform === 'win32'
      ? [
          'https://ghproxy.com/https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe',
          'https://github.moeyy.xyz/https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe'
        ]
      : [
          'https://ghproxy.com/https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp',
          'https://github.moeyy.xyz/https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp'
        ];

    const candidates = [primary, ...mirrors];
    const tryNext = (idx, lastErr) => {
      if (idx >= candidates.length) {
        return reject(lastErr || new Error('No se pudo descargar yt-dlp desde ninguna fuente'));
      }
      const url = candidates[idx];
      const file = fs.createWriteStream(YTDLP_EXE);
      const req = https.get(url, { headers: { 'User-Agent': 'Mozilla/5.0' } }, (resp) => {
        if (resp.statusCode !== 200) {
          file.close();
          fs.rmSync(YTDLP_EXE, { force: true });
          return tryNext(idx + 1, new Error('Descarga falló con código ' + resp.statusCode));
        }
        resp.pipe(file);
        file.on('finish', () => {
          file.close();
          if (process.platform !== 'win32') {
            try { fs.chmodSync(YTDLP_EXE, 0o755); } catch {}
          }
          resolve(YTDLP_EXE);
        });
      });
      req.on('error', (err) => {
        try { file.close(); } catch {}
        fs.rmSync(YTDLP_EXE, { force: true });
        tryNext(idx + 1, err);
      });
    };
    tryNext(0);
  });
}

function findYtDlpPath(){
  try {
    if (fs.existsSync(YTDLP_EXE)) {
      // En Windows, si es un archivo .bat local, necesitamos usar .\ para PowerShell
      if (process.platform === 'win32' && YTDLP_EXE.endsWith('.bat')) {
        return YTDLP_EXE;
      }
      return YTDLP_EXE;
    }
    if (process.platform === 'win32') {
      const r = spawnSync('where', ['yt-dlp.exe'], { encoding: 'utf8' });
      const out = (r.stdout || '').split(/\r?\n/).map(s=>s.trim()).filter(Boolean);
      if (out.length) return out[0];
      const r2 = spawnSync('where', ['yt-dlp'], { encoding: 'utf8' });
      const out2 = (r2.stdout || '').split(/\r?\n/).map(s=>s.trim()).filter(Boolean);
      if (out2.length) return out2[0];
    } else {
      const r = spawnSync('which', ['yt-dlp'], { encoding: 'utf8' });
      const out = (r.stdout || '').trim();
      if (out) return out;
    }
  } catch {}
  return null;
}

function sendFile(res, filepath) {
  const ext = path.extname(filepath).toLowerCase();
  const types = {
    '.html': 'text/html; charset=utf-8',
    '.css': 'text/css; charset=utf-8',
    '.js': 'application/javascript; charset=utf-8',
    '.svg': 'image/svg+xml'
  };
  const type = types[ext] || 'text/plain; charset=utf-8';
  fs.createReadStream(filepath).on('error', () => {
    res.writeHead(404);
    res.end('Not found');
  }).on('open', () => {
    res.writeHead(200, { 'Content-Type': type });
  }).pipe(res);
}

const server = http.createServer((req, res) => {
  const url = new URL(req.url, `http://${req.headers.host}`);

  if (req.method === 'GET' && url.pathname === '/api/health') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    return res.end(JSON.stringify({ ok: true }));
  }

  if (req.method === 'POST' && url.pathname === '/api/download') {
    let body = '';
    req.on('data', (chunk) => (body += chunk));
    req.on('end', async () => {
      try {
        const payload = JSON.parse(body || '{}');
        const videoUrl = payload.url;
        if (!videoUrl || !isAllowedUrl(videoUrl)) {
          res.writeHead(400, { 'Content-Type': 'application/json' });
          return res.end(JSON.stringify({ error: 'URL inválida o no soportada' }));
        }

        // Usar yt-dlp.bat para descarga real
        const ytdlpPath = path.join(BIN_DIR, 'yt-dlp.bat');
        if (!fs.existsSync(ytdlpPath)) {
          res.writeHead(500, { 'Content-Type': 'application/json' });
          return res.end(JSON.stringify({ 
            error: 'yt-dlp no encontrado', 
            details: 'yt-dlp.bat no está disponible en ' + ytdlpPath 
          }));
        }

        const videoId = extractVideoId(videoUrl);
        if (!videoId) {
          res.writeHead(400, { 'Content-Type': 'application/json' });
          return res.end(JSON.stringify({ error: 'URL de video no válida' }));
        }

        // Limpiar la URL de parámetros problemáticos
        const cleanUrl = videoUrl.split('&list=')[0].split('&start_radio=')[0].split('&pp=')[0];
        
        // Generar nombre único para evitar conflictos
        const timestamp = Date.now();
        const outputPath = path.join(DOWNLOAD_DIR, `video-${timestamp}.mp4`);
        
        console.log('=== INICIO DESCARGA ===');
        console.log('URL original:', videoUrl);
        console.log('URL limpia:', cleanUrl);
        console.log('Archivo de salida:', outputPath);
        console.log('YTDLP_EXE:', YTDLP_EXE);
        console.log('¿Existe YTDLP_EXE?:', fs.existsSync(YTDLP_EXE));
        
        // Usar la función findYtDlpPath para obtener la ruta correcta
        const ytdlpExecutablePath = findYtDlpPath();
        console.log('Ruta encontrada por findYtDlpPath:', ytdlpExecutablePath);
        
        if (!ytdlpExecutablePath) {
          res.writeHead(500, { 'Content-Type': 'application/json' });
          return res.end(JSON.stringify({ 
            error: 'yt-dlp no encontrado', 
            details: 'yt-dlp no está instalado o no se puede encontrar en el sistema' 
          }));
        }
        
        const args = [
          '--format', 'best[ext=mp4]/mp4/best',
          '--output', outputPath,
          '--no-playlist',
          '--no-check-certificates',
          cleanUrl
        ];
        
        console.log('Argumentos yt-dlp:', args);
        console.log('Ejecutando:', ytdlpExecutablePath);

        const child = spawn(ytdlpExecutablePath, args, { 
          shell: true,  // Cambiar a true para manejar archivos .bat correctamente
          stdio: ['ignore', 'pipe', 'pipe']
        });

        let output = '';
        let errorOutput = '';

        console.log('Ejecutando yt-dlp con argumentos:', args);
        console.log('Ruta de yt-dlp:', ytdlpPath);

        child.stdout.on('data', (data) => {
          const text = data.toString();
          output += text;
          console.log('STDOUT:', text);
        });

        child.stderr.on('data', (data) => {
          const text = data.toString();
          errorOutput += text;
          console.log('STDERR:', text);
        });

        child.on('close', (code) => {
          console.log('yt-dlp terminó con código:', code);
          console.log('Output completo:', output);
          console.log('Error output:', errorOutput);
          
          // Considerar código 1 como éxito si hay warnings pero el archivo se descargó
          if (code === 0 || (code === 1 && output.includes('100%'))) {
            // Buscar el archivo descargado más reciente
            const files = fs.readdirSync(DOWNLOAD_DIR)
              .filter(f => !f.startsWith('.') && f !== 'README.md')
              .map(f => ({ 
                name: f, 
                time: fs.statSync(path.join(DOWNLOAD_DIR, f)).mtimeMs 
              }))
              .sort((a, b) => b.time - a.time);
            
            const latestFile = files.length > 0 ? files[0].name : null;
            
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ 
              success: true, 
              filename: latestFile,
              message: 'Video descargado exitosamente',
              output: output.slice(-500) // Últimas 500 caracteres del output
            }));
          } else {
            res.writeHead(500, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ 
              error: `Error en la descarga (código ${code})`, 
              details: errorOutput || output,
              code: code
            }));
          }
        });

        child.on('error', (err) => {
          res.writeHead(500, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ 
            error: `Error ejecutando yt-dlp: ${err.code}`, 
            details: err.message 
          }));
        });
      } catch (e) {
        res.writeHead(500, { 'Content-Type': 'application/json' });
        return res.end(JSON.stringify({ error: 'Error procesando la solicitud', details: String(e.message || e) }));
      }
    });
    return;
  }

  if (req.method === 'GET' && url.pathname.startsWith('/api/file/')) {
    const filename = path.basename(url.pathname.replace('/api/file/', ''));
    const filepath = path.join(DOWNLOAD_DIR, filename);
    if (!fs.existsSync(filepath)) {
      res.writeHead(404, { 'Content-Type': 'application/json' });
      return res.end(JSON.stringify({ error: 'Archivo no encontrado' }));
    }
    res.writeHead(200, {
      'Content-Type': 'application/octet-stream',
      'Content-Disposition': `attachment; filename="${filename}"`
    });
    fs.createReadStream(filepath).pipe(res);
    return;
  }

  // Static files
  let filePath = path.join(PUBLIC_DIR, url.pathname === '/' ? 'index.html' : url.pathname);
  if (!filePath.startsWith(PUBLIC_DIR)) {
    res.writeHead(403);
    return res.end('Forbidden');
  }
  fs.stat(filePath, (err, stat) => {
    if (err || !stat.isFile()) {
      res.writeHead(404);
      return res.end('Not found');
    }
    sendFile(res, filePath);
  });
});

server.listen(PORT, () => {
  console.log(`Preview server running at http://localhost:${PORT}/`);
});