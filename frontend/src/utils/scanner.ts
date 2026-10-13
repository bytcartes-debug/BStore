import { BarcodeScanner, BarcodeFormat } from '@capacitor-mlkit/barcode-scanning';
import { validarChecksumCodigo } from './barcodeChecksum';

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

/** Formatos restritos para velocidade e precisão da loja */
export const FORMATOS_BARCODE_WEB = [
  'ean_13',
  'ean_8',
  'upc_a',
  'upc_e',
  'code_128',
  'code_39',
  'qr_code',
] as const;

export interface IBarcodeDetector {
  detect(image: ImageBitmapSource): Promise<Array<{ rawValue: string; format: string }>>;
}

/**
 * Obtém o motor de BarcodeDetector para web:
 * 1. Nativo da janela do navegador (Chrome, Edge no Android e PC) - zero overhead.
 * 2. Polyfill WebAssembly (zxing-wasm) via barcode-detector para Safari iOS e Firefox.
 */
export const obterDetectorWeb = async (): Promise<IBarcodeDetector> => {
  if (typeof window !== 'undefined' && 'BarcodeDetector' in window) {
    const NativeDetector = (window as unknown as { BarcodeDetector: new (opts: { formats: string[] }) => IBarcodeDetector }).BarcodeDetector;
    return new NativeDetector({ formats: [...FORMATOS_BARCODE_WEB] });
  }

  // Import dinâmico do polyfill WebAssembly para navegadores sem BarcodeDetector nativo (Safari/Firefox)
  const { BarcodeDetector } = await import('barcode-detector/pure');
  return new BarcodeDetector({ formats: [...FORMATOS_BARCODE_WEB] }) as unknown as IBarcodeDetector;
};

/**
 * Emite som Web Audio positivo (beep de produto adicionado) e vibração.
 */
export function tocarBeepSucesso() {
  try {
    const AudioCtx = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
    if (AudioCtx) {
      const ctx = new AudioCtx();
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();

      osc.type = 'sine';
      osc.frequency.setValueAtTime(880, ctx.currentTime);
      osc.frequency.exponentialRampToValueAtTime(1320, ctx.currentTime + 0.08);

      gain.gain.setValueAtTime(0.12, ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.08);

      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start();
      osc.stop(ctx.currentTime + 0.08);
    }
  } catch {}

  if (typeof navigator !== 'undefined' && 'vibrate' in navigator) {
    try {
      navigator.vibrate(60);
    } catch {}
  }
}

/**
 * Emite som Web Audio diferenciado para código não encontrado e vibração dupla.
 */
export function tocarBeepDesconhecido() {
  try {
    const AudioCtx = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
    if (AudioCtx) {
      const ctx = new AudioCtx();
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();

      osc.type = 'sawtooth';
      osc.frequency.setValueAtTime(440, ctx.currentTime);
      osc.frequency.linearRampToValueAtTime(310, ctx.currentTime + 0.15);

      gain.gain.setValueAtTime(0.14, ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.15);

      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start();
      osc.stop(ctx.currentTime + 0.15);
    }
  } catch {}

  if (typeof navigator !== 'undefined' && 'vibrate' in navigator) {
    try {
      navigator.vibrate([60, 40, 60]);
    } catch {}
  }
}

/**
 * Abre o scanner de código de barras ou QR code.
 * No telemóvel nativo (Android) usa o Google ML Kit do Capacitor.
 * No navegador / computador abre o leitor com detetor nativo ou Wasm sob demanda.
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
        } catch {}
      }
      return null;
    }

    // 2. Verificar se o módulo Google ML Kit está pronto
    try {
      const { available } = await BarcodeScanner.isGoogleBarcodeScannerModuleAvailable();
      if (!available) {
        await BarcodeScanner.installGoogleBarcodeScannerModule();
      }
    } catch {}

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
      if (barcodes.length > 0 && barcodes[0].rawValue) {
        const raw = barcodes[0].rawValue.trim();
        if (validarChecksumCodigo(raw)) {
          tocarBeepSucesso();
          return raw;
        }
      }
      return null;
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
