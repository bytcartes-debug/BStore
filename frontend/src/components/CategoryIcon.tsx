import { getCategoryIconComponent } from '../utils/categoryIcons';

export default function CategoryIcon({
  value,
  size = 20,
  className = '',
}: {
  value: string;
  size?: number;
  className?: string;
}) {
  const IconComponent = getCategoryIconComponent(value);
  return (
    <span
      className={`category-emoji category-symbol ${className}`.trim()}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        lineHeight: 1,
      }}
      aria-hidden="true"
    >
      <IconComponent size={size} strokeWidth={2} />
    </span>
  );
}
