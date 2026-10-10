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
        'O servidor não conseguiu concluir o pedido. Tente novamente.',
        response.status,
        body.codigo,
      );
    }
    throw new ApiError(
      body.erro || 'Não foi possível concluir o pedido. Tente novamente.',
      response.status,
      body.codigo,
    );
  }
  return response.status === 204 ? (undefined as T) : response.json();
}

export async function descarregarFicheiro(url: string, nomePadrao: string): Promise<void> {
  const response = await apiFetch(url);
  if (!response.ok) {
    throw new ApiError('Falha ao descarregar ficheiro.', response.status);
  }
  const blob = await response.blob();
  const blobUrl = window.URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = blobUrl;
  const disposition = response.headers.get('Content-Disposition');
  let filename = nomePadrao;
  if (disposition) {
    const match = disposition.match(/filename="?([^";]+)"?/);
    if (match && match[1]) {
      filename = match[1];
    }
  }
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  window.URL.revokeObjectURL(blobUrl);
}

