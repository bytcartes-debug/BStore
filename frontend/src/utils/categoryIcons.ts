import type { LucideIcon } from 'lucide-react';
import {
  Tag,
  ShoppingBag,
  Apple,
  CupSoda,
  Sparkles,
  Package,
  UtensilsCrossed,
  Beef,
  Milk,
  Carrot,
  Coffee,
  SprayCan,
  Pill,
  Shirt,
  Footprints,
  Smartphone,
  Wrench,
  Lightbulb,
  Egg,
  Candy,
  Wheat,
  Droplet,
} from 'lucide-react';

export interface CategoryIconItem {
  key: string;
  label: string;
  icon: LucideIcon;
}

export const categoryIcons: CategoryIconItem[] = [
  { key: 'tag', label: 'Geral', icon: Tag },
  { key: 'bag', label: 'Compras', icon: ShoppingBag },
  { key: 'fruit', label: 'Frutas', icon: Apple },
  { key: 'drink', label: 'Bebidas', icon: CupSoda },
  { key: 'care', label: 'Higiene', icon: Sparkles },
  { key: 'box', label: 'Embalagens', icon: Package },
  { key: 'bread', label: 'Padaria / Pão', icon: UtensilsCrossed },
  { key: 'meat', label: 'Carnes', icon: Beef },
  { key: 'milk', label: 'Laticínios', icon: Milk },
  { key: 'vegetable', label: 'Legumes', icon: Carrot },
  { key: 'coffee', label: 'Café', icon: Coffee },
  { key: 'cleaning', label: 'Limpeza', icon: SprayCan },
  { key: 'health', label: 'Saúde', icon: Pill },
  { key: 'clothes', label: 'Vestuário', icon: Shirt },
  { key: 'shoes', label: 'Calçado', icon: Footprints },
  { key: 'phone', label: 'Eletrónica', icon: Smartphone },
  { key: 'tools', label: 'Ferramentas', icon: Wrench },
  { key: 'light', label: 'Iluminação', icon: Lightbulb },
  { key: 'egg', label: 'Ovos', icon: Egg },
  { key: 'sweets', label: 'Doces & Snacks', icon: Candy },
  { key: 'cereal', label: 'Cereais', icon: Wheat },
  { key: 'oil', label: 'Óleos & Condimentos', icon: Droplet },
];

const iconMap: Record<string, LucideIcon> = {
  tag: Tag,
  bag: ShoppingBag,
  fruit: Apple,
  drink: CupSoda,
  care: Sparkles,
  box: Package,
  bread: UtensilsCrossed,
  meat: Beef,
  milk: Milk,
  vegetable: Carrot,
  coffee: Coffee,
  cleaning: SprayCan,
  health: Pill,
  clothes: Shirt,
  shoes: Footprints,
  phone: Smartphone,
  tools: Wrench,
  light: Lightbulb,
  egg: Egg,
  sweets: Candy,
  cereal: Wheat,
  oil: Droplet,
  // Compatibilidade com valores salvos anteriormente como emojis
  '🛍️': ShoppingBag,
  '🏷️': Tag,
  '🍎': Apple,
  '🥤': CupSoda,
  '🧴': Sparkles,
  '📦': Package,
  '🍞': UtensilsCrossed,
  '🥩': Beef,
  '🧀': Milk,
  '🥦': Carrot,
  '🍺': CupSoda,
  '☕': Coffee,
  '🧹': SprayCan,
  '🪣': SprayCan,
  '💊': Pill,
  '👕': Shirt,
  '👟': Footprints,
  '📱': Smartphone,
  '🔧': Wrench,
  '💡': Lightbulb,
  '🐔': Beef,
  '🥚': Egg,
  '🌽': Carrot,
  '🍫': Candy,
  '🍬': Candy,
  '🌾': Wheat,
  '🫒': Droplet,
};

const emojiToKeyMap: Record<string, string> = {
  '🛍️': 'bag',
  '🏷️': 'tag',
  '🍎': 'fruit',
  '🥤': 'drink',
  '🧴': 'care',
  '📦': 'box',
  '🍞': 'bread',
  '🥩': 'meat',
  '🧀': 'milk',
  '🥦': 'vegetable',
  '☕': 'coffee',
  '🧹': 'cleaning',
  '🪣': 'cleaning',
  '💊': 'health',
  '👕': 'clothes',
  '👟': 'shoes',
  '📱': 'phone',
  '🔧': 'tools',
  '💡': 'light',
  '🥚': 'egg',
  '🍬': 'sweets',
  '🍫': 'sweets',
  '🌾': 'cereal',
  '🫒': 'oil',
};

export function categoryIconKey(value: string): string {
  if (!value) return 'tag';
  if (categoryIcons.some((c) => c.key === value)) return value;
  const clean = value.replace(/\ufe0f/g, '').trim();
  if (emojiToKeyMap[value]) return emojiToKeyMap[value];
  if (emojiToKeyMap[clean]) return emojiToKeyMap[clean];
  return clean || 'tag';
}

export function getCategoryIconComponent(value: string): LucideIcon {
  if (!value) return Tag;
  if (iconMap[value]) return iconMap[value];
  const clean = value.replace(/\ufe0f/g, '').trim();
  if (iconMap[clean]) return iconMap[clean];
  return Tag;
}
