import { BarcodeScanner, BarcodeFormat } from '@capacitor-mlkit/barcode-scanning';

/** Devolve true se o plugin de câmara nativo está disponível (Android nativo Capacitor) */
export const isNative = (): boolean =>
  typeof (window as unknown as { Capacitor?: { isNativePlatform: () => boolean } }).Capacitor !==
    'undefined' &&
  (
    window as unknown as { Capacitor: { isNativePlatform: () => boolean } }
  ).Capacitor.isNativePlatform();

type ScannerUiListener = (open: boolean) => void;
type ScannerResolver = (codigo: string | null) => void;

let uiListener: ScannerUiListener | null = null;
let currentResolver: ScannerResolver | null = null;

/** Regista o componente React que apresenta o modal de scanner na web */
export const registerScannerUI = (listener: ScannerUiListener): (() => void) => {
  uiListener = listener;
  return () => {
    if (uiListener === listener) {
      uiListener = null;
    }
  };
};

/** Chamado pelo modal de câmara quando um código é lido ou quando o utilizador cancela */
export const resolverScannerWeb = (codigo: string | null) => {
  if (currentResolver) {
    const fn = currentResolver;
    currentResolver = null;
    fn(codigo);
  }
  if (uiListener) {
    uiListener(false);
  }
};

/**
 * Pede permissão de câmara ao utilizador.
 * Devolve true se concedida.
 */
export const pedirPermissaoCamara = async (): Promise<boolean> => {
  if (!isNative()) {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: true });
      stream.getTracks().forEach((track) => track.stop());
      return true;
    } catch {
      return false;
    }
  }

  try {
    const { camera } = await BarcodeScanner.requestPermissions();
    return camera === 'granted' || camera === 'limited';
  } catch {
    return false;
  }
};

/**
 * Abre o scanner de código de barras ou QR code.
 * No telemóvel nativo (Android) usa o Google ML Kit do Capacitor.
 * No navegador / computador abre o modal de câmara integrado.
 * Devolve o código lido (string) ou null se cancelado.
 */
export const abrirScanner = async (): Promise<string | null> => {
  if (isNative()) {
    // 1. Pedir permissão de câmara apenas no momento do clique
    const temPermissao = await pedirPermissaoCamara();
    if (!temPermissao) {
      const abrirDef = window.confirm(
        'Permissão de câmara recusada. Deseja abrir as definições da aplicação no telemóvel para autorizar a câmara?',
      );
      if (abrirDef) {
        try {
          await BarcodeScanner.openSettings();
        } catch {
          // Ignorar erro ao abrir definições
        }
      }
      return null;
    }

    // 2. Verificar se o módulo Google ML Kit está pronto ou precisa de descarregar
    try {
      const { available } = await BarcodeScanner.isGoogleBarcodeScannerModuleAvailable();
      if (!available) {
        await BarcodeScanner.installGoogleBarcodeScannerModule();
      }
    } catch {
      // Em alguns dispositivos sem Google Play Services ou emuladores, prosseguir diretamente
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
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : String(e);
      if (msg.toLowerCase().includes('cancel')) return null;
      console.error('Erro scanner nativo:', e);
      return null;
    }
  }

  // No navegador web: abre o modal de scanner com câmara ao vivo
  if (!uiListener) {
    const codigo = window.prompt('Introduza o código de barras ou QR code:');
    return codigo?.trim() || null;
  }

  return new Promise<string | null>((resolve) => {
    currentResolver = resolve;
    uiListener?.(true);
  });
};
