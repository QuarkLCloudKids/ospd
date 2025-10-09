(function(){
  console.log('Video Downloader - LoopStudio iniciado');

  // Elementos del DOM
  const videoUrlInput = document.getElementById('videoUrl');
  const downloadBtn = document.getElementById('downloadBtn');
  const messageDiv = document.getElementById('message');

  // Lista de hosts permitidos (solo YouTube y TikTok)
  const allowedHosts = [
    'youtube.com', 'www.youtube.com', 'm.youtube.com', 'youtu.be',
    'tiktok.com', 'www.tiktok.com', 'm.tiktok.com', 'vm.tiktok.com', 'vt.tiktok.com'
  ];

  // Función para validar URL
  function isSupported(url) {
    try {
      const u = new URL(url);
      const host = u.hostname.replace(/^www\./, '');
      
      console.log('Checking URL:', url);
      console.log('Extracted host:', host);
      console.log('Allowed hosts:', allowedHosts);
      
      const isValid = allowedHosts.includes(host);
      console.log('Is valid:', isValid);
      
      return isValid;
    } catch (e) {
      console.log('URL parsing error:', e.message);
      return false;
    }
  }

  // Función para mostrar mensajes
  function showMessage(text, type = 'info') {
    messageDiv.innerHTML = `<div class="${type}">${text}</div>`;
  }

  // Función para limpiar mensajes
  function clearMessage() {
    messageDiv.innerHTML = '';
  }

  // Validación en tiempo real
  function validateInput() {
    const url = videoUrlInput.value.trim();
    
    if (!url) {
      downloadBtn.disabled = true;
      clearMessage();
      return;
    }

    if (isSupported(url)) {
      downloadBtn.disabled = false;
      clearMessage();
      console.log('✅ URL válida:', url);
    } else {
      downloadBtn.disabled = true;
      showMessage('URL de video no válida. Solo se admiten enlaces de YouTube y TikTok.', 'error');
      console.log('❌ URL no válida:', url);
    }
  }

  // Función de descarga
  async function downloadVideo() {
    const url = videoUrlInput.value.trim();
    
    if (!url || !isSupported(url)) {
      showMessage('Por favor, ingresa una URL válida de YouTube o TikTok.', 'error');
      return;
    }

    downloadBtn.disabled = true;
    downloadBtn.textContent = 'Descargando...';
    showMessage('Procesando video, por favor espera...', 'info');

    try {
      const response = await fetch('/api/download', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ url })
      });

      const result = await response.json();

      if (response.ok && result.success) {
        showMessage(`✅ Video descargado exitosamente: ${result.filename}`, 'success');
      } else {
        showMessage(`❌ Error: ${result.error || 'Error desconocido'}`, 'error');
      }
    } catch (error) {
      console.error('Error en la descarga:', error);
      showMessage('❌ Error de conexión. Inténtalo de nuevo.', 'error');
    } finally {
      downloadBtn.disabled = false;
      downloadBtn.textContent = 'Descargar';
      validateInput(); // Re-validar para habilitar/deshabilitar el botón
    }
  }

  // Event listeners
  if (videoUrlInput) {
    videoUrlInput.addEventListener('input', validateInput);
    videoUrlInput.addEventListener('paste', () => {
      setTimeout(validateInput, 100); // Esperar a que se pegue el contenido
    });
  }

  if (downloadBtn) {
    downloadBtn.addEventListener('click', downloadVideo);
  }

  // Función de prueba para debug
  window.testUrl = function(url) {
    const testUrl = url || 'https://vt.tiktok.com/ZSUMhMUek/';
    console.log('Testing URL:', testUrl);
    console.log('Is valid:', isSupported(testUrl));
  };

  // Validación inicial
  validateInput();

  console.log('✅ Video Downloader inicializado correctamente');
})();