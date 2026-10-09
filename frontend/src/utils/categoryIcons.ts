export interface CategoryIconItem {
  key: string;
  label: string;
  emoji: string;
}

export const categoryIcons: CategoryIconItem[] = [
  { key: 'tag', label: 'Geral', emoji: '🏷️' },
  { key: 'bag', label: 'Compras', emoji: '🛍️' },
  { key: 'fruit', label: 'Frutas', emoji: '🍎' },
  { key: 'drink', label: 'Bebidas', emoji: '🥤' },
  { key: 'care', label: 'Higiene', emoji: '🧴' },
  { key: 'box', label: 'Embalagens', emoji: '📦' },
  { key: 'bread', label: 'Padaria / Pão', emoji: '🍞' },
  { key: 'meat', label: 'Carnes', emoji: '🥩' },
  { key: 'milk', label: 'Laticínios', emoji: '🧀' },
  { key: 'vegetable', label: 'Legumes', emoji: '🥦' },
  { key: 'coffee', label: 'Café', emoji: '☕' },
  { key: 'cleaning', label: 'Limpeza', emoji: '🧹' },
  { key: 'health', label: 'Saúde', emoji: '💊' },
  { key: 'clothes', label: 'Vestuário', emoji: '👕' },
  { key: 'shoes', label: 'Calçado', emoji: '👟' },
  { key: 'phone', label: 'Eletrónica', emoji: '📱' },
  { key: 'tools', label: 'Ferramentas', emoji: '🔧' },
  { key: 'light', label: 'Iluminação', emoji: '💡' },
  { key: 'egg', label: 'Ovos', emoji: '🥚' },
  { key: 'sweets', label: 'Doces & Snacks', emoji: '🍬' },
  { key: 'cereal', label: 'Cereais', emoji: '🌾' },
  { key: 'oil', label: 'Óleos & Condimentos', emoji: '🫒' },
];

const emojiMap: Record<string, string> = {
  tag: '🏷️',
  bag: '🛍️',
  fruit: '🍎',
  drink: '🥤',
  care: '🧴',
  box: '📦',
  bread: '🍞',
  meat: '🥩',
  milk: '🧀',
  vegetable: '🥦',
  coffee: '☕',
  cleaning: '🧹',
  health: '💊',
  clothes: '👕',
  shoes: '👟',
  phone: '📱',
  tools: '🔧',
  light: '💡',
  egg: '🥚',
  sweets: '🍬',
  cereal: '🌾',
  oil: '🫒',
  // Caso o utilizador já tenha guardado o emoji diretamente
  '🛍️': '🛍️',
  '🏷️': '🏷️',
  '🍎': '🍎',
  '🥤': '🥤',
  '🧴': '🧴',
  '📦': '📦',
  '🍞': '🍞',
  '🥩': '🥩',
  '🧀': '🧀',
  '🥦': '🥦',
  '🍺': '🥤',
  '☕': '☕',
  '🧹': '🧹',
  '🪣': '🧹',
  '💊': '💊',
  '👕': '👕',
  '👟': '👟',
  '📱': '📱',
  '🔧': '🔧',
  '💡': '💡',
  '🐔': '🥩',
  '🥚': '🥚',
  '🌽': '🥦',
  '🍫': '🍬',
  '🍬': '🍬',
};

const keyByEmoji: Record<string, string> = {};
categoryIcons.forEach(({ key, emoji }) => {
  keyByEmoji[emoji] = key;
  keyByEmoji[emoji.replace(/\ufe0f/g, '').trim()] = key;
});

export function categoryIconKey(value: string): string {
  if (!value) return 'tag';
  if (categoryIcons.some((c) => c.key === value)) return value;
  const clean = value.replace(/\ufe0f/g, '').trim();
  if (keyByEmoji[value]) return keyByEmoji[value];
  if (keyByEmoji[clean]) return keyByEmoji[clean];
  return clean || 'tag';
}

export function getCategoryEmoji(value: string): string {
  if (!value) return '🏷️';
  if (emojiMap[value]) return emojiMap[value];
  const clean = value.replace(/\ufe0f/g, '').trim();
  if (emojiMap[clean]) return emojiMap[clean];
  // Se for qualquer outro emoji de 1 ou 2 caracteres, usa-o diretamente
  if (value.length <= 4) return value;
  return '🏷️';
}
