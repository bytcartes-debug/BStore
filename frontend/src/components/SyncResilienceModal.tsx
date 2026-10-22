import { useState, useEffect, useCallback } from 'react';
import {
  Wifi,
  WifiOff,
  RefreshCw,
  CheckCircle,
  AlertTriangle,
  Clock,
  Trash2,
  Database,
  Smartphone,
} from 'lucide-react';
import { Modal, EmptyState, Spinner } from './UI';
import {
  listarVendasPendentes,
  removerVendaPendente,
  sincronizarVendasPendentes,
  type VendaPendente,
} from '../utils/offlineQueue';
import { apiRequest } from '../utils/api';
import { useToast } from '../utils/toast';

interface SyncResilienceModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export function SyncResilienceModal({ isOpen, onClose }: SyncResilienceModalProps) {
  const [online, setOnline] = useState(() => navigator.onLine);
  const [pingMs, setPingMs] = useState<number | null>(null);
  const [pendentes, setPendentes] = useState<VendaPendente[]>([]);
  const [sincronizando, setSincronizando] = useState(false);
  const [carregando, setCarregando] = useState(true);
  const toast = useToast();

  const carregarPendentes = useCallback(async () => {
    setCarregando(true);
    try {
      const lista = await listarVendasPendentes();
      setPendentes(lista);
    } catch {
      setPendentes([]);
    } finally {
      setCarregando(false);
    }
  }, []);

