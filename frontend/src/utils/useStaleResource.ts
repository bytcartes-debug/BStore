import { useCallback, useEffect, useRef, useState } from 'react';

// Memória RAM volátil para resposta a 0ms
const memoryCache = new Map<string, { data: unknown; timestamp: number }>();
const listeners = new Map<string, Set<() => void>>();

/** Regista ou desregista um observador de invalidação */
export function invalidateResource(cacheKey: string) {
  // Remove da memória RAM
  for (const k of memoryCache.keys()) {
    if (k.startsWith(cacheKey)) {
      memoryCache.delete(k);
    }
  }

  // Notifica todos os hooks ativos que usam esta chave
  const keyListeners = listeners.get(cacheKey);
  if (keyListeners) {
    keyListeners.forEach((callback) => {
      try {
        callback();
      } catch {}
    });
  }
}

/** Limpa todas as caches de um utilizador no logout */
export function clearUserCache(usuarioId?: string) {
  memoryCache.clear();
  if (typeof localStorage !== 'undefined') {
    const prefix = usuarioId ? `bstore_cache_${usuarioId}_` : 'bstore_cache_';
    const keysToRemove: string[] = [];
    for (let i = 0; i < localStorage.length; i++) {
      const k = localStorage.key(i);
      if (k && k.startsWith(prefix)) {
        keysToRemove.push(k);
      }
    }
    keysToRemove.forEach((k) => localStorage.removeItem(k));
  }
}

interface UseStaleResourceOptions {
  cacheKey: string;
  usuarioId?: string;
  maxAgeMs?: number; // Padrão: 5 minutos na RAM / 24h em storage
}

/**
 * Hook de dados com estratégia Stale-While-Revalidate:
 * 1. Apresenta de imediato os dados em cache (memória ou localStorage) em 0ms.
 * 2. Atualiza em segundo plano através do loader.
 * 3. Permite invalidação seletiva e sincronização entre ecrãs.
 */
export function useStaleResource<T>(
  loader: (signal: AbortSignal) => Promise<T>,
  options: UseStaleResourceOptions,
) {
  const { cacheKey, usuarioId = 'padrao', maxAgeMs = 5 * 60 * 1000 } = options;
  const storageKey = `bstore_cache_${usuarioId}_${cacheKey}`;

  // Tenta obter dados existentes na memória ou em localStorage imediatamente
  const getInitialCachedData = (): T | null => {
    // 1. Memória RAM
    const inMem = memoryCache.get(storageKey);
    if (inMem && Date.now() - inMem.timestamp < maxAgeMs) {
      return inMem.data as T;
    }

    // 2. LocalStorage persistente
    if (typeof localStorage !== 'undefined') {
      try {
        const raw = localStorage.getItem(storageKey);
        if (raw) {
          const parsed = JSON.parse(raw);
          if (parsed && parsed.data) {
            memoryCache.set(storageKey, { data: parsed.data, timestamp: parsed.timestamp || Date.now() });
            return parsed.data as T;
          }
        }
      } catch {}
    }
    return null;
  };

  const initialData = getInitialCachedData();
  const [data, setData] = useState<T | null>(initialData);
  const [loading, setLoading] = useState<boolean>(!initialData);
  const [isRevalidating, setIsRevalidating] = useState<boolean>(Boolean(initialData));
  const [error, setError] = useState<string | null>(null);

  const controller = useRef<AbortController | null>(null);

  const reload = useCallback(async () => {
    controller.current?.abort();
    const request = new AbortController();
    controller.current = request;

    if (!data) {
      setLoading(true);
    } else {
      setIsRevalidating(true);
    }
    setError(null);

    try {
      const fresh = await loader(request.signal);
      if (!request.signal.aborted) {
        setData(fresh);
        // Atualiza memória RAM
        memoryCache.set(storageKey, { data: fresh, timestamp: Date.now() });

        // Atualiza LocalStorage em segundo plano
        if (typeof localStorage !== 'undefined') {
          try {
            localStorage.setItem(storageKey, JSON.stringify({ data: fresh, timestamp: Date.now() }));
          } catch {}
        }
      }
    } catch (err) {
      if (!request.signal.aborted) {
        // Se já temos dados antigos, mantemos sem quebrar a interface
        if (!data) {
          setError(err instanceof Error ? err.message : 'Não foi possível carregar os dados.');
        }
      }
    } finally {
      if (!request.signal.aborted) {
        setLoading(false);
        setIsRevalidating(false);
      }
    }
  }, [loader, storageKey, data]);

  // Regista listener de invalidação
  useEffect(() => {
    if (!listeners.has(cacheKey)) {
      listeners.set(cacheKey, new Set());
    }
    const set = listeners.get(cacheKey)!;
    const onInvalidate = () => void reload();
    set.add(onInvalidate);

    return () => {
      set.delete(onInvalidate);
    };
  }, [cacheKey, reload]);

  useEffect(() => {
    void reload();
    return () => controller.current?.abort();
  }, [reload]);

  return { data, loading, isRevalidating, error, reload };
}
