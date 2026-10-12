import { useEffect, useState } from 'react';

interface AnimatedNumberProps {
  value: number;
  duration?: number;
  formatter?: (val: number) => string;
}

export function AnimatedNumber({
  value,
  duration = 900,
  formatter = (v) => v.toLocaleString('pt-MZ'),
}: AnimatedNumberProps) {
  const [current, setCurrent] = useState(() => (value > 1000 ? Math.floor(value * 0.4) : 0));

  useEffect(() => {
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      setCurrent(value);
      return;
    }

    const start = performance.now();
    const initial = 0;
    const diff = value - initial;

    let frameId: number;

    const tick = (now: number) => {
      const elapsed = now - start;
      const progress = Math.min(elapsed / duration, 1);
      // Easing out cubic: 1 - pow(1 - progress, 3)
      const ease = 1 - Math.pow(1 - progress, 3);
      setCurrent(initial + diff * ease);

      if (progress < 1) {
        frameId = requestAnimationFrame(tick);
      }
    };

    frameId = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frameId);
  }, [value, duration]);

  return <span>{formatter(current)}</span>;
}

export default AnimatedNumber;
