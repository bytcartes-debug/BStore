import React, { useEffect, useRef, useState, useCallback } from 'react';
import {
  Flashlight,
  FlashlightOff,
  RotateCw,
  Upload,
  Keyboard,
  AlertCircle,
  X,
  Zap,
} from 'lucide-react';
import { Modal, Spinner } from './UI';
import {
  obterDetectorWeb,
  tocarBeepSucesso,
  type IBarcodeDetector,
} from '../utils/scanner';
import { BarcodeConsensoTracker, validarChecksumCodigo } from '../utils/barcodeChecksum';

interface CameraScannerModalProps {
  isOpen: boolean;
  onScan: (codigo: string) => void;
  onClose: () => void;
  title?: string;
  subtitle?: string;
  continuous?: boolean;
}

export const CameraScannerModal: React.FC<CameraScannerModalProps> = ({
  isOpen,
  onScan,
  onClose,
  title = 'Ler código com câmara',
  subtitle = 'Aponte a câmara para o código de barras ou QR code.',
  continuous = false,
}) => {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const detectorRef = useRef<IBarcodeDetector | null>(null);
  const consensusTrackerRef = useRef<BarcodeConsensoTracker>(new BarcodeConsensoTracker());
  const animationFrameIdRef = useRef<number | null>(null);
  const videoCallbackIdRef = useRef<number | null>(null);
  const isDecodingRef = useRef(false);
  const isStoppingRef = useRef(false);
  const lastActiveTimeRef = useRef(Date.now());
  const onScanRef = useRef(onScan);
  onScanRef.current = onScan;
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [isReady, setIsReady] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [errorHelp, setErrorHelp] = useState<string | null>(null);
  const [flashSuccess, setFlashSuccess] = useState(false);
  const [hasTorch, setHasTorch] = useState(false);
  const [torchOn, setTorchOn] = useState(false);
  const [cameras, setCameras] = useState<Array<{ id: string; label: string }>>([]);
  const [cameraIndex, setCameraIndex] = useState(0);
  const [manualCode, setManualCode] = useState('');
  const [showManual, setShowManual] = useState(false);

  // Parar todas as faixas da câmara e libertar o hardware
  const pararCamera = useCallback(() => {
    isStoppingRef.current = true;
    if (animationFrameIdRef.current) {
      cancelAnimationFrame(animationFrameIdRef.current);
      animationFrameIdRef.current = null;
    }
    if (videoCallbackIdRef.current && videoRef.current && 'cancelVideoFrameCallback' in videoRef.current) {
      (videoRef.current as any).cancelVideoFrameCallback(videoCallbackIdRef.current);
      videoCallbackIdRef.current = null;
    }
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((track) => {
        try {
          track.stop();
        } catch {}
      });
      streamRef.current = null;
    }
    if (videoRef.current) {
      videoRef.current.srcObject = null;
    }
    setIsReady(false);
    setTorchOn(false);
    setHasTorch(false);
  }, []);

  const handleFinishScan = useCallback(
    (code: string) => {
      const clean = code.trim();
      if (!clean) return;

      lastActiveTimeRef.current = Date.now();
      tocarBeepSucesso();
      setFlashSuccess(true);
      setTimeout(() => setFlashSuccess(false), 300);

      onScanRef.current(clean);

      if (!continuous) {
        pararCamera();
        onClose();
      }
    },
    [continuous, onClose, pararCamera],
  );

  // Loop de leitura optimizado
  const iniciarLoopLeitura = useCallback(() => {
    let frameCount = 0;
    let avgDecodeTime = 0;
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d', { willReadFrequently: true });

    const processFrame = async () => {
      if (isStoppingRef.current || !videoRef.current || !detectorRef.current) return;

      const video = videoRef.current;
      if (video.readyState < 2 || video.videoWidth === 0 || video.videoHeight === 0) {
        agendarProximo();
        return;
      }

      // Auto-fecho após 60s sem leituras para poupar bateria e câmara
      if (Date.now() - lastActiveTimeRef.current > 60000) {
        pararCamera();
        onClose();
        return;
      }

      frameCount++;
      // Auto-ajuste de taxa de amostragem: se tempo de decodificação > 25ms, pula frames
      const skipFactor = avgDecodeTime > 30 ? 3 : avgDecodeTime > 15 ? 2 : 1;
      if (frameCount % skipFactor !== 0) {
        agendarProximo();
        return;
      }

      if (isDecodingRef.current) {
        agendarProximo();
        return;
      }

      isDecodingRef.current = true;
      const t0 = performance.now();

      try {
        // Recorte da região central (70% da largura) para leitura acelerada
        const vw = video.videoWidth;
        const vh = video.videoHeight;
        const cropW = Math.floor(vw * 0.7);
        const cropH = Math.floor(vh * 0.7);
        const cropX = Math.floor((vw - cropW) / 2);
        const cropY = Math.floor((vh - cropH) / 2);

        canvas.width = cropW;
        canvas.height = cropH;
        if (ctx) {
          ctx.drawImage(video, cropX, cropY, cropW, cropH, 0, 0, cropW, cropH);
        }

        const barcodes = await detectorRef.current.detect(canvas);
        const duracao = performance.now() - t0;
        avgDecodeTime = avgDecodeTime === 0 ? duracao : avgDecodeTime * 0.8 + duracao * 0.2;

        if (barcodes && barcodes.length > 0) {
          for (const item of barcodes) {
            const raw = item.rawValue ? item.rawValue.trim() : '';
            if (!raw) continue;

            const aprovado = consensusTrackerRef.current.processar(raw, false, item.format);
            if (aprovado) {
              if (import.meta.env?.DEV) {
                console.debug(`[Scanner] Código lido em ${duracao.toFixed(1)}ms: ${aprovado}`);
              }
              handleFinishScan(aprovado);
              break;
            }
          }
        }
      } catch (err) {
        if (import.meta.env?.DEV) {
          console.debug('[Scanner] Erro transitório no frame:', err);
        }
      } finally {
        isDecodingRef.current = false;
        agendarProximo();
      }
    };

    const agendarProximo = () => {
      if (isStoppingRef.current) return;
      if (videoRef.current && 'requestVideoFrameCallback' in videoRef.current) {
        videoCallbackIdRef.current = (videoRef.current as any).requestVideoFrameCallback(
          processFrame,
        );
      } else {
        animationFrameIdRef.current = requestAnimationFrame(processFrame);
      }
    };

    agendarProximo();
  }, [handleFinishScan, onClose, pararCamera]);

  // Iniciar stream de câmara
  const iniciarCamera = useCallback(
    async (deviceId?: string) => {
      isStoppingRef.current = false;
      setErrorMsg(null);
      setErrorHelp(null);
      setIsReady(false);
      lastActiveTimeRef.current = Date.now();
      consensusTrackerRef.current.reset();

      try {
        // Inicializar motor de deteção
        if (!detectorRef.current) {
          detectorRef.current = await obterDetectorWeb();
        }

        const constraints: MediaStreamConstraints = {
          audio: false,
          video: deviceId
            ? {
                deviceId: { exact: deviceId },
                width: { ideal: 1280 },
                height: { ideal: 720 },
                frameRate: { ideal: 30 },
              }
            : {
                facingMode: { ideal: 'environment' },
                width: { ideal: 1280 },
                height: { ideal: 720 },
                frameRate: { ideal: 30 },
              },
        };

        const stream = await navigator.mediaDevices.getUserMedia(constraints);
        streamRef.current = stream;

        // Verificar suporte a lanterna e foco contínuo
        const track = stream.getVideoTracks()[0];
        if (track) {
          const capabilities = (track.getCapabilities ? track.getCapabilities() : {}) as any;
          setHasTorch(Boolean(capabilities.torch));

          // Ativar focagem contínua se suportado
          if (capabilities.focusMode && Array.isArray(capabilities.focusMode)) {
            if (capabilities.focusMode.includes('continuous')) {
              try {
                await track.applyConstraints({
                  advanced: [{ focusMode: 'continuous' } as any],
                });
              } catch {}
            }
          }
        }

        if (videoRef.current) {
          videoRef.current.srcObject = stream;
          await videoRef.current.play();
          setIsReady(true);
          iniciarLoopLeitura();
        }

        // Listar câmaras disponíveis
        try {
          const devices = await navigator.mediaDevices.enumerateDevices();
          const videoDevs = devices
            .filter((d) => d.kind === 'videoinput')
            .map((d, i) => ({
              id: d.deviceId,
              label: d.label || `Câmara ${i + 1}`,
            }));
          setCameras(videoDevs);
        } catch {}
      } catch (err: unknown) {
        const error = err as Error;
        const name = error.name || '';
        let msg = 'Não foi possível ligar a câmara.';
        let help = 'Verifique se permitiu o acesso à câmara nas definições do navegador.';

        if (name === 'NotAllowedError' || name === 'PermissionDeniedError') {
          msg = 'Permissão de câmara recusada.';
          help =
            'No Chrome: clique no ícone de cadeado junto ao URL e ative a Câmara. No Safari: abra Definições > Safari > Câmara > Permitir.';
        } else if (name === 'NotFoundError' || name === 'DevicesNotFoundError') {
          msg = 'Nenhuma câmara detetada neste dispositivo.';
          help = 'Pode digitar o código manualmente ou carregar uma fotografia com o código.';
        } else if (name === 'NotReadableError' || name === 'TrackStartError') {
          msg = 'A câmara está a ser usada por outra aplicação.';
          help = 'Feche outras aplicações ou separadores que estejam a usar a câmara e tente de novo.';
        }

        setErrorMsg(msg);
        setErrorHelp(help);
      }
    },
    [iniciarLoopLeitura],
  );

  // Alternar lanterna
  const toggleTorch = useCallback(async () => {
    if (!streamRef.current) return;
    const track = streamRef.current.getVideoTracks()[0];
    if (track) {
      try {
        const nextState = !torchOn;
        await track.applyConstraints({
          advanced: [{ torch: nextState } as any],
        });
        setTorchOn(nextState);
      } catch {}
    }
  }, [torchOn]);

  // Trocar de câmara
  const alternarCamera = useCallback(() => {
    if (cameras.length < 2) return;
    pararCamera();
    const nextIdx = (cameraIndex + 1) % cameras.length;
    setCameraIndex(nextIdx);
    void iniciarCamera(cameras[nextIdx].id);
  }, [cameraIndex, cameras, iniciarCamera, pararCamera]);

  // Toque no vídeo para focar onde o utilizador tocou
  const handleVideoTouch = useCallback((e: React.MouseEvent<HTMLVideoElement>) => {
    if (!streamRef.current || !videoRef.current) return;
    const track = streamRef.current.getVideoTracks()[0];
    if (!track) return;
    const caps = (track.getCapabilities ? track.getCapabilities() : {}) as any;
    if (caps.pointsOfInterest) {
      const rect = videoRef.current.getBoundingClientRect();
      const x = (e.clientX - rect.left) / rect.width;
      const y = (e.clientY - rect.top) / rect.height;
      try {
        track.applyConstraints({
          advanced: [{ pointsOfInterest: [{ x, y }] } as any],
        });
      } catch {}
    }
  }, []);

  // Leitura a partir de fotografia enviada (scanFile)
  const handleUploadFoto = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    try {
      if (!detectorRef.current) {
        detectorRef.current = await obterDetectorWeb();
      }
      const img = new Image();
      const url = URL.createObjectURL(file);
      img.src = url;
      await new Promise((resolve) => (img.onload = resolve));

      const barcodes = await detectorRef.current.detect(img);
      URL.revokeObjectURL(url);

      if (barcodes && barcodes.length > 0) {
        for (const b of barcodes) {
          if (b.rawValue && validarChecksumCodigo(b.rawValue)) {
            handleFinishScan(b.rawValue.trim());
            return;
          }
        }
      }
      alert('Nenhum código de barras legível foi encontrado nesta imagem.');
    } catch {
      alert('Não foi possível ler a imagem selecionada.');
    }
  };

  const handleManualSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const clean = manualCode.trim();
    if (!clean) return;
    if (!validarChecksumCodigo(clean)) {
      alert('Código com formato ou dígito de controlo inválido.');
      return;
    }
    handleFinishScan(clean);
  };

  // Ciclo de vida: Inicia quando abre, limpa rigorosamente ao fechar ou ocultar separador
  useEffect(() => {
    if (isOpen) {
      void iniciarCamera();
    } else {
      pararCamera();
    }

    const handleVisibilityChange = () => {
      if (document.hidden) {
        pararCamera();
      } else if (isOpen) {
        void iniciarCamera();
      }
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);
    return () => {
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      pararCamera();
    };
  }, [isOpen, iniciarCamera, pararCamera]);

  if (!isOpen) return null;

  return (
    <Modal
      title={title}
      onClose={() => {
        pararCamera();
        onClose();
      }}
      wide
    >
      <div className="camera-scanner-modal-body" style={{ position: 'relative' }}>
        {subtitle && (
          <p style={{ margin: '0 0 12px 0', fontSize: 13, color: 'var(--text-secondary)' }}>
            {subtitle}
          </p>
        )}

        {/* Flash visual de confirmação (relâmpago verde) */}
        {flashSuccess && (
          <div
            style={{
              position: 'absolute',
              inset: 0,
              background: 'rgba(34, 197, 94, 0.45)',
              zIndex: 30,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              pointerEvents: 'none',
              borderRadius: 12,
              animation: 'fadeOut 0.3s ease-out',
            }}
          >
            <Zap size={64} color="#ffffff" style={{ filter: 'drop-shadow(0 2px 8px rgba(0,0,0,0.4))' }} />
          </div>
        )}

        {/* Viewport do vídeo com mira central */}
        <div
          style={{
            position: 'relative',
            width: '100%',
            aspectRatio: '4/3',
            maxHeight: '52vh',
            background: '#000',
            borderRadius: 12,
            overflow: 'hidden',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <video
            ref={videoRef}
            autoPlay
            playsInline
            muted
            onClick={handleVideoTouch}
            style={{
              width: '100%',
              height: '100%',
              objectFit: 'cover',
              display: isReady ? 'block' : 'none',
            }}
          />

          {!isReady && !errorMsg && (
            <div style={{ textAlign: 'center', color: '#fff', padding: 20 }}>
              <Spinner size="normal" />
              <p style={{ marginTop: 12, fontSize: 13, opacity: 0.85 }}>A iniciar câmara em tempo real…</p>
            </div>
          )}

          {errorMsg && (
            <div style={{ padding: 24, textAlign: 'center', color: '#fff' }}>
              <AlertCircle size={40} color="#ef4444" style={{ margin: '0 auto 12px' }} />
              <p style={{ fontWeight: 600, fontSize: 15, marginBottom: 6 }}>{errorMsg}</p>
              {errorHelp && <p style={{ fontSize: 13, color: '#d1d5db', marginBottom: 16 }}>{errorHelp}</p>}
              <div style={{ display: 'flex', gap: 8, justifyContent: 'center', flexWrap: 'wrap' }}>
                <button
                  type="button"
                  className="btn-primary"
                  onClick={() => void iniciarCamera()}
                  style={{ fontSize: 13 }}
                >
                  Tentar novamente
                </button>
                <button
                  type="button"
                  className="btn-secondary"
                  onClick={() => setShowManual(true)}
                  style={{ fontSize: 13 }}
                >
                  Entrada manual
                </button>
              </div>
            </div>
          )}

          {/* Mira de enquadramento central (70% de recorte) */}
          {isReady && (
            <div
              style={{
                position: 'absolute',
                width: '74%',
                height: '56%',
                border: '2px solid rgba(255, 255, 255, 0.85)',
                borderRadius: 14,
                boxShadow: '0 0 0 9999px rgba(0, 0, 0, 0.52)',
                pointerEvents: 'none',
                overflow: 'hidden',
              }}
            >
              <div className="laser-scanner-beam" aria-hidden="true" />
            </div>
          )}
        </div>

        {/* Barra de controlos da câmara */}
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            marginTop: 12,
            gap: 8,
            flexWrap: 'wrap',
          }}
        >
          <div style={{ display: 'flex', gap: 8 }}>
            {hasTorch && (
              <button
                type="button"
                className="btn-secondary"
                onClick={() => void toggleTorch()}
                title="Ligar/desligar lanterna"
                style={{ display: 'inline-flex', alignItems: 'center', gap: 6, padding: '6px 12px', fontSize: 13 }}
              >
                {torchOn ? <FlashlightOff size={16} /> : <Flashlight size={16} />}
                <span>{torchOn ? 'Apagar' : 'Lanterna'}</span>
              </button>
            )}

            {cameras.length > 1 && (
              <button
                type="button"
                className="btn-secondary"
                onClick={alternarCamera}
                title="Mudar para outra câmara"
                style={{ display: 'inline-flex', alignItems: 'center', gap: 6, padding: '6px 12px', fontSize: 13 }}
              >
                <RotateCw size={16} />
                <span>Trocar</span>
              </button>
            )}

            <button
              type="button"
              className="btn-secondary"
              onClick={() => fileInputRef.current?.click()}
              title="Carregar fotografia com código"
              style={{ display: 'inline-flex', alignItems: 'center', gap: 6, padding: '6px 12px', fontSize: 13 }}
            >
              <Upload size={16} />
              <span>Foto</span>
            </button>
            <input
              ref={fileInputRef}
              type="file"
              accept="image/*"
              style={{ display: 'none' }}
              onChange={(e) => void handleUploadFoto(e)}
            />
          </div>

          <button
            type="button"
            className="btn-secondary"
            onClick={() => setShowManual((prev) => !prev)}
            style={{ display: 'inline-flex', alignItems: 'center', gap: 6, padding: '6px 12px', fontSize: 13 }}
          >
            <Keyboard size={16} />
            <span>Digitar</span>
          </button>
        </div>

        {/* Entrada manual alternativa */}
        {showManual && (
          <form
            onSubmit={handleManualSubmit}
            style={{
              marginTop: 14,
              padding: 12,
              background: 'var(--bg-app)',
              borderRadius: 8,
              border: '1px solid var(--border-color)',
              display: 'flex',
              gap: 8,
            }}
          >
            <input
              type="text"
              placeholder="Digite o código (ex: 5601234567890)"
              value={manualCode}
              onChange={(e) => setManualCode(e.target.value)}
              autoFocus
              style={{ flex: 1, padding: '8px 12px', borderRadius: 6, border: '1px solid var(--input-border)' }}
            />
            <button type="submit" className="btn-primary" style={{ padding: '8px 16px', fontSize: 13 }}>
              Confirmar
            </button>
          </form>
        )}

        {/* Ações inferiores */}
        <div style={{ marginTop: 16, display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          <button
            type="button"
            className="btn-secondary"
            onClick={() => {
              pararCamera();
              onClose();
            }}
          >
            <X size={15} style={{ marginRight: 4, verticalAlign: -2 }} />
            Parar / Fechar
          </button>
        </div>
      </div>
    </Modal>
  );
};