  const medirPing = useCallback(async () => {
    if (!navigator.onLine) {
      setPingMs(null);
      return;
    }
    const inicio = performance.now();
    try {
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), 4000);
      const res = await fetch('/api/saude', { credentials: 'same-origin', signal: controller.signal });
      clearTimeout(timeout);
      if (res.ok) {
        setPingMs(Math.round(performance.now() - inicio));
        setOnline(true);
      } else {
        setPingMs(null);
      }
    } catch {
      setPingMs(null);
      setOnline(false);
    }
  }, []);

  useEffect(() => {
    if (!isOpen) return;
    void carregarPendentes();
    void medirPing();

    const handleOnline = () => {
      setOnline(true);
      void medirPing();
    };
    const handleOffline = () => {
      setOnline(false);
      setPingMs(null);
    };
    const handlePendentes = () => void carregarPendentes();

    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);
    window.addEventListener('bstore:pendentes-atualizadas', handlePendentes);

    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
      window.removeEventListener('bstore:pendentes-atualizadas', handlePendentes);
    };
  }, [isOpen, carregarPendentes, medirPing]);

  const executarSincronizacao = async () => {
    if (sincronizando) return;
    if (!online) {
      toast('Sem ligação à rede para sincronizar.');
      return;
    }
    setSincronizando(true);
    try {
      const res = await sincronizarVendasPendentes(async (payload, uuidCliente) => {
        return apiRequest('/api/vendas/lote', {
          method: 'POST',
          headers: {
            'Idempotency-Key': uuidCliente,
          },
          body: JSON.stringify(payload),
        });
      });

      await carregarPendentes();

      if (res.enviadas > 0 && res.erros === 0) {
        toast(`✅ Sincronizadas ${res.enviadas} venda(s) com sucesso.`);
      } else if (res.enviadas > 0 && res.erros > 0) {
        toast(`⚠️ ${res.enviadas} sincronizada(s), mas ${res.erros} falharam.`);
      } else if (res.erros > 0) {
        toast(`❌ Falha ao sincronizar ${res.erros} venda(s). Verifique os detalhes.`);
      } else {
        toast('Nenhuma venda pendente para sincronizar.');
      }
    } catch (err: any) {
      toast('Erro durante a sincronização: ' + (err?.message || 'Falha de comunicação'));
    } finally {
      setSincronizando(false);
    }
  };

  const descartarItem = async (uuidCliente: string) => {
    if (!window.confirm('Tem a certeza que deseja descartar esta venda pendente?')) return;
    await removerVendaPendente(uuidCliente);
    await carregarPendentes();
    toast('Venda pendente removida da fila local.');
  };

  if (!isOpen) return null;

  const totalPendente = pendentes.reduce((acc, it) => {
    const val = parseFloat(it.total || '0');
    return acc + (isNaN(val) ? 0 : val);
  }, 0);

  return (
    <Modal title="Monitor de Resiliência & Sincronização Offline" onClose={onClose} wide>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
        {/* Painel de Diagnóstico do Sistema */}
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
            gap: '0.75rem',
            background: 'var(--bg-subtle, rgba(0, 0, 0, 0.03))',
            padding: '1rem',
            borderRadius: '0.75rem',
            border: '1px solid var(--border-color, rgba(0,0,0,0.08))',
          }}
        >
          {/* Status da Conexão */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
            <div
              style={{
                width: '40px',
                height: '40px',
                borderRadius: '50%',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                background: online ? 'rgba(16, 185, 129, 0.15)' : 'rgba(239, 68, 68, 0.15)',
                color: online ? '#10b981' : '#ef4444',
              }}
            >
              {online ? <Wifi size={20} /> : <WifiOff size={20} />}
            </div>
            <div>
              <span style={{ fontSize: '0.75rem', color: 'var(--text-muted, #64748b)', display: 'block' }}>
                Conexão Servidor
              </span>
              <strong style={{ fontSize: '0.95rem', color: online ? '#10b981' : '#ef4444' }}>
                {online ? 'Online' : 'Offline'}
              </strong>
              {pingMs !== null && (
                <span style={{ fontSize: '0.75rem', color: 'var(--text-muted, #64748b)', marginLeft: '0.4rem' }}>
                  ({pingMs} ms)
                </span>
              )}
            </div>
          </div>

          {/* Fila IndexedDB */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
            <div
              style={{
                width: '40px',
                height: '40px',
                borderRadius: '50%',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                background: pendentes.length > 0 ? 'rgba(245, 158, 11, 0.15)' : 'rgba(59, 130, 246, 0.15)',
                color: pendentes.length > 0 ? '#f59e0b' : '#3b82f6',
              }}
            >
              <Database size={20} />
            </div>
            <div>
              <span style={{ fontSize: '0.75rem', color: 'var(--text-muted, #64748b)', display: 'block' }}>
                Fila Local (IndexedDB)
              </span>
              <strong style={{ fontSize: '0.95rem' }}>
                {pendentes.length} {pendentes.length === 1 ? 'venda pendente' : 'vendas pendentes'}
              </strong>
              {totalPendente > 0 && (
                <span style={{ fontSize: '0.75rem', color: 'var(--text-muted, #64748b)', display: 'block' }}>
                  Total: {totalPendente.toFixed(2)} MT
                </span>
              )}
            </div>
          </div>

          {/* Motor PWA */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
            <div
              style={{
                width: '40px',
                height: '40px',
                borderRadius: '50%',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                background: 'rgba(139, 92, 246, 0.15)',
                color: '#8b5cf6',
              }}
            >
              <Smartphone size={20} />
            </div>
            <div>
              <span style={{ fontSize: '0.75rem', color: 'var(--text-muted, #64748b)', display: 'block' }}>
                Modo de Operação
              </span>
              <strong style={{ fontSize: '0.95rem' }}>
                Idempotente & Local-First
              </strong>
            </div>
          </div>
        </div>

        {/* Barra de Ações */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.75rem' }}>
          <div>
            <h4 style={{ margin: 0, fontSize: '1rem' }}>Fila de Vendas Aguardando Transmissão</h4>
            <p style={{ margin: 0, fontSize: '0.8rem', color: 'var(--text-muted, #64748b)' }}>
              Vendas concluídas sem internet ficam gravadas localmente e são enviadas com garantia de não-duplicação.
            </p>
          </div>
          <div style={{ display: 'flex', gap: '0.5rem' }}>
            <button
              className="btn btn-secondary btn-sm"
              onClick={() => void carregarPendentes()}
              disabled={carregando || sincronizando}
              title="Recarregar fila local"
            >
              <RefreshCw size={14} className={carregando ? 'animate-spin' : ''} />
              <span>Atualizar</span>
            </button>
            <button
              className="btn btn-primary btn-sm"
              onClick={executarSincronizacao}
              disabled={pendentes.length === 0 || sincronizando || !online}
            >
              {sincronizando ? <Spinner size="small" /> : <RefreshCw size={14} />}
              <span>{sincronizando ? 'A sincronizar…' : 'Sincronizar Todas'}</span>
            </button>
          </div>
        </div>

        {/* Tabela de Vendas Pendentes */}
        {carregando ? (
          <div style={{ padding: '2rem 0', textAlign: 'center' }}>
            <Spinner size="normal" />
            <p style={{ marginTop: '0.5rem', fontSize: '0.875rem', color: 'var(--text-muted, #64748b)' }}>
              A ler dados do IndexedDB...
            </p>
          </div>
        ) : pendentes.length === 0 ? (
          <EmptyState
            icon={<CheckCircle size={36} color="#10b981" />}
            title="Tudo sincronizado com o servidor"
            description="Não existem vendas pendentes na fila local do seu dispositivo."
          />
        ) : (
          <div style={{ overflowX: 'auto', border: '1px solid var(--border-color, #e2e8f0)', borderRadius: '0.5rem' }}>
            <table className="table" style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.85rem' }}>
              <thead>
                <tr style={{ background: 'var(--bg-subtle, rgba(0,0,0,0.02))', borderBottom: '1px solid var(--border-color, #e2e8f0)' }}>
                  <th style={{ padding: '0.6rem 0.75rem', textAlign: 'left' }}>Nº Provisório</th>
                  <th style={{ padding: '0.6rem 0.75rem', textAlign: 'left' }}>Criada Em</th>
                  <th style={{ padding: '0.6rem 0.75rem', textAlign: 'left' }}>Itens</th>
                  <th style={{ padding: '0.6rem 0.75rem', textAlign: 'left' }}>Total</th>
                  <th style={{ padding: '0.6rem 0.75rem', textAlign: 'left' }}>Status / Tentativas</th>
                  <th style={{ padding: '0.6rem 0.75rem', textAlign: 'center' }}>Ação</th>
                </tr>
              </thead>
              <tbody>
                {pendentes.map((item) => (
                  <tr key={item.uuidCliente} style={{ borderBottom: '1px solid var(--border-color, #e2e8f0)' }}>
                    <td style={{ padding: '0.6rem 0.75rem', fontWeight: 600 }}>
                      <code>{item.numeroProvisorio || item.uuidCliente.slice(0, 8)}</code>
                    </td>
                    <td style={{ padding: '0.6rem 0.75rem', color: 'var(--text-muted, #64748b)', whiteSpace: 'nowrap' }}>
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: '0.3rem' }}>
                        <Clock size={12} />
                        {new Date(item.dataCriacao).toLocaleTimeString('pt-MZ', { hour: '2-digit', minute: '2-digit', second: '2-digit' })}
                      </span>
                    </td>
                    <td style={{ padding: '0.6rem 0.75rem' }}>
                      {item.itensQtd} item(s) {item.resumoItens ? `(${item.resumoItens})` : ''}
                    </td>
                    <td style={{ padding: '0.6rem 0.75rem', fontWeight: 700 }}>
                      {parseFloat(item.total || '0').toFixed(2)} MT
                    </td>
                    <td style={{ padding: '0.6rem 0.75rem' }}>
                      {item.erro ? (
                        <span style={{ color: '#ef4444', display: 'inline-flex', alignItems: 'center', gap: '0.3rem', fontSize: '0.78rem' }} title={item.erro}>
                          <AlertTriangle size={13} /> {item.erro.slice(0, 30)}... ({item.tentativas || 0}x)
                        </span>
                      ) : (
                        <span className="badge badge-warning" style={{ fontSize: '0.72rem' }}>
                          Aguardando Envio
                        </span>
                      )}
                    </td>
                    <td style={{ padding: '0.6rem 0.75rem', textAlign: 'center' }}>
                      <button
                        className="icon-btn"
                        onClick={() => void descartarItem(item.uuidCliente)}
                        title="Descartar venda pendente"
                        style={{ color: '#ef4444' }}
                        disabled={sincronizando}
                      >
                        <Trash2 size={15} />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '0.5rem' }}>
          <button className="btn btn-secondary" onClick={onClose}>
            Fechar
          </button>
        </div>
      </div>
    </Modal>
  );
}
