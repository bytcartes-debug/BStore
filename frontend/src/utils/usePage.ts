import { useCallback, useEffect, useState } from 'react';
import { apiRequest } from './api';
import { useResource } from './useResource';

export interface PageResult<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
  totalPages: number;
}

export function useDebouncedValue<T>(value: T, delay = 300) {
  const [settled, setSettled] = useState(value);
  useEffect(() => {
    const timer = window.setTimeout(() => setSettled(value), delay);
    return () => clearTimeout(timer);
  }, [value, delay]);
  return settled;
}

export async function requestPage<T>(url: string, signal: AbortSignal): Promise<PageResult<T>> {
  const data = await apiRequest<PageResult<T>>(url, { signal });
  if (!Array.isArray(data.items)) throw new Error('O servidor precisa de ser atualizado para apresentar esta lista.');
  return data;
}

export function usePage<T>(endpoint: string, filters: Record<string, string>) {
  const filterKey = new URLSearchParams(filters).toString();
  const [position, setPosition] = useState({ key: filterKey, page: 1 });
  const [pageSize, setPageSize] = useState(25);
  const page = position.key === filterKey ? position.page : 1;
  const url = `${endpoint}?page=${page}&pageSize=${pageSize}&${filterKey}`;
  const loader = useCallback(async (signal: AbortSignal) => ({ result: await requestPage<T>(url, signal), url }), [url]);
  const resource = useResource(loader);
  const data = resource.data?.url === url ? resource.data.result : null;
  useEffect(() => {
    if (data && page > data.totalPages) setPosition({ key: filterKey, page: data.totalPages });
  }, [data, page, filterKey]);
  return {
    data,
    loading: resource.loading || (!data && !resource.error),
    error: resource.error,
    reload: resource.reload,
    page,
    pageSize,
    setPage: (next: number) => setPosition({ key: filterKey, page: next }),
    setPageSize: (size: number) => { setPageSize(size); setPosition({ key: filterKey, page: 1 }); },
  };
}
