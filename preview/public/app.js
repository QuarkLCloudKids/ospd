(function(){
  const urlInput = document.getElementById('url');
  const adBtn = document.getElementById('adBtn');
  const dlBtn = document.getElementById('dlBtn');
  const ad = document.getElementById('ad');
  const count = document.getElementById('count');
  const closeBtn = document.getElementById('closeBtn');
  const errorEl = document.getElementById('error');

  // Desactivar por completo el panel y el botón de anuncio
  if (ad) { ad.hidden = true; ad.classList.add('hidden'); }
  if (closeBtn) { closeBtn.disabled = true; }
  if (adBtn) { adBtn.style.display = 'none'; }

  function isSupported(url){
    try {
      const u = new URL(url);
      const host = u.hostname.replace(/^www\./,'');
      return ['youtube.com','m.youtube.com','youtu.be','tiktok.com','m.tiktok.com','vm.tiktok.com','vt.tiktok.com','facebook.com','m.facebook.com','fb.watch'].includes(host);
    } catch { return false; }
  }

  let unlocked = false;
  let interval = null;
  let fallback = null;

  function hideAd(){
    // Cierra el overlay con redundancia por si algún navegador ignora "hidden"
    ad.hidden = true;
    ad.classList.add('hidden');
    if (interval) { clearInterval(interval); interval = null; }
    if (fallback) { clearTimeout(fallback); fallback = null; }
  }
  adBtn.addEventListener('click', () => {
    errorEl.hidden = true;
    unlocked = false;
    ad.hidden = false;
    ad.classList.remove('hidden');
    // Habilitamos el botón cerrar desde el inicio del anuncio
    closeBtn.disabled = false;
    let s = 15;
    count.textContent = s;
    if (interval) clearInterval(interval);
    if (fallback) clearTimeout(fallback);
    interval = setInterval(() => {
      s -= 1;
      if (s < 0) s = 0;
      count.textContent = s;
      if (s <= 0) {
        clearInterval(interval);
        unlocked = true;
        // Ocultamos inmediatamente; si por alguna razón falla, el fallback garantiza cierre.
        hideAd();
        update();
      }
    }, 1000);
    // Fallback por si el navegador pausa timers: fuerza cierre tras 17s
    fallback = setTimeout(() => {
      if (!unlocked) {
        unlocked = true;
      }
      hideAd();
      update();
    }, 17000);
  });

  // Permitir cerrar manualmente el panel una vez desbloqueado
  closeBtn.addEventListener('click', () => {
    hideAd();
    update();
  });

  // Cerrar con tecla ESC si ya está desbloqueado
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && !ad.hidden) {
      hideAd();
      update();
    }
  });

  // Clic en el fondo para cerrar si ya está desbloqueado
  ad.addEventListener('click', (e) => {
    if (e.target === ad) {
      hideAd();
      update();
    }
  });

  urlInput.addEventListener('input', update);
  function update(){ dlBtn.disabled = !isSupported(urlInput.value); }
  update();

  dlBtn.addEventListener('click', async () => {
    errorEl.hidden = true;
    dlBtn.disabled = true;
    dlBtn.textContent = 'Descargando...';
    
    try {
      const r = await fetch('/api/download', {
        method: 'POST', headers: { 'Content-Type': 'application/json', 'x-ad-token':'ads-ok' }, body: JSON.stringify({ url: urlInput.value })
      });
      const data = await r.json().catch(() => ({}));
      if (!r.ok) {
        const msg = [data.error || 'Error procesando la solicitud', data.details].filter(Boolean).join(' · ');
        errorEl.textContent = msg;
        errorEl.hidden = false;
        return;
      }
      
      // Verificar que el archivo existe antes de intentar descargarlo
      if (data.filename) {
        // Crear un enlace temporal para descargar el archivo
        const downloadLink = document.createElement('a');
        downloadLink.href = '/api/file/' + encodeURIComponent(data.filename);
        downloadLink.download = data.filename;
        downloadLink.style.display = 'none';
        document.body.appendChild(downloadLink);
        downloadLink.click();
        document.body.removeChild(downloadLink);
        
        // Mostrar mensaje de éxito
        errorEl.textContent = 'Descarga iniciada exitosamente';
        errorEl.style.color = 'green';
        errorEl.hidden = false;
        setTimeout(() => {
          errorEl.hidden = true;
          errorEl.style.color = '';
        }, 3000);
      } else if (data.success && data.message) {
        // Mostrar información del video procesado
        errorEl.innerHTML = `
          <strong>Video procesado:</strong><br>
          <strong>Título:</strong> ${data.title || 'N/A'}<br>
          <strong>Canal:</strong> ${data.uploader || 'N/A'}<br>
          <strong>Duración:</strong> ${data.duration || 'N/A'}<br>
          <br>
          <em>${data.message}</em>
        `;
        errorEl.style.color = '#ff6b35';
        errorEl.hidden = false;
        setTimeout(() => {
          errorEl.hidden = true;
          errorEl.style.color = '';
        }, 8000);
      } else {
        errorEl.textContent = 'Error: No se pudo obtener el archivo descargado';
        errorEl.hidden = false;
      }
    } catch(e){
      errorEl.textContent = e && e.message ? e.message : 'Error de red';
      errorEl.hidden = false;
    } finally {
      dlBtn.disabled = false;
      dlBtn.textContent = 'Descargar';
    }
  });
})();