exports.handler = async (event, context) => {
  // Netlify Functions no puede servir archivos descargados de forma persistente
  // Esta función devuelve un mensaje explicativo
  
  return {
    statusCode: 501,
    headers: {
      'Content-Type': 'application/json',
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Headers': 'Content-Type',
      'Access-Control-Allow-Methods': 'GET, OPTIONS'
    },
    body: JSON.stringify({
      error: 'Descarga no disponible',
      message: 'Las descargas directas no están disponibles en Netlify debido a limitaciones de la plataforma. Los archivos no se pueden almacenar de forma persistente en el servidor.',
      suggestion: 'Para descargas funcionales, considera usar un servidor dedicado o una plataforma que soporte almacenamiento persistente.'
    })
  };
};