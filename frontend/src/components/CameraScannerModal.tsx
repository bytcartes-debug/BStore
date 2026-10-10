import React, { useEffect, useRef, useState, useCallback } from 'react';
import {
  Flashlight,
  FlashlightOff,
  RotateCw,
  Upload,
  Check,
  Keyboard,
  AlertCircle,
} from 'lucide-react';
import { Html5Qrcode, Html5QrcodeSupportedFormats } from 'html5-qrcode';
import { Modal, Spinner } from './UI';

interface CameraScannerModalProps {
  isOpen: boolean;
  onScan: (codigo: string) => void;
  onClose: () => void;
  title?: string;
  subtitle?: string;
}

const SUPPORTED_FORMATS = [
  Html5QrcodeSupportedFormats.QR_CODE,
  Html5QrcodeSupportedFormats.EAN_13,
  Html5QrcodeSupportedFormats.EAN_8,
  Html5QrcodeSupportedFormats.CODE_128,
  Html5QrcodeSupportedFormats.CODE_39,
  Html5QrcodeSupportedFormats.UPC_A,
  Html5QrcodeSupportedFormats.UPC_E,
  Html5QrcodeSupportedFormats.DATA_MATRIX,
  Html5QrcodeSupportedFormats.ITF,
];

function playScanFeedback() {
  try {
    const AudioCtx =
      window.AudioContext ||
      (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
    if (AudioCtx) {
      const ctx = new AudioCtx();
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = 'sine';
      osc.frequency.setValueAtTime(880, ctx.currentTime);
      gain.gain.setValueAtTime(0.12, ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.1);
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start();
      osc.stop(ctx.currentTime + 0.1);
    }
  } catch {
    // Ignore audio feedback errors
  }
  if (typeof navigator !== 'undefined' && 'vibrate' in navigator) {
    try {
      navigator.vibrate(80);
    } catch {
      // Ignore vibration errors
    }
  }
}

export const CameraScannerModal: React.FC<CameraScannerModalProps> = ({
  isOpen,
  onScan,
  onClose,
  title = 'Ler código com câmara',
  subtitle = 'Aponte a câmara para o QR code ou código de barras.',
}) => {
  const containerId = 'bstore-camera-reader-viewport';
  const scannerRef = useRef<Html5Qrcode | null>(null);
  const isStoppingRef = useRef(false);
  const isStartingRef = useRef(false);
  const onScanRef = useRef(onScan);
  onScanRef.current = onScan;
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [isReady, setIsReady] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [scannedCode, setScannedCode] = useState<string | null>(null);
  const [hasTorch, setHasTorch] = useState(false);
  const [torchOn, setTorchOn] = useState(false);
  const [cameras, setCameras] = useState<Array<{ id: string; label: string }>>([]);
  const [cameraIndex, setCameraIndex] = useState(0);
  const [manualCode, setManualCode] = useState('');
  const [showManual, setShowManual] = useState(false);

  const handleFinishScan = useCallback(async (code: string) => {
    const clean = code.trim();
    if (!clean) return;
    playScanFeedback();
    setScannedCode(clean);

    // Parar o leitor
    if (scannerRef.current && !isStoppingRef.current) {
      isStoppingRef.current = true;
      try {
        if (scannerRef.current.isScanning) {
          await scannerRef.current.stop();
        }
        scannerRef.current.clear();
      } catch {
        // Ignorar erros ao parar
      }
    }

    // Pequena pausa para o utilizador ver a confirmacao visual
    setTimeout(() => {
      onScanRef.current(clean);
    }, 250);
  }, []);

  const startScanner = useCallback(
    async (cameraIdOrFacing: string | { facingMode: string }) => {
      if (isStartingRef.current || isStoppingRef.current) return;
      isStartingRef.current = true;
      setErrorMsg(null);
      setIsReady(false);
      try {
        if (!scannerRef.current) {
          scannerRef.current = new Html5Qrcode(containerId, {
            formatsToSupport: SUPPORTED_FORMATS,
            verbose: false,
          });
        }

        const scanner = scannerRef.current;
        if (scanner.isScanning) {
          await scanner.stop();
        }

        const config = {
          fps: 12,
          qrbox: (viewWidth: number, viewHeight: number) => {
            const minEdge = Math.min(viewWidth, viewHeight);
            const size = Math.max(160, Math.floor(minEdge * 0.72));
            return { width: size, height: size };
          },
          aspectRatio: 1.0,
        };

        await scanner.start(
          cameraIdOrFacing,
          config,
          (decodedText) => {
            void handleFinishScan(decodedText);
          },
          () => {
            // Ignorar falhas de deteccao de cada fotograma
          },
        );

        setIsReady(true);

        // Verificar suporte a lanterna
        try {
          const track = scanner.getRunningTrackCameraCapabilities();
          if (track && (track as unknown as { torch?: boolean }).torch) {
            setHasTorch(true);
          }
        } catch {
          setHasTorch(false);
        }
      } catch (err: unknown) {
        console.warn('Erro ao abrir câmara:', err);
        const strErr = String(err);
        if (strErr.includes('NotAllowedError') || strErr.includes('Permission')) {
          setErrorMsg(
            'Permissão de câmara negada. Permita o acesso à câmara no navegador ou introduza o código manualmente.',
          );
        } else if (strErr.includes('NotFoundError') || strErr.includes('DevicesNotFoundError')) {
          setErrorMsg(
            'Nenhuma câmara detetada neste dispositivo. Pode introduzir o código manualmente.',
          );
        } else {
          setErrorMsg(
            'Não foi possível ligar a câmara. Pode carregar uma imagem ou introduzir o código manualmente.',
          );
        }
        setShowManual(true);
      } finally {
        isStartingRef.current = false;
      }
    },
    [handleFinishScan],
  );

  useEffect(() => {
    if (!isOpen) return;
    isStoppingRef.current = false;
    setScannedCode(null);
    setManualCode('');
    setErrorMsg(null);

    // Listar câmaras disponíveis
    void Html5Qrcode.getCameras()
      .then((devices) => {
        if (devices && devices.length > 0) {
          setCameras(devices);
        }
      })
      .catch(() => {
        // Falha normal se permissão ainda não concedida
      });

    // Iniciar por defeito com a câmara traseira
    void startScanner({ facingMode: 'environment' });

    return () => {
      isStoppingRef.current = true;
      if (scannerRef.current) {
        const s = scannerRef.current;
        if (s.isScanning) {
          void s
            .stop()
            .catch(() => {})
            .finally(() => {
              try {
                s.clear();
              } catch {
                // ignorar
              }
            });
        } else {
          try {
            s.clear();
          } catch {
            // ignorar
          }
        }
      }
    };
  }, [isOpen, startScanner]);

  const handleToggleTorch = async () => {
    if (!scannerRef.current || !hasTorch) return;
    try {
      const nextTorch = !torchOn;
      await scannerRef.current.applyVideoConstraints({
        advanced: [{ torch: nextTorch } as MediaTrackConstraintSet],
      });
      setTorchOn(nextTorch);
    } catch {
      // Ignorar falha de lanterna
    }
  };

  const handleSwitchCamera = () => {
    if (cameras.length <= 1) return;
    const nextIndex = (cameraIndex + 1) % cameras.length;
    setCameraIndex(nextIndex);
    setTorchOn(false);
    void startScanner(cameras[nextIndex].id);
  };

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setErrorMsg(null);
    try {
      if (!scannerRef.current) {
        scannerRef.current = new Html5Qrcode(containerId, {
          formatsToSupport: SUPPORTED_FORMATS,
          verbose: false,
        });
      }
      const scanner = scannerRef.current;
      if (scanner.isScanning) {
        await scanner.stop();
      }
      const decodedText = await scanner.scanFile(file, true);
      void handleFinishScan(decodedText);
    } catch {
      setErrorMsg('Nenhum QR code ou código de barras foi encontrado nesta imagem.');
    }
  };

  const handleManualSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!manualCode.trim()) return;
    void handleFinishScan(manualCode.trim());
  };

  if (!isOpen) return null;

  return (
    <Modal title={title} onClose={onClose}>
      <p className="scanner-modal-subtitle">{subtitle}</p>

      <div className="scanner-modal-content">
        {errorMsg && (
          <div className="scanner-notice-error" role="alert">
            <AlertCircle size={18} aria-hidden="true" />
            <span>{errorMsg}</span>
          </div>
        )}

        {/* Área do leitor de câmara */}
        <div className="scanner-viewport-box">
          <div id={containerId} className="scanner-video-feed" />

          {!isReady && !errorMsg && (
            <div className="scanner-loading-overlay">
              <Spinner size="normal" />
              <p>A iniciar câmara…</p>
            </div>
          )}

          {isReady && (
            <div className="scanner-reticle-overlay" aria-hidden="true">
              <div
                className={`scanner-target-reticle ${scannedCode ? 'scanner-target-success' : ''}`}
              >
                <span className="scanner-corner top-left" />
                <span className="scanner-corner top-right" />
                <span className="scanner-corner bottom-left" />
                <span className="scanner-corner bottom-right" />
                {!scannedCode && <div className="scanner-laser" />}
                {scannedCode && (
                  <div className="scanner-success-badge">
                    <Check size={28} strokeWidth={3} />
                  </div>
                )}
              </div>
              <div className="scanner-guide-text">
                {scannedCode ? `Código: ${scannedCode}` : 'Aponte o código para o quadrado'}
              </div>
            </div>
          )}
        </div>

        {/* Controlos da câmara */}
        <div className="scanner-controls-bar">
          {hasTorch && (
            <button
              type="button"
              className={`btn-secondary scanner-control-btn ${torchOn ? 'active' : ''}`}
              onClick={() => void handleToggleTorch()}
              title={torchOn ? 'Desligar lanterna' : 'Ligar lanterna'}
            >
              {torchOn ? <FlashlightOff size={18} /> : <Flashlight size={18} />}
              <span>{torchOn ? 'Lanterna ligada' : 'Lanterna'}</span>
            </button>
          )}

          {cameras.length > 1 && (
            <button
              type="button"
              className="btn-secondary scanner-control-btn"
              onClick={handleSwitchCamera}
              title="Trocar de câmara"
            >
              <RotateCw size={18} />
              <span>Trocar câmara</span>
            </button>
          )}

          <button
            type="button"
            className="btn-secondary scanner-control-btn"
            onClick={() => fileInputRef.current?.click()}
            title="Ler a partir de uma foto ou ficheiro"
          >
            <Upload size={18} />
            <span>Foto</span>
          </button>
          <input
            ref={fileInputRef}
            type="file"
            accept="image/*"
            style={{ display: 'none' }}
            onChange={(e) => void handleFileChange(e)}
          />

          <button
            type="button"
            className={`btn-secondary scanner-control-btn ${showManual ? 'active' : ''}`}
            onClick={() => setShowManual((v) => !v)}
            title="Introduzir código com o teclado"
          >
            <Keyboard size={18} />
            <span>{showManual ? 'Ocultar teclado' : 'Digitar'}</span>
          </button>
        </div>

        {/* Entrada manual de código */}
        {showManual && (
          <form onSubmit={handleManualSubmit} className="scanner-manual-form">
            <label htmlFor="scanner-manual-input" className="scanner-manual-label">
              Código manual (QR ou código de barras):
            </label>
            <div className="scanner-manual-row">
              <input
                id="scanner-manual-input"
                type="text"
                value={manualCode}
                onChange={(e) => setManualCode(e.target.value)}
                placeholder="Ex: 5601234567890"
                className="scanner-manual-input"
                autoFocus
              />
              <button
                type="submit"
                className="btn-primary scanner-manual-submit"
                disabled={!manualCode.trim()}
              >
                Confirmar
              </button>
            </div>
          </form>
        )}

        <div className="scanner-modal-footer">
          <button type="button" className="btn-secondary btn-full-width" onClick={onClose}>
            Cancelar
          </button>
        </div>
      </div>
    </Modal>
  );
};
