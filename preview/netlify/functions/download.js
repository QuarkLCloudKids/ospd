const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');
const https = require('https');

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

function downloadYtDlp() {
  return new Promise((resolve, reject) => {
    const ytDlpPath = '/tmp/yt-dlp';
    if (fs.existsSync(ytDlpPath)) {
      return resolve(ytDlpPath);
    }

    const file = fs.createWriteStream(ytDlpPath);
    const request = https.get('https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp', (response) => {
      response.pipe(file);
      file.on('finish', () => {
        file.close();
        fs.chmodSync(ytDlpPath, '755');
        resolve(ytDlpPath);
      });
    });

    request.on('error', (err) => {
      fs.unlink(ytDlpPath, () => {});
      reject(err);
    });
  });
}

exports.handler = async (event, context) => {
  if (event.httpMethod !== 'POST') {
    return {
      statusCode: 405,
      body: JSON.stringify({ error: 'Method not allowed' })
    };
  }

  try {
    const { url } = JSON.parse(event.body);
    
    if (!url || !isAllowedUrl(url)) {
      return {
        statusCode: 400,
        body: JSON.stringify({ error: 'URL no válida o no soportada' })
      };
    }

    // Descargar yt-dlp si no existe
    const ytDlpPath = await downloadYtDlp();
    
    // Crear directorio temporal para descargas
    const downloadDir = '/tmp/downloads';
    if (!fs.existsSync(downloadDir)) {
      fs.mkdirSync(downloadDir, { recursive: true });
    }

    return new Promise((resolve) => {
      const args = [
        '--no-playlist',
        '--extract-flat',
        '--print', 'title',
        '--print', 'uploader',
        '--print', 'duration',
        url
      ];

      const child = spawn(ytDlpPath, args, {
        cwd: downloadDir,
        stdio: ['pipe', 'pipe', 'pipe']
      });

      let stdout = '';
      let stderr = '';

      child.stdout.on('data', (data) => {
        stdout += data.toString();
      });

      child.stderr.on('data', (data) => {
        stderr += data.toString();
      });

      child.on('close', (code) => {
        if (code === 0) {
          const lines = stdout.trim().split('\n');
          const title = lines[0] || 'Video';
          const uploader = lines[1] || 'Desconocido';
          const duration = lines[2] || 'Desconocida';

          resolve({
            statusCode: 200,
            headers: {
              'Content-Type': 'application/json',
              'Access-Control-Allow-Origin': '*',
              'Access-Control-Allow-Headers': 'Content-Type, x-ad-token',
              'Access-Control-Allow-Methods': 'POST, OPTIONS'
            },
            body: JSON.stringify({
              success: true,
              title,
              uploader,
              duration,
              message: 'Video procesado exitosamente. Nota: La descarga directa no está disponible en Netlify debido a limitaciones de la plataforma.'
            })
          });
        } else {
          resolve({
            statusCode: 500,
            headers: {
              'Content-Type': 'application/json',
              'Access-Control-Allow-Origin': '*'
            },
            body: JSON.stringify({
              error: 'Error procesando el video',
              details: stderr || 'Error desconocido'
            })
          });
        }
      });

      // Timeout después de 25 segundos (límite de Netlify Functions)
      setTimeout(() => {
        child.kill();
        resolve({
          statusCode: 408,
          headers: {
            'Content-Type': 'application/json',
            'Access-Control-Allow-Origin': '*'
          },
          body: JSON.stringify({
            error: 'Timeout procesando el video',
            details: 'El procesamiento tomó demasiado tiempo'
          })
        });
      }, 25000);
    });

  } catch (error) {
    return {
      statusCode: 500,
      headers: {
        'Content-Type': 'application/json',
        'Access-Control-Allow-Origin': '*'
      },
      body: JSON.stringify({
        error: 'Error interno del servidor',
        details: error.message
      })
    };
  }
};