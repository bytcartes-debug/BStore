import Decimal from 'decimal.js';

export type DecimalValue = Decimal.Value;

export const decimalSeguro = (value: unknown, fallback: DecimalValue = 0): Decimal => {
  if (value === null || value === undefined || value === '') {
    return new Decimal(fallback);
  }
  try {
    const d = new Decimal(value as DecimalValue);
    if (d.isNaN()) return new Decimal(fallback);
    return d;
  } catch {
    return new Decimal(fallback);
  }
};

export const decimal = (value: DecimalValue): Decimal => decimalSeguro(value);

export const formatMoney = (value: unknown): string => `MT ${decimalSeguro(value).toFixed(2)}`;

export const formatQuantity = (value: unknown, unit: string = ''): string => {
  const formatted = decimalSeguro(value).toFixed(3).replace(/\.?0+$/, '');
  return unit ? `${formatted} ${unit}` : formatted;
};

export const parseDecimalInput = (value: string): Decimal | null => {
  if (!value || !value.trim()) return null;
  try {
    const d = new Decimal(value);
    return d.isNaN() ? null : d;
  } catch {
    return null;
  }
};
