import { useState, useEffect, useCallback } from 'react';
import { apiRequest } from '../utils/api';
import type { UserSession } from '../App';
import {
  ShieldCheck,
  Search,
  RefreshCw,
  Eye,
  Calendar,
  User as UserIcon,
  Tag,
  Globe,
  ChevronLeft,
  ChevronRight,
  Filter,
} from 'lucide-react';
import {
  PageHeading,
  Loading,
  LoadError,
  EmptyState,
  Modal,
} from '../components/UI';

export interface RegistroAuditoriaItem {
  id: number;
  usuarioId: number;
  usuarioNome: string;
  tipoEntidade: string;
  acao: string;
  entidadeId: number | null;
  detalhes: string;
  ipOrigem: string | null;
  criadoEm: string;
}

interface RespostaAuditoria {
  registros: RegistroAuditoriaItem[];
  total: number;
  pagina: number;
  limite: number;
  totalPaginas: number;
}

const ENTIDADES = [
  { value: '', label: 'Todas as Entidades' },
  { value: 'AUTENTICACAO', label: 'Autenticação' },
  { value: 'VENDA', label: 'Vendas' },
  { value: 'CAIXA', label: 'Caixa & Sessões' },
  { value: 'PRODUTO', label: 'Produtos' },
  { value: 'STOCK', label: 'Stock & Inventário' },
  { value: 'UTILIZADOR', label: 'Utilizadores' },
];

