import { test, expect } from '@playwright/test';
import { formatarDataHora, formatarData, formatarHora } from '../src/utils/dataHora';

test.describe('Helper de formatação de data e hora (Africa/Maputo)', () => {
  test('2026-10-11T23:30:00 mostra 23:30 e dia 11, independentemente do fuso do ambiente de teste', () => {
    const formatado = formatarDataHora('2026-10-11T23:30:00');
    expect(formatado).toBe('11/10/2026 23:30');
    expect(formatado).toContain('11');
    expect(formatado).toContain('23:30');
  });

  test('2026-10-12T00:30:00 mostra 00:30 e dia 12', () => {
    const formatado = formatarDataHora('2026-10-12T00:30:00');
    expect(formatado).toBe('12/10/2026 00:30');
    expect(formatado).toContain('12');
    expect(formatado).toContain('00:30');
  });

  test('extrai data e hora separadamente com consistência', () => {
    expect(formatarData('2026-10-11T23:30:00')).toBe('11/10/2026');
    expect(formatarHora('2026-10-11T23:30:00')).toBe('23:30');
  });
});
