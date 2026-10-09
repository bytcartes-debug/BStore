// IndexedDB offline queue for BStore sales and catalog caching

const DB_NAME = 'bstore_offline_db';
const DB_VERSION = 1;
const STORE_PENDENTES = 'vendas_pendentes';
const STORE_CACHE = 'cache_local';

export interface VendaPendente {
  uuidCliente: string;
  dataCriacao: string;
  payload: any;
  erro?: string;
}

function openDB(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    if (!('indexedDB' in window)) {
      reject(new Error('IndexedDB não suportado'));
      return;
    }
    const request = indexedDB.open(DB_NAME, DB_VERSION);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(STORE_PENDENTES)) {
        db.createObjectStore(STORE_PENDENTES, { keyPath: 'uuidCliente' });
      }
      if (!db.objectStoreNames.contains(STORE_CACHE)) {
        db.createObjectStore(STORE_CACHE, { keyPath: 'chave' });
      }
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

export async function salvarVendaPendente(venda: VendaPendente): Promise<void> {
  try {
    const db = await openDB();
    const tx = db.transaction(STORE_PENDENTES, 'readwrite');
    tx.objectStore(STORE_PENDENTES).put(venda);
    return new Promise((resolve, reject) => {
      tx.oncomplete = () => {
        window.dispatchEvent(new CustomEvent('bstore:pendentes-atualizadas'));
        resolve();
      };
      tx.onerror = () => reject(tx.error);
    });
  } catch (err) {
    console.error('Erro ao salvar venda pendente offline:', err);
  }
}

export async function listarVendasPendentes(): Promise<VendaPendente[]> {
  try {
    const db = await openDB();
    const tx = db.transaction(STORE_PENDENTES, 'readonly');
    const store = tx.objectStore(STORE_PENDENTES);
    return new Promise((resolve) => {
      const req = store.getAll();
      req.onsuccess = () => resolve(req.result || []);
      req.onerror = () => resolve([]);
    });
  } catch {
    return [];
  }
}

export async function removerVendaPendente(uuidCliente: string): Promise<void> {
  try {
    const db = await openDB();
    const tx = db.transaction(STORE_PENDENTES, 'readwrite');
    tx.objectStore(STORE_PENDENTES).delete(uuidCliente);
    return new Promise((resolve) => {
      tx.oncomplete = () => {
        window.dispatchEvent(new CustomEvent('bstore:pendentes-atualizadas'));
        resolve();
      };
      tx.onerror = () => resolve();
    });
  } catch {}
}

export async function guardarCacheLocal(chave: string, dados: any): Promise<void> {
  try {
    const db = await openDB();
    const tx = db.transaction(STORE_CACHE, 'readwrite');
    tx.objectStore(STORE_CACHE).put({ chave, dados, atualizadoEm: Date.now() });
  } catch {}
}

export async function obterCacheLocal<T>(chave: string): Promise<T | null> {
  try {
    const db = await openDB();
    const tx = db.transaction(STORE_CACHE, 'readonly');
    return new Promise((resolve) => {
      const req = tx.objectStore(STORE_CACHE).get(chave);
      req.onsuccess = () => resolve(req.result ? req.result.dados : null);
      req.onerror = () => resolve(null);
    });
  } catch {
    return null;
  }
}

export async function limparDadosLocais(): Promise<void> {
  try {
    const db = await openDB();
    const tx = db.transaction([STORE_PENDENTES, STORE_CACHE], 'readwrite');
    tx.objectStore(STORE_PENDENTES).clear();
    tx.objectStore(STORE_CACHE).clear();
  } catch {}
}

// Sincronizador de vendas pendentes ao voltar online
export async function sincronizarVendasPendentes(
  enviarVendaFn: (payload: any, uuidCliente: string) => Promise<any>
): Promise<{ enviadas: number; erros: number }> {
  const pendentes = await listarVendasPendentes();
  let enviadas = 0;
  let erros = 0;

  for (const item of pendentes) {
    try {
      await enviarVendaFn(item.payload, item.uuidCliente);
      await removerVendaPendente(item.uuidCliente);
      enviadas++;
    } catch (e: any) {
      erros++;
      // Marca erro na venda para o utilizador ver em "Vendas com problema"
      const motivo = e?.message || 'Erro de validação';
      await salvarVendaPendente({ ...item, erro: motivo });
    }
  }

  return { enviadas, erros };
}
