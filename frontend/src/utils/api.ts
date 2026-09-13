/**
 * apiFetch — wrapper de fetch que adiciona automaticamente
 * X-User-Id e X-Role da sessão guardada no localStorage.
 * Substitui todos os `fetch('/api/...')` nas páginas.
 */
export const apiFetch = (url: string, options?: RequestInit): Promise<Response> => {
  const raw = localStorage.getItem('currentUser');
  const session = raw ? JSON.parse(raw) : {};
  return fetch(url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(options?.headers as Record<string, string> || {}),
      'X-User-Id': session.userId != null ? String(session.userId) : '',
      'X-Role':    session.role   ?? '',
    },
  });
};
