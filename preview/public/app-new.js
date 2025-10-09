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

  // Función para mostrar/ocultar la barra de progreso
  function showProgressBar(show = true) {
    const progressContainer = document.querySelector('#progressContainer');
    if (progressContainer) {
      progressContainer.style.display = show ? 'block' : 'none';
    }
  }

  // Función para actualizar la barra de progreso
  function updateProgress(percentage, text = null) {
    const progressBar = document.querySelector('#progressBar');
    const progressText = document.querySelector('#progressText');
    
    if (progressBar) {
      progressBar.style.width = `${percentage}%`;
    }
    
    if (progressText) {
      progressText.textContent = text || `${percentage}%`;
    }
  }

  // Función para simular progreso (ya que yt-dlp no proporciona progreso real)
  function simulateProgress() {
    return new Promise((resolve) => {
      let progress = 0;
      const interval = setInterval(() => {
        progress += Math.random() * 15; // Incremento aleatorio
        if (progress > 90) progress = 90; // No pasar del 90% hasta completar
        
        updateProgress(Math.floor(progress));
        
        if (progress >= 90) {
          clearInterval(interval);
          resolve();
        }
      }, 500); // Actualizar cada 500ms
    });
  }

  // Función principal de descarga
  async function downloadVideo() {
    const url = videoUrlInput.value.trim();
    
    if (!url || !isSupported(url)) {
      showMessage('❌ Por favor, ingresa una URL válida de YouTube o TikTok', 'error');
      return;
    }

    // Deshabilitar botón y mostrar estado de carga
    downloadBtn.disabled = true;
    downloadBtn.textContent = 'Procesando...';
    
    // Limpiar mensajes anteriores y mostrar estado inicial
    clearMessage();
    showMessage('🔄 Procesando video...', 'info');
    
    // Mostrar barra de progreso
    showProgressBar(true);
    updateProgress(0, 'Iniciando descarga...');

    // Pequeño retraso para asegurar que el mensaje se muestre
    await new Promise(resolve => setTimeout(resolve, 100));

    try {
      // Iniciar simulación de progreso
      const progressPromise = simulateProgress();
      
      const response = await fetch('/api/download', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ url })
      });

      // Esperar a que termine la simulación de progreso
      await progressPromise;
      
      const result = await response.json();

      if (response.ok && result.success) {
        // Completar la barra de progreso
        updateProgress(100, 'Descarga completada');
        
        // Pequeña pausa para mostrar el 100%
        await new Promise(resolve => setTimeout(resolve, 500));
        
        showMessage(`✅ Video descargado exitosamente: ${result.filename}`, 'success');
        
        // Crear enlace de descarga
        if (result.filename) {
          const downloadLink = document.createElement('a');
          downloadLink.href = `/api/file/${result.filename}`;
          downloadLink.download = result.filename;
          downloadLink.textContent = `📥 Descargar ${result.filename}`;
          downloadLink.style.display = 'block';
          downloadLink.style.marginTop = '10px';
          downloadLink.style.color = '#4CAF50';
          downloadLink.style.textDecoration = 'none';
          downloadLink.style.fontWeight = 'bold';
          downloadLink.style.padding = '8px 16px';
          downloadLink.style.backgroundColor = '#f0f8f0';
          downloadLink.style.borderRadius = '4px';
          downloadLink.style.border = '1px solid #4CAF50';
          
          // Agregar el enlace después del mensaje
          const messageDiv = document.querySelector('#message');
          if (messageDiv) {
            messageDiv.appendChild(downloadLink);
          }
        }
      } else {
        updateProgress(0, 'Error en descarga');
        showMessage(`❌ Error: ${result.error || 'Error desconocido'}`, 'error');
        console.error('Error del servidor:', result);
      }
    } catch (error) {
      updateProgress(0, 'Error de conexión');
      console.error('Error en la descarga:', error);
      showMessage('❌ Error de conexión. Inténtalo de nuevo.', 'error');
    } finally {
      // Ocultar barra de progreso después de un momento
      setTimeout(() => {
        showProgressBar(false);
      }, 2000);
      
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