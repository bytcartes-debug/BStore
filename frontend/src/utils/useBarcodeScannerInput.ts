import { useEffect, useRef } from 'react';
import { validarChecksumCodigo } from './barcodeChecksum';

interface BarcodeScannerInputOptions {
  onScan: (barcode: string) => void;
  enabled?: boolean;
  maxIntervalMs?: number; // padrão 50 ms
  minChars?: number; // padrão 6
  maxChars?: number; // padrão 24
}

/**
 * Hook para intercetar leituras de leitores de código de barras USB / Bluetooth no PC (modo emulação de teclado).
 * Detecta sequências de teclas ultra-rápidas (<50ms entre caracteres) terminadas com Enter.
 */
export function useBarcodeScannerInput({
  onScan,
  enabled = true,
  maxIntervalMs = 50,
  minChars = 6,
  maxChars = 24,
}: BarcodeScannerInputOptions) {
  const onScanRef = useRef(onScan);
  onScanRef.current = onScan;

  useEffect(() => {
    if (!enabled) return;

    let buffer = '';
    let lastKeyTime = 0;
    const intervals: number[] = [];

    const handleKeyDown = (e: KeyboardEvent) => {
      // Ignorar teclas modificadoras
      if (e.ctrlKey || e.altKey || e.metaKey) return;

      const now = performance.now();
      const interval = now - lastKeyTime;

      if (e.key === 'Enter') {
        if (buffer.length >= minChars && buffer.length <= maxChars) {
          // Verificar velocidade: leitor de código digita com menos de 50ms entre caracteres
          const avgInterval = intervals.length > 0
            ? intervals.reduce((a, b) => a + b, 0) / intervals.length
            : 0;

          const isFastScanner = intervals.length >= 3 && avgInterval <= maxIntervalMs;

          if (isFastScanner) {
            const barcode = buffer.trim();
            // Verifica se tem checksum válido se for EAN/UPC
            if (validarChecksumCodigo(barcode)) {
              e.preventDefault();
              e.stopPropagation();

              // Se o foco estava num input de texto, limpa o que foi digitado pelo leitor
              const activeEl = document.activeElement;
              if (activeEl instanceof HTMLInputElement && !activeEl.classList.contains('search-field-input')) {
                // Se era o campo de código de barras, deixa ou preenche
                if (activeEl.id === 'produto-codigo' || activeEl.name === 'codigoBarras') {
                  activeEl.value = barcode;
                }
              }

              onScanRef.current(barcode);
            }
          }
        }
        buffer = '';
        intervals.length = 0;
        return;
      }

      // Aceita apenas caracteres imprimíveis de tamanho 1
      if (e.key.length === 1) {
        // Se demorou mais que 150ms desde o último caractere, reinicia o buffer
        if (interval > 150) {
          buffer = '';
          intervals.length = 0;
        } else if (buffer.length > 0) {
          intervals.push(interval);
        }

        buffer += e.key;
        lastKeyTime = now;
      }
    };

    window.addEventListener('keydown', handleKeyDown, true);
    return () => {
      window.removeEventListener('keydown', handleKeyDown, true);
    };
  }, [enabled, maxIntervalMs, minChars, maxChars]);
}
