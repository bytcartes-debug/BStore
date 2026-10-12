import { useCallback, useEffect, useImperativeHandle, useRef } from 'react';

/**
 * 21st.dev Component #31530 — Motion Confetti Burst
 * Canvas-based particle explosion fired when an action succeeds (venda concluída, fecho de caixa).
 * Pure HTML5 Canvas, dependency-free, GPU accelerated, 60fps.
 * Respects prefers-reduced-motion.
 */

const GRAVITY = 0.24;
const DRAG = 0.992;
const TERMINAL_VELOCITY = 7;
const DECAY = 0.013;

const DEFAULT_COLORS = [
  '#10b981', // Emerald / Brand green
  '#34d399', // Mint
  '#3b82f6', // Sky blue
  '#f59e0b', // Amber / Gold
  '#ec4899', // Rose
  '#8b5cf6', // Violet
];

export interface ConfettiHandle {
  fire: () => void;
}

export interface ConfettiProps {
  fire?: boolean;
  count?: number;
  origin?: { x: number; y: number };
  spread?: number;
  colors?: string[];
  ref?: React.Ref<ConfettiHandle>;
  className?: string;
}

type Particle = {
  x: number;
  y: number;
  vx: number;
  vy: number;
  size: number;
  tilt: number;
  spin: number;
  life: number;
  color: string;
};

export function Confetti({
  fire = false,
  count = 80,
  origin = { x: 0.5, y: 0.45 },
  spread = 12,
  colors = DEFAULT_COLORS,
  className,
  ref,
}: ConfettiProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const particlesRef = useRef<Particle[]>([]);
  const frameRef = useRef<number | null>(null);

  const draw = useCallback(() => {
    const canvas = canvasRef.current;
    const context = canvas?.getContext('2d');

    if (!canvas || !context) {
      return;
    }

    const ratio = window.devicePixelRatio || 1;
    context.clearRect(0, 0, canvas.width, canvas.height);

    const alive: Particle[] = [];

    for (const particle of particlesRef.current) {
      particle.vy = Math.min(particle.vy + GRAVITY, TERMINAL_VELOCITY);
      particle.vx *= DRAG;
      particle.x += particle.vx;
      particle.y += particle.vy;
      particle.tilt += particle.spin;
      particle.life -= DECAY;

      if (particle.life <= 0) {
        continue;
      }

      alive.push(particle);

      context.save();
      context.translate(particle.x * ratio, particle.y * ratio);
      context.rotate(particle.tilt);
      context.globalAlpha = Math.max(0, Math.min(1, particle.life));
      context.fillStyle = particle.color;
      context.fillRect(
        (-particle.size / 2) * ratio,
        (-particle.size / 4) * ratio,
        particle.size * ratio,
        ((particle.size / 2) * Math.abs(Math.cos(particle.tilt)) + 1) * ratio,
      );
      context.restore();
    }

    particlesRef.current = alive;

    if (alive.length > 0) {
      frameRef.current = window.requestAnimationFrame(draw);
      return;
    }

    frameRef.current = null;
    context.clearRect(0, 0, canvas.width, canvas.height);
  }, []);

  const start = useCallback(() => {
    const canvas = canvasRef.current;

    if (!canvas || window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      return;
    }

    const ratio = window.devicePixelRatio || 1;
    const { width, height } = canvas.getBoundingClientRect();

    canvas.width = Math.round(width * ratio);
    canvas.height = Math.round(height * ratio);

    for (let index = 0; index < count; index += 1) {
      const angle = Math.PI * (1.15 + Math.random() * 0.7);
      const velocity = spread * (0.45 + Math.random() * 0.55);

      particlesRef.current.push({
        x: origin.x * width,
        y: origin.y * height,
        vx: Math.cos(angle) * velocity,
        vy: Math.sin(angle) * velocity,
        size: 5 + Math.random() * 6,
        tilt: Math.random() * Math.PI,
        spin: (Math.random() - 0.5) * 0.35,
        life: 1,
        color: colors[index % colors.length],
      });
    }

    if (frameRef.current === null) {
      frameRef.current = window.requestAnimationFrame(draw);
    }
  }, [colors, count, draw, origin.x, origin.y, spread]);

  useImperativeHandle(ref, () => ({ fire: start }), [start]);

  useEffect(() => {
    if (fire) {
      start();
    }
  }, [fire, start]);

  useEffect(() => {
    return () => {
      if (frameRef.current !== null) {
        window.cancelAnimationFrame(frameRef.current);
      }
    };
  }, []);

  return (
    <canvas
      ref={canvasRef}
      aria-hidden="true"
      style={{
        position: 'fixed',
        inset: 0,
        width: '100%',
        height: '100%',
        maxWidth: '100vw',
        maxHeight: '100dvh',
        pointerEvents: 'none',
        zIndex: 9999,
      }}
      className={className}
    />
  );
}

export default Confetti;
