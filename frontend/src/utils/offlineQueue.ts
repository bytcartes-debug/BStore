// IndexedDB offline queue and local-first persistence for BStore sales and catalog caching

const DB_NAME = 'bstore_offline_db';
const DB_VERSION = 1;
const STORE_PENDENTES = 'vendas_pendentes';
const STORE_CACHE = 'cache_local';

export interface VendaPendente {
  uuidCliente: string;
  dataCriacao: string;
  numeroProvisorio?: string;
  total: string;
  itensQtd: number;
  resumoItens?: string;
  metodos: string;
  payload: any;
  erro?: string;
  tentativas?: number;
}

function openDB(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    if (typeof window === 'undefined' || !('indexedDB' in window)) {
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

export function gerarNumeroProvisorioOffline(): string {
  const ts = Date.now().toString().slice(-6);
  const aleatorio = Math.floor(10 + Math.random() * 90);
  return `OFF-${ts}-${aleatorio}`;
}

export async function salvarVendaPendente(venda: VendaPendente): Promise<void> {
  try {
    const db = await openDB();
    const tx = db.transaction(STORE_PENDENTES, 'readwrite');
    const itemComTentativas = {
      ...venda,
      tentativas: venda.tentativas ?? 0,
      numeroProvisorio: venda.numeroProvisorio || gerarNumeroProvisorioOffline(),
    };
    tx.objectStore(STORE_PENDENTES).put(itemComTentativas);
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
      req.onsuccess = () => {
        const lista: VendaPendente[] = req.result || [];
        // Ordena por data decrescente
        lista.sort((a, b) => new Date(b.dataCriacao).getTime() - new Date(a.dataCriacao).getTime());
        resolve(lista);
      };
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

export function abaterStockProdutosLocais<T extends { id: number; stock: string }>(
  produtos: T[],
  itensVendidos: Array<{ produtoId?: number; quantidade: string | number }>
): T[] {
  if (!produtos || produtos.length === 0 || !itensVendidos || itensVendidos.length === 0) {
    return produtos;
  }

  const mapaQtd = new Map<number, number>();
  for (const it of itensVendidos) {
    if (it.produtoId) {
      const atual = mapaQtd.get(it.produtoId) || 0;
      const qtdNum = typeof it.quantidade === 'number' ? it.quantidade : parseFloat(it.quantidade || '0');
      mapaQtd.set(it.produtoId, atual + (isNaN(qtdNum) ? 0 : qtdNum));
    }
  }

  return produtos.map((p) => {
    const abatimento = mapaQtd.get(p.id);
    if (!abatimento) return p;
    const stockAtual = parseFloat(p.stock || '0');
    const novoStock = Math.max(0, stockAtual - abatimento);
    return {
      ...p,
      stock: novoStock.toFixed(2),
    };
  });
}

export async function limparDadosLocais(): Promise<void> {
  try {
    const db = await openDB();
    const tx = db.transaction([STORE_PENDENTES, STORE_CACHE], 'readwrite');
    tx.objectStore(STORE_PENDENTES).clear();
    tx.objectStore(STORE_CACHE).clear();
  } catch {}

  try {
    const keysToRemove: string[] = [];
    for (let i = 0; i < localStorage.length; i++) {
      const k = localStorage.key(i);
      if (k && k.startsWith('bstore_')) {
        keysToRemove.push(k);
      }
    }
    for (const k of keysToRemove) {
      localStorage.removeItem(k);
    }
  } catch {}
}

export async function sincronizarVendasPendentes(
  enviarVendaFn: (payload: any, uuidCliente: string) => Promise<any>
): Promise<{ enviadas: number; erros: number; falhas: Array<{ uuid: string; motivo: string }> }> {
  const pendentes = await listarVendasPendentes();
  let enviadas = 0;
  let erros = 0;
  const falhas: Array<{ uuid: string; motivo: string }> = [];

  for (const item of pendentes) {
    try {
      await enviarVendaFn(item.payload, item.uuidCliente);
      await removerVendaPendente(item.uuidCliente);
      enviadas++;
    } catch (e: any) {
      erros++;
      const motivo = e?.message || 'Falha ao sincronizar venda';
      falhas.push({ uuid: item.uuidCliente, motivo });
      await salvarVendaPendente({
        ...item,
        erro: motivo,
        tentativas: (item.tentativas || 0) + 1,
      });
    }
  }

  return { enviadas, erros, falhas };
}
