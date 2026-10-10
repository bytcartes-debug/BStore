export const apiFetch = async (url: string, options?: RequestInit): Promise<Response> => {
  const response = await fetch(url, {
    ...options,
    signal: options?.signal
      ? AbortSignal.any([options.signal, AbortSignal.timeout(20000)])
      : AbortSignal.timeout(20000),
    credentials: 'same-origin',
    headers: {
      'Content-Type': 'application/json',
      ...((options?.headers as Record<string, string>) || {}),
    },
  });
  if (response.status === 401) {
    window.dispatchEvent(new Event('bstore:unauthenticated'));
  }
  return response;
};

export class ApiError extends Error {
  status: number;
  code?: string;
  constructor(message: string, status: number, code?: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

export async function apiRequest<T = void>(url: string, options?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await apiFetch(url, options);
  } catch (error) {
    if (options?.signal?.aborted) throw error;
    throw new Error('Não foi possível ligar ao servidor. Verifique a ligação e tente novamente.');
  }
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    if (response.status >= 500) {
      throw new ApiError(
        body.erro || 'O servidor não conseguiu concluir o pedido. Tente novamente.',
        response.status,
        body.codigo,
      );
    }
    throw new ApiError(body.erro || 'Não foi possível concluir o pedido. Tente novamente.', response.status, body.codigo);
  }
  return response.status === 204 ? (undefined as T) : response.json();
}