function formatarDataHora(iso: string) {
  try {
    const d = new Date(iso);
    return d.toLocaleString('pt-MZ', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
  } catch {
    return iso;
  }
}

function AcaoBadge({ acao }: { acao: string }) {
  let cor = 'badge-info';
  const a = acao.toUpperCase();

  if (a.includes('LOGIN') || a.includes('CRIAR') || a.includes('ABERTURA')) {
    cor = 'badge-success';
  } else if (a.includes('ANULAR') || a.includes('ELIMINAR') || a.includes('DEVOLVER') || a.includes('DELETE')) {
    cor = 'badge-danger';
  } else if (a.includes('FECHO') || a.includes('AJUSTE') || a.includes('SANGRIA') || a.includes('DESPESA')) {
    cor = 'badge-warning';
  }

  return (
    <span className={`badge ${cor}`} style={{ textTransform: 'uppercase', letterSpacing: '0.04em', fontSize: '0.74rem' }}>
      {acao.replace(/_/g, ' ')}
    </span>
  );
}

export default function AuditoriaPage({ user: _user }: { user?: UserSession | null }) {
  void _user;
  const [dados, setDados] = useState<RespostaAuditoria | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [pagina, setPagina] = useState(1);
  const [entidadeFiltro, setEntidadeFiltro] = useState('');
  const [acaoFiltro, setAcaoFiltro] = useState('');
  const [detalhesItem, setDetalhesItem] = useState<RegistroAuditoriaItem | null>(null);

  const carregarLogs = useCallback(async (pag: number, ent: string, acao: string, signal?: AbortSignal) => {
    setLoading(true);
    setError(null);
    try {
      const params = new URLSearchParams();
      params.set('pagina', String(pag));
      params.set('limite', '25');
      if (ent) params.set('tipoEntidade', ent);
      if (acao) params.set('acao', acao);

      const res = await apiRequest<RespostaAuditoria>(`/api/auditoria?${params.toString()}`, { signal });
      setDados(res);
    } catch (e: any) {
      if (!signal?.aborted) {
        setError(e?.message || 'Não foi possível carregar a trilha de auditoria.');
      }
    } finally {
      if (!signal?.aborted) {
        setLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    void carregarLogs(pagina, entidadeFiltro, acaoFiltro, controller.signal);
    return () => controller.abort();
  }, [pagina, entidadeFiltro, acaoFiltro, carregarLogs]);

  const handleEntidadeChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setEntidadeFiltro(e.target.value);
    setPagina(1);
  };

  const handleAcaoSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPagina(1);
    void carregarLogs(1, entidadeFiltro, acaoFiltro);
  };

  return (
    <div className="page-container">
      <PageHeading
        title="Trilha de Auditoria & Não-Repúdio"
        description="Histórico imutável de eventos críticos, autenticação e movimentações operacionais (RLS activo)"
      >
        <button
          className="btn btn-secondary"
          onClick={() => void carregarLogs(pagina, entidadeFiltro, acaoFiltro)}
          disabled={loading}
          title="Recarregar logs"
        >
          <RefreshCw size={16} className={loading ? 'animate-spin' : ''} />
          <span>Atualizar</span>
        </button>
      </PageHeading>

      {/* Barra de Filtros */}
      <div className="card" style={{ marginBottom: '1.25rem', padding: '1rem' }}>
        <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', alignItems: 'center' }}>
          <div style={{ minWidth: '180px', flex: '1 1 200px' }}>
            <label className="field-label" htmlFor="filtro-entidade" style={{ marginBottom: '0.35rem', display: 'flex', alignItems: 'center', gap: '0.4rem', fontSize: '0.8rem' }}>
              <Filter size={14} /> Entidade
            </label>
            <select
              id="filtro-entidade"
              className="field-input"
              value={entidadeFiltro}
              onChange={handleEntidadeChange}
            >
              {ENTIDADES.map((item) => (
                <option key={item.value} value={item.value}>
                  {item.label}
                </option>
              ))}
            </select>
          </div>

          <div style={{ minWidth: '220px', flex: '2 1 250px' }}>
            <form onSubmit={handleAcaoSubmit}>
              <label className="field-label" htmlFor="filtro-acao" style={{ marginBottom: '0.35rem', display: 'flex', alignItems: 'center', gap: '0.4rem', fontSize: '0.8rem' }}>
                <Search size={14} /> Filtrar Ação
              </label>
              <div style={{ display: 'flex', gap: '0.5rem' }}>
                <input
                  id="filtro-acao"
                  type="text"
                  className="field-input"
                  placeholder="Ex: LOGIN, FECHO_CAIXA, CRIAR_PRODUTO..."
                  value={acaoFiltro}
                  onChange={(e) => setAcaoFiltro(e.target.value)}
                />
                <button type="submit" className="btn btn-primary" style={{ flexShrink: 0 }}>
                  Filtrar
                </button>
              </div>
            </form>
          </div>
        </div>
      </div>

      {/* Conteúdo Principal */}
      {loading && !dados ? (
        <Loading label="A carregar registos de auditoria..." />
      ) : error ? (
        <LoadError message={error} retry={() => void carregarLogs(pagina, entidadeFiltro, acaoFiltro)} />
      ) : !dados || dados.registros.length === 0 ? (
        <EmptyState
          icon={<ShieldCheck size={34} strokeWidth={1.8} />}
          title="Nenhum registo de auditoria encontrado"
          description="Nenhuma ação registada para os filtros selecionados."
        />
      ) : (
        <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
          <div style={{ overflowX: 'auto' }}>
            <table className="table" style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.875rem' }}>
              <thead>
                <tr style={{ background: 'var(--bg-subtle, rgba(0, 0, 0, 0.03))', borderBottom: '1px solid var(--border-color, #e2e8f0)', textAlign: 'left' }}>
                  <th style={{ padding: '0.75rem 1rem', width: '170px' }}>Data & Hora</th>
                  <th style={{ padding: '0.75rem 1rem' }}>Utilizador</th>
                  <th style={{ padding: '0.75rem 1rem' }}>Ação</th>
                  <th style={{ padding: '0.75rem 1rem' }}>Entidade / ID</th>
                  <th style={{ padding: '0.75rem 1rem' }}>Detalhes</th>
                  <th style={{ padding: '0.75rem 1rem', width: '120px' }}>Origem</th>
                  <th style={{ padding: '0.75rem 1rem', width: '80px', textAlign: 'center' }}>Ações</th>
                </tr>
              </thead>
              <tbody>
                {dados.registros.map((item) => (
                  <tr
                    key={item.id}
                    style={{ borderBottom: '1px solid var(--border-color, #e2e8f0)', transition: 'background 0.15s' }}
                  >
                    <td style={{ padding: '0.75rem 1rem', whiteSpace: 'nowrap', color: 'var(--text-muted, #64748b)' }}>
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: '0.35rem' }}>
                        <Calendar size={13} />
                        {formatarDataHora(item.criadoEm)}
                      </span>
                    </td>
                    <td style={{ padding: '0.75rem 1rem', fontWeight: 500 }}>
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: '0.4rem' }}>
                        <UserIcon size={14} style={{ color: 'var(--text-muted, #64748b)' }} />
                        {item.usuarioNome || `ID: ${item.usuarioId}`}
                      </span>
                    </td>
                    <td style={{ padding: '0.75rem 1rem', whiteSpace: 'nowrap' }}>
                      <AcaoBadge acao={item.acao} />
                    </td>
                    <td style={{ padding: '0.75rem 1rem', whiteSpace: 'nowrap' }}>
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: '0.35rem', fontWeight: 500 }}>
                        <Tag size={13} style={{ color: 'var(--text-muted, #64748b)' }} />
                        {item.tipoEntidade} {item.entidadeId ? `#${item.entidadeId}` : ''}
                      </span>
                    </td>
                    <td style={{ padding: '0.75rem 1rem', maxWidth: '300px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      <span title={item.detalhes}>{item.detalhes}</span>
                    </td>
                    <td style={{ padding: '0.75rem 1rem', whiteSpace: 'nowrap', color: 'var(--text-muted, #64748b)', fontSize: '0.8rem' }}>
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: '0.35rem' }}>
                        <Globe size={13} />
                        {item.ipOrigem || '—'}
                      </span>
                    </td>
                    <td style={{ padding: '0.75rem 1rem', textAlign: 'center' }}>
                      <button
                        className="icon-btn"
                        onClick={() => setDetalhesItem(item)}
                        title="Ver detalhes completos"
                        style={{ padding: '0.35rem', borderRadius: '4px' }}
                      >
                        <Eye size={16} />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Paginação */}
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              padding: '0.85rem 1.25rem',
              borderTop: '1px solid var(--border-color, #e2e8f0)',
              fontSize: '0.85rem',
              color: 'var(--text-muted, #64748b)',
              flexWrap: 'wrap',
              gap: '0.75rem',
            }}
          >
            <span>
              Mostrando <strong>{dados.registros.length}</strong> de <strong>{dados.total}</strong> eventos
            </span>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <button
                className="btn btn-secondary btn-sm"
                onClick={() => setPagina((p) => Math.max(1, p - 1))}
                disabled={pagina <= 1 || loading}
              >
                <ChevronLeft size={15} /> Anterior
              </button>
              <span style={{ padding: '0 0.5rem' }}>
                Página <strong>{pagina}</strong> de <strong>{dados.totalPaginas || 1}</strong>
              </span>
              <button
                className="btn btn-secondary btn-sm"
                onClick={() => setPagina((p) => Math.min(dados.totalPaginas, p + 1))}
                disabled={pagina >= dados.totalPaginas || loading}
              >
                Próximo <ChevronRight size={15} />
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Modal de Detalhes do Evento */}
      {detalhesItem && (
        <Modal
          title={`Evento de Auditoria #${detalhesItem.id}`}
          onClose={() => setDetalhesItem(null)}
        >
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', fontSize: '0.875rem' }}>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '0.75rem', background: 'var(--bg-subtle, rgba(0, 0, 0, 0.02))', padding: '0.75rem', borderRadius: '6px' }}>
              <div>
                <strong style={{ display: 'block', fontSize: '0.75rem', color: 'var(--text-muted, #64748b)' }}>Data e Hora:</strong>
                <span>{formatarDataHora(detalhesItem.criadoEm)}</span>
              </div>
              <div>
                <strong style={{ display: 'block', fontSize: '0.75rem', color: 'var(--text-muted, #64748b)' }}>Utilizador Responsável:</strong>
                <span>{detalhesItem.usuarioNome || `ID: ${detalhesItem.usuarioId}`}</span>
              </div>
              <div>
                <strong style={{ display: 'block', fontSize: '0.75rem', color: 'var(--text-muted, #64748b)' }}>Ação Executada:</strong>
                <AcaoBadge acao={detalhesItem.acao} />
              </div>
              <div>
                <strong style={{ display: 'block', fontSize: '0.75rem', color: 'var(--text-muted, #64748b)' }}>Entidade / ID:</strong>
                <span>{detalhesItem.tipoEntidade} {detalhesItem.entidadeId ? `#${detalhesItem.entidadeId}` : '(Geral)'}</span>
              </div>
              <div>
                <strong style={{ display: 'block', fontSize: '0.75rem', color: 'var(--text-muted, #64748b)' }}>IP de Origem:</strong>
                <span>{detalhesItem.ipOrigem || 'Não registado'}</span>
              </div>
            </div>

            <div>
              <strong style={{ display: 'block', marginBottom: '0.35rem', fontSize: '0.8rem', color: 'var(--text-muted, #64748b)' }}>
                Descrição Detalhada do Evento:
              </strong>
              <div
                style={{
                  background: 'var(--bg-code, #0f172a)',
                  color: '#f8fafc',
                  padding: '0.85rem',
                  borderRadius: '6px',
                  fontFamily: 'monospace',
                  fontSize: '0.82rem',
                  lineHeight: '1.45',
                  wordBreak: 'break-word',
                  whiteSpace: 'pre-wrap',
                }}
              >
                {detalhesItem.detalhes || 'Nenhum detalhe adicional informado.'}
              </div>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '0.5rem' }}>
              <button className="btn btn-secondary" onClick={() => setDetalhesItem(null)}>
                Fechar
              </button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
}
