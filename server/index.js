const express = require('express');
const cors = require('cors');
const fs = require('fs');
const path = require('path');
const { URL } = require('url');
const ytdl = require('youtube-dl-exec');

const app = express();
const PORT = process.env.PORT || 5000;
const DOWNLOAD_DIR = path.join(__dirname, 'downloads');

if (!fs.existsSync(DOWNLOAD_DIR)) {
  fs.mkdirSync(DOWNLOAD_DIR, { recursive: true });
}

app.use(cors());
app.use(express.json());

const ALLOWED_HOSTS = new Set([
  'youtube.com', 'www.youtube.com', 'm.youtube.com', 'youtu.be',
  'tiktok.com', 'www.tiktok.com', 'm.tiktok.com', 'vm.tiktok.com',
  'facebook.com', 'www.facebook.com', 'm.facebook.com', 'fb.watch'
]);

function isAllowedUrl(raw) {
  try {
    const u = new URL(raw);
    return ALLOWED_HOSTS.has(u.hostname);
  } catch (e) {
    return false;
  }
}

function slugify(s) {
  return s
    .toString()
    .normalize('NFKD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-zA-Z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .toLowerCase()
    .slice(0, 80);
}

app.post('/api/download', async (req, res) => {
  const token = req.headers['x-ad-token'];
  const { url } = req.body || {};

  if (token !== 'ads-ok') {
    return res.status(403).json({ error: 'Ad not completed' });
  }

  if (!url || !isAllowedUrl(url)) {
    return res.status(400).json({ error: 'Invalid or unsupported URL' });
  }

  try {
    // Get metadata to build a deterministic filename
    const info = await ytdl(url, {
      dumpSingleJson: true,
      noWarnings: true,
      noCheckCertificates: true,
      preferFreeFormats: true,
      addHeader: ['referer: https://www.tiktok.com/']
    });

    const title = slugify(info.title || 'video');
    const id = info.id || Date.now().toString();
    const ext = (info.ext || 'mp4').toLowerCase();
    const filename = `${title}-${id}.${ext}`;
    const filepath = path.join(DOWNLOAD_DIR, filename);

    // Download the file
    const proc = ytdl.raw(url, {
      output: filepath,
      noWarnings: true,
      noCheckCertificates: true,
      preferFreeFormats: true,
      addHeader: ['referer: https://www.tiktok.com/']
    });

    proc.on('error', (err) => {
      console.error('yt-dlp error:', err);
    });

    proc.on('close', (code) => {
      if (code !== 0) {
        return res.status(500).json({ error: 'Download failed' });
      }
      // Safety check file exists
      if (!fs.existsSync(filepath)) {
        return res.status(500).json({ error: 'File not found after download' });
      }
      return res.json({ filename });
    });
  } catch (err) {
    console.error(err);
    return res.status(500).json({ error: 'Failed to fetch video info' });
  }
});

app.get('/api/file/:filename', (req, res) => {
  const filename = path.basename(req.params.filename);
  const filepath = path.join(DOWNLOAD_DIR, filename);
  if (!fs.existsSync(filepath)) {
    return res.status(404).json({ error: 'File not found' });
  }
  res.setHeader('Content-Disposition', `attachment; filename="${filename}"`);
  res.sendFile(filepath);
});

app.get('/api/health', (req, res) => {
  res.json({ ok: true });
});

app.listen(PORT, () => {
  console.log(`OPS Downloader server running on http://localhost:${PORT}`);
});