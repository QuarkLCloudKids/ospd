// Archivo de debug para probar la validación de URLs
function testUrl(url) {
  try {
    const u = new URL(url);
    const host = u.hostname.replace(/^www\./,'');
    console.log('URL:', url);
    console.log('Host:', host);
    const isValid = ['youtube.com','m.youtube.com','youtu.be','tiktok.com','m.tiktok.com','vm.tiktok.com','vt.tiktok.com','facebook.com','m.facebook.com','fb.watch'].includes(host);
    console.log('Is valid:', isValid);
    return isValid;
  } catch (e) { 
    console.log('Error:', e);
    return false; 
  }
}

// Test con la URL de TikTok
testUrl('https://vt.tiktok.com/ZSUMhMUek/');