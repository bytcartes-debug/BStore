import { BarcodeScanner, BarcodeFormat } from '@capacitor-mlkit/barcode-scanning';

/** Devolve true se o plugin de câmara está disponível (Android nativo) */
export const isNative = (): boolean =>
  typeof (window as any).Capacitor !== 'undefined' &&
  (window as any).Capacitor.isNativePlatform();

/**
 * Pede permissão de câmara ao utilizador.
 * Devolve true se concedida.
 */
export const pedirPermissaoCamara = async (): Promise<boolean> => {
  try {
    const { camera } = await BarcodeScanner.requestPermissions();
    return camera === 'granted' || camera === 'limited';
  } catch {
    return false;
  }
};

/**
 * Abre o scanner de código de barras.
 * Devolve o código lido (string) ou null se cancelado/erro.
 */
export const abrirScanner = async (): Promise<string | null> => {
  if (!isNative()) {
    // No browser: pede o código manualmente
    const codigo = window.prompt('Introduza o código de barras manualmente:');
    return codigo?.trim() || null;
  }

  const temPermissao = await pedirPermissaoCamara();
  if (!temPermissao) {
    alert('Permissão de câmara negada. Vá às definições do dispositivo para ativar.');
    return null;
  }

  try {
    const { barcodes } = await BarcodeScanner.scan({
      formats: [
        BarcodeFormat.Ean13,
        BarcodeFormat.Ean8,
        BarcodeFormat.Code128,
        BarcodeFormat.Code39,
        BarcodeFormat.QrCode,
        BarcodeFormat.UpcA,
        BarcodeFormat.UpcE,
      ],
    });
    return barcodes.length > 0 ? (barcodes[0].rawValue ?? null) : null;
  } catch (e: any) {
    // Utilizador cancelou — sem erro visível
    if (e?.message?.includes('cancel') || e?.message?.includes('Cancel')) return null;
    console.error('Erro scanner:', e);
    return null;
  }
};
