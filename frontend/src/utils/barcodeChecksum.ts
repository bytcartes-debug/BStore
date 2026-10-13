/**
 * Algoritmos de validação de dígito de controlo e filtro de consenso/cooldown
 * para leitura de códigos de barras (EAN-13, EAN-8, UPC-A, UPC-E).
 */

/** Valida o dígito de controlo de EAN-13 */
export function validarEan13(code: string): boolean {
  if (!/^\d{13}$/.test(code)) return false;
  let sum = 0;
  for (let i = 0; i < 12; i++) {
    const digit = parseInt(code[i], 10);
    sum += i % 2 === 0 ? digit : digit * 3;
  }
  const checkDigit = (10 - (sum % 10)) % 10;
  return checkDigit === parseInt(code[12], 10);
}

/** Valida o dígito de controlo de EAN-8 */
export function validarEan8(code: string): boolean {
  if (!/^\d{8}$/.test(code)) return false;
  let sum = 0;
  for (let i = 0; i < 7; i++) {
    const digit = parseInt(code[i], 10);
    sum += i % 2 === 0 ? digit * 3 : digit;
  }
  const checkDigit = (10 - (sum % 10)) % 10;
  return checkDigit === parseInt(code[7], 10);
}

/** Valida o dígito de controlo de UPC-A (12 dígitos numéricos) */
export function validarUpcA(code: string): boolean {
  if (!/^\d{12}$/.test(code)) return false;
  // UPC-A é equivalente a EAN-13 com zero à esquerda
  return validarEan13('0' + code);
}

/** Valida o dígito de controlo de UPC-E (8 dígitos numéricos) */
export function validarUpcE(code: string): boolean {
  if (!/^\d{8}$/.test(code)) return false;
  // Expande UPC-E para UPC-A e verifica
  const numSystem = code[0];
  const check = parseInt(code[7], 10);
  if (numSystem !== '0' && numSystem !== '1') return false;

  const d1 = code[1];
  const d2 = code[2];
  const d3 = code[3];
  const d4 = code[4];
  const d5 = code[5];
  const d6 = code[6];

  let mfg = '';
  let prod = '';

  switch (d6) {
    case '0':
    case '1':
    case '2':
      mfg = d1 + d2 + d6 + '00';
      prod = '00' + d3 + d4 + d5;
      break;
    case '3':
      mfg = d1 + d2 + d3 + '00';
      prod = '000' + d4 + d5;
      break;
    case '4':
      mfg = d1 + d2 + d3 + d4 + '0';
      prod = '0000' + d5;
      break;
    default:
      mfg = d1 + d2 + d3 + d4 + d5;
      prod = '0000' + d6;
      break;
  }

  const upcA = numSystem + mfg + prod + check;
  return validarUpcA(upcA);
}

/**
 * Valida o código de acordo com o formato detectado ou seu comprimento.
 * Rejeita leituras com checksum incorreto para prevenir adições erradas de produtos.
 */
export function validarChecksumCodigo(code: string, format?: string): boolean {
  const clean = code.trim();
  if (!clean) return false;

  const fmt = (format || '').toLowerCase().replace(/[^a-z0-9]/g, '');

  if (fmt.includes('ean13') || clean.length === 13) {
    if (/^\d{13}$/.test(clean)) return validarEan13(clean);
  }

  if (fmt.includes('ean8') || clean.length === 8) {
    if (/^\d{8}$/.test(clean)) return validarEan8(clean) || validarUpcE(clean);
  }

  if (fmt.includes('upca') || clean.length === 12) {
    if (/^\d{12}$/.test(clean)) return validarUpcA(clean);
  }

  if (fmt.includes('upce')) {
    if (/^\d{8}$/.test(clean)) return validarUpcE(clean);
  }

  // Code 128, Code 39, QR Code e outros alfanuméricos têm verificações internas do motor
  return clean.length >= 2;
}

export interface ConsensoTrackerConfig {
  cooldownMs?: number; // padrão 1200 ms
  consensoJanelaMs?: number; // padrão 400 ms
  leiturasNecessarias?: number; // padrão 2 leituras para web, 1 para nativo
}

export class BarcodeConsensoTracker {
  private lastReadCode: string = '';
  private lastReadTime: number = 0;
  private pendingCode: string = '';
  private pendingCount: number = 0;
  private pendingFirstTime: number = 0;
  private config: Required<ConsensoTrackerConfig>;

  constructor(config?: ConsensoTrackerConfig) {
    this.config = {
      cooldownMs: config?.cooldownMs ?? 1200,
      consensoJanelaMs: config?.consensoJanelaMs ?? 400,
      leiturasNecessarias: config?.leiturasNecessarias ?? 2,
    };
  }

  /**
   * Processa uma leitura bruta vinda do motor de câmara.
   * Retorna o código aprovado ou null se ainda em consenso ou dentro de cooldown.
   */
  processar(code: string, isNativeEngine = false, format?: string): string | null {
    const clean = code.trim();
    if (!clean) return null;

    // 1. Validar checksum se aplicável
    if (!validarChecksumCodigo(clean, format)) {
      if (import.meta.env?.DEV) {
        console.warn(`[Scanner] Código rejeitado por checksum inválido: ${clean} (formato: ${format || 'desconhecido'})`);
      }
      return null;
    }

    const agora = Date.now();

    // 2. Cooldown para o mesmo código
    if (clean === this.lastReadCode && agora - this.lastReadTime < this.config.cooldownMs) {
      return null;
    }

    // 3. Se for motor nativo (ML Kit), 1 leitura é suficiente se checksum estiver válido
    if (isNativeEngine) {
      this.lastReadCode = clean;
      this.lastReadTime = agora;
      this.resetPending();
      return clean;
    }

    // 4. Consenso por 2 leituras em janela de 400ms
    if (clean === this.pendingCode && agora - this.pendingFirstTime <= this.config.consensoJanelaMs) {
      this.pendingCount++;
      if (this.pendingCount >= this.config.leiturasNecessarias) {
        this.lastReadCode = clean;
        this.lastReadTime = agora;
        this.resetPending();
        return clean;
      }
      return null;
    }

    // Novo código potencial
    this.pendingCode = clean;
    this.pendingCount = 1;
    this.pendingFirstTime = agora;
    return null;
  }

  private resetPending() {
    this.pendingCode = '';
    this.pendingCount = 0;
    this.pendingFirstTime = 0;
  }

  reset() {
    this.lastReadCode = '';
    this.lastReadTime = 0;
    this.resetPending();
  }
}
