import { useEffect, useState } from 'react';
import type { LucideIcon } from 'lucide-react';
import { Shield, Cpu } from 'lucide-react';
import './AITypewriter.css';

export interface AIPhrase {
  id: string;
  category: string;
  command: string;
  text: string;
  metricLabel: string;
  metricIcon: LucideIcon;
  color: string;
}

interface AITypewriterProps {
  phrases: AIPhrase[];
}

export function AITypewriter({ phrases }: AITypewriterProps) {
  const [phraseIndex, setPhraseIndex] = useState(0);
  const [displayedText, setDisplayedText] = useState('');
  const [isDeleting, setIsDeleting] = useState(false);
  const [isPaused, setIsPaused] = useState(false);

  const currentPhrase = phrases[phraseIndex];
  const fullText = currentPhrase.text;
  const MetricIcon = currentPhrase.metricIcon;

  useEffect(() => {
    let timeout: ReturnType<typeof setTimeout>;

    if (isPaused) {
      // Pause at end of text before transitioning to next
      timeout = setTimeout(() => {
        setIsPaused(false);
        setIsDeleting(true);
      }, 3600);
      return () => clearTimeout(timeout);
    }

    if (isDeleting) {
      if (displayedText.length === 0) {
        setIsDeleting(false);
        setPhraseIndex((prev) => (prev + 1) % phrases.length);
      } else {
        timeout = setTimeout(() => {
          setDisplayedText((prev) => prev.slice(0, -2));
        }, 14);
      }
    } else {
      // Typing forward (letter by letter like an AI streaming tokens)
      if (displayedText.length < fullText.length) {
        const nextChar = fullText[displayedText.length];
        // Natural cadence variation
        const delay = nextChar === ' ' ? 22 : nextChar === '.' || nextChar === ':' ? 90 : 26;
        timeout = setTimeout(() => {
          setDisplayedText(fullText.slice(0, displayedText.length + 1));
        }, delay);
      } else {
        setIsPaused(true);
      }
    }

    return () => clearTimeout(timeout);
  }, [displayedText, isDeleting, isPaused, fullText, phrases.length]);

  return (
    <div className="ai-stream-terminal" aria-live="polite">
      {/* Terminal Top Bar */}
      <div className="ai-terminal-header">
        <div className="ai-terminal-status">
          <span className="ai-pulse-dot" aria-hidden="true" />
          <span className="ai-terminal-badge">STREAMING IA</span>
          <span className="ai-terminal-divider">/</span>
          <span className="ai-terminal-tag">{currentPhrase.category}</span>
        </div>
        <div className="ai-terminal-meta">
          <Cpu size={13} className="ai-meta-icon" aria-hidden="true" />
          <span>AUTÔNOMO</span>
        </div>
      </div>

      {/* Terminal Command Line */}
      <div className="ai-terminal-prompt">
        <span className="ai-prompt-arrow">$</span>
        <span className="ai-prompt-cmd">{currentPhrase.command}</span>
      </div>

      {/* Streamed Body (Letter by letter) */}
      <div className="ai-terminal-body">
        <p className="ai-typed-text">
          {displayedText}
          <span className="ai-cursor" aria-hidden="true">
            ▋
          </span>
        </p>
      </div>

      {/* Metric Insight Banner (No emojis, strictly clean symbols) */}
      <div
        className="ai-terminal-footer"
        style={{ '--accent-color': currentPhrase.color } as React.CSSProperties}
      >
        <div className="ai-metric-pill">
          <MetricIcon size={15} className="ai-metric-icon" aria-hidden="true" />
          <span className="ai-metric-label">{currentPhrase.metricLabel}</span>
        </div>
        <div className="ai-live-indicator">
          <Shield size={13} aria-hidden="true" />
          <span>BStore Verificado</span>
        </div>
      </div>
    </div>
  );
}

export default AITypewriter;
