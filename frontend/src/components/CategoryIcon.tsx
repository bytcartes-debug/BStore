import { getCategoryEmoji } from '../utils/categoryIcons';

export default function CategoryIcon({ value, size = 20 }: { value: string; size?: number }) {
  const emoji = getCategoryEmoji(value);
  return (
    <span
      className="category-emoji"
      style={{ fontSize: `${size}px`, lineHeight: 1, display: 'inline-block' }}
      aria-hidden="true"
    >
      {emoji}
    </span>
  );
}
