/**
 * Utilitário de formatação de data e hora para a loja BStore.
 * Faz parse manual de strings ISO/SQL ("AAAA-MM-DDTHH:mm:ss" ou "AAAA-MM-DD HH:mm:ss")
 * sem passar por `new Date()`, garantindo que a hora exibida é exatamente a enviada pelo servidor
 * (fuso de Maputo), sem distorções provocadas pelo fuso horário do navegador do utilizador.
 */

export function formatarDataHora(texto?: string | null): string {
  if (!texto) return '-';
  const limpo = texto.trim();

  // Expressão regular para AAAA-MM-DD[T ]HH:mm(:ss)?
  const match = limpo.match(/^(\d{4})-(\d{2})-(\d{2})[T\s](\d{2}):(\d{2})(?::(\d{2}))?/);
  if (match) {
    const [, ano, mes, dia, hora, minuto] = match;
    return `${dia}/${mes}/${ano} ${hora}:${minuto}`;
  }

  // Se for apenas data AAAA-MM-DD
  const matchData = limpo.match(/^(\d{4})-(\d{2})-(\d{2})/);
  if (matchData) {
    const [, ano, mes, dia] = matchData;
    return `${dia}/${mes}/${ano}`;
  }

  return limpo;
}

export function formatarData(texto?: string | null): string {
  if (!texto) return '-';
  const limpo = texto.trim();
  const match = limpo.match(/^(\d{4})-(\d{2})-(\d{2})/);
  if (match) {
    const [, ano, mes, dia] = match;
    return `${dia}/${mes}/${ano}`;
  }
  return limpo;
}

export function formatarHora(texto?: string | null): string {
  if (!texto) return '-';
  const limpo = texto.trim();
  const match = limpo.match(/[T\s](\d{2}):(\d{2})/);
  if (match) {
    const [, hora, minuto] = match;
    return `${hora}:${minuto}`;
  }
  return limpo;
}
