import { useCallback, useEffect, useRef, useState } from 'react';

export function useResource<T>(loader: (signal: AbortSignal) => Promise<T>) {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const controller = useRef<AbortController | null>(null);
  const reload = useCallback(async () => {
    controller.current?.abort();
    const request = new AbortController();
    controller.current = request;
    setLoading(true);
    setError(null);
    try {
      const result = await loader(request.signal);
      if (!request.signal.aborted) setData(result);
    } catch (error) {
      if (!request.signal.aborted)
        setError(error instanceof Error ? error.message : 'Não foi possível carregar os dados.');
    } finally {
      if (!request.signal.aborted) setLoading(false);
    }
  }, [loader]);
  useEffect(() => {
    void reload();
    return () => controller.current?.abort();
  }, [reload]);
  return { data, loading, error, reload };
}

export function useMutation() {
  const lock = useRef(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const run = async (action: () => Promise<void>) => {
    if (lock.current) return;
    lock.current = true;
    setPending(true);
    setError(null);
    try {
      await action();
    } catch (error) {
      setError(
        error instanceof Error ? error.message : 'Não foi possível guardar. Tente novamente.',
      );
    } finally {
      lock.current = false;
      setPending(false);
    }
  };
  return { pending, error, setError, run };
}
