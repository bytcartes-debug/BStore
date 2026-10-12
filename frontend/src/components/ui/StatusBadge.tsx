import type { LucideIcon } from 'lucide-react';
import './StatusBadge.css';

export type StatusVariant = 'online' | 'warning' | 'danger' | 'info' | 'neutral';

interface StatusBadgeProps {
  label: string;
  variant?: StatusVariant;
  icon?: LucideIcon;
  pulse?: boolean;
  className?: string;
}

export function StatusBadge({
  label,
  variant = 'online',
  icon: Icon,
  pulse = true,
  className = '',
}: StatusBadgeProps) {
  return (
    <span className={`status-badge status-badge-${variant} ${className}`.trim()}>
      {pulse && <span className="status-badge-dot" aria-hidden="true" />}
      {Icon && <Icon size={12} className="status-badge-icon" aria-hidden="true" />}
      <span className="status-badge-label">{label}</span>
    </span>
  );
}

export default StatusBadge;
