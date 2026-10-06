import Decimal from 'decimal.js';

export type DecimalValue = Decimal.Value;

export const decimal = (value: DecimalValue): Decimal => new Decimal(value);

export const formatMoney = (value: DecimalValue): string => `MT ${decimal(value).toFixed(2)}`;

export const formatQuantity = (value: DecimalValue, unit: string): string => {
  const formatted = decimal(value).toFixed(3).replace(/\.?0+$/, '');
  return unit ? `${formatted} ${unit}` : formatted;
};

export const parseDecimalInput = (value: string): Decimal | null => {
  if (!value.trim()) return null;
  try {
    return decimal(value);
  } catch {
    return null;
  }
};
