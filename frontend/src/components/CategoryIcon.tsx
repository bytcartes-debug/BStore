import { Tag } from 'lucide-react';
import { categoryIcons, categoryIconKey } from '../utils/categoryIcons';

export default function CategoryIcon({ value, size = 24 }: { value: string; size?: number }) {
  const Icon = categoryIcons.find((item) => item.key === categoryIconKey(value))?.icon || Tag;
  return <Icon size={size} aria-hidden="true" />;
}
