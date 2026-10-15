/**
 * Utilitário de compressão e normalização de imagens para o serviço de IA.
 * Garante que fotos de câmaras móveis de alta resolução (12MP/48MP) sejam
 * redimensionadas para max 1280px e convertidas para JPEG padrão, reduzindo o payload
 * para ~150-250KB e evitando erros de Payload Too Large (413) e formatos não suportados.
 */
export async function comprimirImagemParaIa(
  file: File,
  maxDim = 1280,
  qualidade = 0.85,
): Promise<{ base64: string; mimeType: string }> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(new Error('Não foi possível ler o ficheiro de imagem.'));
    reader.onload = (e) => {
      const src = e.target?.result as string;
      if (!src) {
        reject(new Error('Ficheiro de imagem vazio.'));
        return;
      }

      const img = new Image();
      img.onerror = () => reject(new Error('Formato de imagem não suportado pelo navegador.'));
      img.onload = () => {
        let width = img.width;
        let height = img.height;

        if (width > maxDim || height > maxDim) {
          if (width > height) {
            height = Math.round((height * maxDim) / width);
            width = maxDim;
          } else {
            width = Math.round((width * maxDim) / height);
            height = maxDim;
          }
        }

        const canvas = document.createElement('canvas');
        canvas.width = Math.max(1, width);
        canvas.height = Math.max(1, height);
        const ctx = canvas.getContext('2d');
        if (!ctx) {
          // Se não conseguir canvas, devolve original
          resolve({ base64: src, mimeType: file.type || 'image/jpeg' });
          return;
        }

        // Fundo branco sólido (evita problemas com transparência PNG)
        ctx.fillStyle = '#FFFFFF';
        ctx.fillRect(0, 0, canvas.width, canvas.height);
        ctx.drawImage(img, 0, 0, canvas.width, canvas.height);

        try {
          const dataUrl = canvas.toDataURL('image/jpeg', qualidade);
          resolve({
            base64: dataUrl,
            mimeType: 'image/jpeg',
          });
        } catch {
          // Fallback caso toDataURL falhe
          resolve({ base64: src, mimeType: file.type || 'image/jpeg' });
        }
      };
      img.src = src;
    };
    reader.readAsDataURL(file);
  });
}
