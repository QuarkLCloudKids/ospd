import { useEffect, useMemo, useState } from 'react'
import './App.css'

function isSupported(url) {
  try {
    const u = new URL(url)
    const host = u.hostname.replace(/^www\./, '')
    return [
      'youtube.com','m.youtube.com','youtu.be',
      'tiktok.com','m.tiktok.com','vm.tiktok.com',
      'facebook.com','m.facebook.com','fb.watch'
    ].includes(host)
  } catch {
    return false
  }
}

export default function App() {
  const [url, setUrl] = useState('')
  const [adOpen, setAdOpen] = useState(false)
  const [countdown, setCountdown] = useState(15)
  const [unlocked, setUnlocked] = useState(false)
  const [downloading, setDownloading] = useState(false)
  const [error, setError] = useState('')

  const canDownload = useMemo(() => unlocked && isSupported(url), [unlocked, url])

  useEffect(() => {
    let t
    if (adOpen) {
      setCountdown(15)
      t = setInterval(() => {
        setCountdown((s) => {
          if (s <= 1) {
            clearInterval(t)
            setAdOpen(false)
            setUnlocked(true)
            return 0
          }
          return s - 1
        })
      }, 1000)
    }
    return () => t && clearInterval(t)
  }, [adOpen])

  async function handleDownload() {
    setError('')
    if (!canDownload) {
      setError('URL no soportada o anuncio no completado')
      return
    }
    try {
      setDownloading(true)
      const r = await fetch('/api/download', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'x-ad-token': 'ads-ok'
        },
        body: JSON.stringify({ url })
      })
      const data = await r.json()
      if (!r.ok) throw new Error(data?.error || 'Error al descargar')
      const filename = data.filename
      window.location.href = `/api/file/${encodeURIComponent(filename)}`
    } catch (e) {
      setError(e.message)
    } finally {
      setDownloading(false)
    }
  }

  return (
    <div className="container">
      <header>
        <h1>OPS Downloader</h1>
        <p className="subtitle">Descarga videos de YouTube, TikTok (sin marca de agua) y Facebook</p>
      </header>

      <div className="card">
        <label htmlFor="url">Pega el enlace del video:</label>
        <input
          id="url"
          type="url"
          placeholder="https://..."
          value={url}
          onChange={(e) => setUrl(e.target.value)}
        />

        <div className="actions">
          {!unlocked && (
            <button className="ad-btn" onClick={() => setAdOpen(true)}>Ver anuncio para desbloquear</button>
          )}
          <button className="download-btn" disabled={!canDownload || downloading} onClick={handleDownload}>
            {downloading ? 'Preparando...' : 'Descargar'}
          </button>
        </div>

        {error && <p className="error">{error}</p>}
        <p className="notice">Usa esta herramienta solo con contenido que tienes derecho a descargar y de acuerdo con los términos de cada plataforma.</p>
      </div>

      {adOpen && (
        <div className="ad-overlay" role="dialog" aria-modal="true">
          <div className="ad-box">
            <h2>Anuncio</h2>
            <div className="ad-content">
              <div className="ad-placeholder">Tu anuncio aquí</div>
            </div>
            <p>Descarga desbloqueada en {countdown}s</p>
          </div>
        </div>
      )}
    </div>
  )
}
