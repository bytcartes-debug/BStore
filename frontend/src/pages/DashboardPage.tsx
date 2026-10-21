import { useState, useMemo, useCallback } from 'react';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import {
  AlertTriangle,
  ArrowUpRight,
  Banknote,
  BarChart3,
  Calendar,
  CheckCircle2,
  CreditCard,
  FileText,
  Package,
  Receipt,
  ShoppingCart,
  Coins,
  Tag,
  Target,
  Edit2,
  TrendingDown,
  TrendingUp,
  Trophy,
  Users,
  Bot,
  Sparkles,
  Copy,
  Send,
  MessageSquare,
  ChevronDown,
  ChevronUp,
  X,
} from 'lucide-react';
import { apiRequest } from '../utils/api';
import { useResource } from '../utils/useResource';
import { useIa } from '../utils/useIa';
import { useToast } from '../utils/toast';
import { decimalSeguro, formatMoney, formatQuantity } from '../utils/decimal';
import { EmptyState, LoadError, PageHeading, Spinner, Skeleton, SkeletonCard } from '../components/UI';
import { AnimatedNumber } from '../components/ui/AnimatedNumber';
import type { PageId } from '../App';
import './DashboardPage.css';

interface DashboardData {
  inicio?: string;
  fim?: string;
  filtroPersonalizado?: boolean;
  totalVendasHoje?: string;
  lucroHoje?: string;
  lucroUltimos7Dias?: string;
  totalVendasPeriodo?: string;
  lucroPeriodo?: string;
  valorTotalStockCusto?: string;
  totalProdutos?: number;
  totalCategorias?: number;
  totalDevedores?: number;
  faltaReporCount?: number;
  perdasMes?: string;
  alertasStock?: {
    id: number;
    nome: string;
    stock: string;
    stockMinimo: string;
    unidade?: string;
  }[];
  vendasRecentes?: {
    id: number;
    produto: string;
    quantidade: string;
    total: string;
    data: string;
  }[];
  vendasPorDia?: { dia: string; dataIso?: string; total: string }[];
  vendasPorMetodo?: Record<string, string>;
  produtosMaisVendidos?: {
    produtoId: number;
    nome: string;
    quantidadeTotal?: string;
    valorTotal?: string;
    quantidade?: string;
    total?: string;
  }[];
}

type PeriodoPreset = '7dias' | 'hoje' | 'ontem' | '30dias' | 'mes' | 'custom';

function formatIsoDate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function calcularIntervalo(p: PeriodoPreset, customIn: string, customOut: string) {
  const hoje = new Date();
  if (p === 'hoje') {
    const s = formatIsoDate(hoje);
    return { inicio: s, fim: s, label: 'Hoje' };
  }
  if (p === 'ontem') {
    const ont = new Date();
    ont.setDate(ont.getDate() - 1);
    const s = formatIsoDate(ont);
    return { inicio: s, fim: s, label: 'Ontem' };
  }
  if (p === '7dias') {
    const i = new Date();
    i.setDate(i.getDate() - 6);
    return { inicio: formatIsoDate(i), fim: formatIsoDate(hoje), label: 'Últimos 7 dias' };
  }
  if (p === '30dias') {
    const i = new Date();
    i.setDate(i.getDate() - 29);
    return { inicio: formatIsoDate(i), fim: formatIsoDate(hoje), label: 'Últimos 30 dias' };
  }
  if (p === 'mes') {
    const i = new Date(hoje.getFullYear(), hoje.getMonth(), 1);
    return { inicio: formatIsoDate(i), fim: formatIsoDate(hoje), label: 'Este mês' };
  }
  return {
    inicio: customIn || formatIsoDate(hoje),
    fim: customOut || formatIsoDate(hoje),
    label: 'Personalizado',
  };
}

function formatarDataCurta(iso: string): string {
  if (!iso || !iso.includes('-')) return iso;
  const parts = iso.split('-');
  return `${parts[2]}/${parts[1]}`;
}

export default function DashboardPage({ navigate }: { navigate: (page: PageId) => void }) {
  const [preset, setPreset] = useState<PeriodoPreset>('7dias');
  const [customInicio, setCustomInicio] = useState(() => {
    const d = new Date();
    d.setDate(d.getDate() - 6);
    return formatIsoDate(d);
  });
  const [customFim, setCustomFim] = useState(() => formatIsoDate(new Date()));

  const intervalo = useMemo(
    () => calcularIntervalo(preset, customInicio, customFim),
    [preset, customInicio, customFim]
  );

  const loader = useCallback(
    (signal: AbortSignal) => {
      const url =
        preset === '7dias'
          ? '/api/dashboard'
          : `/api/dashboard?inicio=${intervalo.inicio}&fim=${intervalo.fim}`;
      return apiRequest<DashboardData>(url, { signal });
    },
    [preset, intervalo.inicio, intervalo.fim]
  );

  const { data, loading, error, reload } = useResource(loader);
  const [tableView, setTableView] = useState(false);
  const [metaDiaria, setMetaDiaria] = useState<number>(() => {
    try {
      return Number(localStorage.getItem('bstore_meta_diaria') || '5000');
    } catch {
      return 5000;
    }
  });

  const handleEditarMeta = () => {
    const val = window.prompt('Definir meta diária de vendas (MT):', String(metaDiaria));
    if (val === null) return;
    const num = parseFloat(val.replace(',', '.'));
    if (!isNaN(num) && num > 0) {
      setMetaDiaria(num);
      try {
        localStorage.setItem('bstore_meta_diaria', String(num));
      } catch {}
    }
  };

  const { status: iaStatus } = useIa();
  const toast = useToast();

  const [resumoDia, setResumoDia] = useState<string | null>(null);
  const [gerandoResumo, setGerandoResumo] = useState(false);

  // Assistente "Pergunte à BStore"
  const [perguntaTexto, setPerguntaTexto] = useState('');
  const [perguntaCarregando, setPerguntaCarregando] = useState(false);
  const [perguntaResposta, setPerguntaResposta] = useState<{
    resposta: string;
    comoCalculei: Array<{ ferramenta: string; parametros: Record<string, unknown>; resumo: string }>;
    foraDoAmbito?: boolean;
  } | null>(null);
  const [mostrarComoCalculei, setMostrarComoCalculei] = useState(false);

  const handleGerarResumoDia = async (forcar = false) => {
    if (!data) return;
    try {
      setGerandoResumo(true);
      const metricas = {
        data: today,
        totalVendasHoje: data.totalVendasHoje || '0',
        lucroHoje: data.lucroHoje || '0',
        alertasStock: alertasStock.slice(0, 5).map((a) => a.nome),
        produtosMaisVendidos: produtosMaisVendidos.slice(0, 3).map((p) => p.nome),
        totalDevedores: data.totalDevedores || 0,
        forcar,
      };
      const res = await apiRequest<{ resumo: string }>('/api/ia/resumo-dia', {
        method: 'POST',
        body: JSON.stringify(metricas),
      });
      setResumoDia(res.resumo);
    } catch (err) {
      toast('Não foi possível gerar resumo: ' + (err instanceof Error ? err.message : String(err)));
    } finally {
      setGerandoResumo(false);
    }
  };

  const handlePartilharResumoWhatsapp = () => {
    if (!resumoDia) return;
    const url = `https://wa.me/?text=${encodeURIComponent(resumoDia)}`;
    window.open(url, '_blank', 'noopener,noreferrer');
  };

  const handleEnviarPergunta = async (perguntaDireta?: string) => {
    const p = (perguntaDireta || perguntaTexto).trim();
    if (!p) return;
    if (perguntaDireta) setPerguntaTexto(perguntaDireta);
    setPerguntaCarregando(true);
    try {
      const res = await apiRequest<{
        resposta: string;
        comoCalculei: Array<{ ferramenta: string; parametros: Record<string, unknown>; resumo: string }>;
        foraDoAmbito?: boolean;
      }>('/api/ia/perguntar-negocio', {
        method: 'POST',
        body: JSON.stringify({ pergunta: p }),
      });
      setPerguntaResposta(res);
      setMostrarComoCalculei(false);
    } catch (err: unknown) {
      toast(err instanceof Error ? err.message : 'Falha ao responder à pergunta.');
    } finally {
      setPerguntaCarregando(false);
    }
  };

  const today = new Intl.DateTimeFormat('pt-MZ', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  }).format(new Date());

  if (loading && !data) {
    return (
      <div className="page-container" aria-busy="true">
        <div className="page-heading">
          <div className="page-heading-info" style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <Skeleton width={220} height={28} borderRadius={6} />
            <Skeleton width={320} height={16} borderRadius={4} />
          </div>
        </div>
        <div className="stats-grid" style={{ marginBottom: 24 }}>
          {Array.from({ length: 4 }).map((_, i) => (
            <SkeletonCard key={i} />
          ))}
        </div>
        <div className="card" style={{ padding: 24, minHeight: 260 }}>
          <Skeleton width={180} height={20} borderRadius={4} style={{ marginBottom: 16 }} />
          <Skeleton width="100%" height={180} borderRadius={8} />
        </div>
      </div>
    );
  }
  if (!data)
    return (
      <LoadError message={error || 'Não foi possível carregar a visão geral.'} retry={reload} />
    );

  const vendasPorDia = Array.isArray(data.vendasPorDia) ? data.vendasPorDia : [];
  const alertasStock = Array.isArray(data.alertasStock) ? data.alertasStock : [];
  const vendasRecentes = Array.isArray(data.vendasRecentes) ? data.vendasRecentes : [];
  const produtosMaisVendidos = Array.isArray(data.produtosMaisVendidos)
    ? data.produtosMaisVendidos
    : [];
  const vendasPorMetodo =
    data.vendasPorMetodo && typeof data.vendasPorMetodo === 'object' ? data.vendasPorMetodo : {};

  const chartData = vendasPorDia.map((item) => ({
    dia: item?.dia || '',
    total: decimalSeguro(item?.total).toNumber(),
  }));

  const metrics = [
    {
      label: 'Produtos no Catálogo',
      value: Number(data.totalProdutos) || 0,
      icon: Package,
      page: 'produtos' as const,
    },
    {
      label: 'Categorias Ativas',
      value: Number(data.totalCategorias) || 0,
      icon: Tag,
      page: 'categorias' as const,
    },
    {
      label: 'Total de Devedores',
      value: Number(data.totalDevedores) || 0,
      icon: Users,
      page: 'devedores' as const,
    },
    {
      label: 'Falta Repor',
      value: Number(data.faltaReporCount ?? alertasStock.length) || 0,
      icon: AlertTriangle,
      page: 'reposicao' as const,
    },
  ];

  return (
    <div className="dashboard-page">
      <PageHeading title="A sua loja, hoje" description={today}>
        <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
          <button
            type="button"
            className="btn-secondary"
            onClick={() => void handleGerarResumoDia(false)}
            disabled={gerandoResumo}
            style={{ color: 'var(--color-brand, #ea580c)', fontWeight: 600, display: 'inline-flex', alignItems: 'center', gap: 6 }}
          >
            {gerandoResumo ? <Spinner size="small" /> : <Sparkles size={16} />}
            Resumo do Dia (IA)
          </button>
          <button className="btn-primary" onClick={() => navigate('vendas')}>
            <ShoppingCart size={16} strokeWidth={2.2} aria-hidden="true" /> Nova Venda
          </button>
        </div>
      </PageHeading>

      {resumoDia && (
        <section
          style={{
            background: 'var(--bg-card)',
            border: '1px solid var(--border-color)',
            borderLeft: '4px solid #ea580c',
            borderRadius: 8,
            padding: '16px 20px',
            marginBottom: 20,
            display: 'flex',
            flexDirection: 'column',
            gap: 10,
          }}
          aria-label="Resumo inteligente do dia"
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <Bot size={20} color="#ea580c" />
              <strong style={{ fontSize: 15 }}>Análise Executiva da BStore IA</strong>
            </div>
            <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
              <button
                type="button"
                className="btn-secondary"
                style={{ padding: '4px 8px', fontSize: 12, display: 'inline-flex', alignItems: 'center', gap: 4 }}
                onClick={() => {
                  navigator.clipboard.writeText(resumoDia);
                  toast('Resumo copiado para a área de transferência.');
                }}
              >
                <Copy size={13} /> Copiar
              </button>
              <button
                type="button"
                className="btn-primary"
                style={{
                  padding: '4px 8px',
                  fontSize: 12,
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 4,
                  background: '#25D366',
                  borderColor: '#25D366',
                  color: '#fff',
                }}
                onClick={handlePartilharResumoWhatsapp}
                title="Partilhar resumo no WhatsApp"
              >
                <Send size={13} /> WhatsApp
              </button>
              <button
                type="button"
                className="icon-btn"
                style={{ padding: 4 }}
                onClick={() => setResumoDia(null)}
                title="Fechar resumo"
              >
                <X size={15} />
              </button>
            </div>
          </div>
          <div style={{ fontSize: 13, lineHeight: 1.6, whiteSpace: 'pre-wrap', color: 'var(--text-primary)' }}>
            {resumoDia}
          </div>
        </section>
      )}

      {/* RECURSO 3: PERGUNTE À BSTORE (ASSISTENTE EXECUTIVO) */}
      {iaStatus?.ativa && (
        <section
          style={{
            background: 'var(--bg-card)',
            border: '1px solid var(--border-color)',
            borderRadius: 8,
            padding: '14px 18px',
            marginBottom: 20,
            display: 'flex',
            flexDirection: 'column',
            gap: 12,
          }}
          aria-label="Pergunte à BStore"
        >
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 8 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <MessageSquare size={18} color="var(--color-brand, #ea580c)" />
              <strong style={{ fontSize: 14 }}>Pergunte à BStore</strong>
              <span className="badge badge-success" style={{ fontSize: 11, padding: '2px 6px' }}>
                Assistente de Gestão
              </span>
            </div>
            <span style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
              Perguntas sobre vendas, lucros, reposição e saldos em aberto
            </span>
          </div>

          <form
            onSubmit={(e) => {
              e.preventDefault();
              void handleEnviarPergunta();
            }}
            style={{ display: 'flex', gap: 8 }}
          >
            <input
              type="text"
              value={perguntaTexto}
              onChange={(e) => setPerguntaTexto(e.target.value)}
              placeholder="Ex: Quanto vendi esta semana? Quais os 5 produtos com mais lucro? O que devo repor?"
              disabled={perguntaCarregando}
              style={{
                flex: 1,
                padding: '8px 12px',
                borderRadius: 6,
                border: '1px solid var(--border-color, #cbd5e1)',
                fontSize: 13,
              }}
            />
            <button
              type="submit"
              className="btn-primary"
              disabled={perguntaCarregando || !perguntaTexto.trim()}
              style={{ display: 'inline-flex', alignItems: 'center', gap: 6, padding: '8px 14px' }}
            >
              {perguntaCarregando ? <Spinner size="small" /> : <Send size={14} />}
              Perguntar
            </button>
          </form>

          {/* CHIPS DE SUGESTÕES RÁPIDAS */}
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', alignItems: 'center' }}>
            <span style={{ fontSize: 11, color: 'var(--text-secondary)', fontWeight: 600 }}>Sugestões rápidas:</span>
            {[
              'Quanto vendi esta semana?',
              'Quais os produtos com mais lucro?',
              'O que devo repor de stock?',
              'Quem me deve mais?',
            ].map((sug) => (
              <button
                key={sug}
                type="button"
                className="badge dashboard-suggestion-chip"
                style={{
                  cursor: 'pointer',
                  fontSize: 11,
                  padding: '4px 10px',
                  background: 'var(--color-brand-light)',
                  border: '1px solid var(--border-color)',
                  color: 'var(--color-brand)',
                  fontWeight: 650,
                  borderRadius: 12,
                  transition: 'all 0.18s ease',
                }}
                onClick={() => void handleEnviarPergunta(sug)}
                disabled={perguntaCarregando}
              >
                {sug}
              </button>
            ))}
          </div>

          {/* RESPOSTA DO ASSISTENTE */}
          {perguntaResposta && (
            <div
              style={{
                marginTop: 4,
                padding: '12px 14px',
                background: 'var(--bg-subtle)',
                borderRadius: 8,
                border: '1px solid var(--border-color)',
                fontSize: 13,
                lineHeight: 1.5,
              }}
            >
              <div style={{ display: 'flex', alignItems: 'flex-start', gap: 8, marginBottom: 8 }}>
                <Bot size={16} color="var(--color-brand, #ea580c)" style={{ marginTop: 2, flexShrink: 0 }} />
                <div style={{ flex: 1, whiteSpace: 'pre-wrap', color: 'var(--text-primary)' }}>
                  {perguntaResposta.resposta}
                </div>
              </div>

              {perguntaResposta.comoCalculei && perguntaResposta.comoCalculei.length > 0 && (
                <div style={{ marginTop: 8, paddingTop: 8, borderTop: '1px dashed var(--border-color, #cbd5e1)' }}>
                  <button
                    type="button"
                    style={{
                      background: 'none',
                      border: 'none',
                      cursor: 'pointer',
                      fontSize: 11,
                      color: 'var(--text-muted, #64748b)',
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: 4,
                      padding: 0,
                    }}
                    onClick={() => setMostrarComoCalculei(!mostrarComoCalculei)}
                  >
                    {mostrarComoCalculei ? <ChevronUp size={12} /> : <ChevronDown size={12} />}
                    Como calculei isto ({perguntaResposta.comoCalculei.length} consulta(s) auditadas)
                  </button>

                  {mostrarComoCalculei && (
                    <div style={{ marginTop: 6, fontSize: 11, color: 'var(--text-secondary)' }}>
                      {perguntaResposta.comoCalculei.map((c, i) => (
                        <div key={i} style={{ marginBottom: 4, fontFamily: 'monospace' }}>
                          • {c.ferramenta}: {JSON.stringify(c.parametros)}
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}
            </div>
          )}
        </section>
      )}

      <div className="dashboard-filter-bar" role="region" aria-label="Filtrar período da visão geral">
        <div className="filter-presets" role="group" aria-label="Período de análise">
          <button
            type="button"
            className={`filter-preset-btn${preset === 'hoje' ? ' active' : ''}`}
            aria-pressed={preset === 'hoje'}
            onClick={() => setPreset('hoje')}
          >
            Hoje
          </button>
          <button
            type="button"
            className={`filter-preset-btn${preset === 'ontem' ? ' active' : ''}`}
            aria-pressed={preset === 'ontem'}
            onClick={() => setPreset('ontem')}
          >
            Ontem
          </button>
          <button
            type="button"
            className={`filter-preset-btn${preset === '7dias' ? ' active' : ''}`}
            aria-pressed={preset === '7dias'}
            onClick={() => setPreset('7dias')}
          >
            7 Dias
          </button>
          <button
            type="button"
            className={`filter-preset-btn${preset === '30dias' ? ' active' : ''}`}
            aria-pressed={preset === '30dias'}
            onClick={() => setPreset('30dias')}
          >
            30 Dias
          </button>
          <button
            type="button"
            className={`filter-preset-btn${preset === 'mes' ? ' active' : ''}`}
            aria-pressed={preset === 'mes'}
            onClick={() => setPreset('mes')}
          >
            Este Mês
          </button>
          <button
            type="button"
            className={`filter-preset-btn${preset === 'custom' ? ' active' : ''}`}
            aria-pressed={preset === 'custom'}
            onClick={() => setPreset('custom')}
          >
            <Calendar size={13} style={{ display: 'inline-block', verticalAlign: '-1px', marginRight: 4 }} aria-hidden="true" />
            Personalizado
          </button>
        </div>

        {preset === 'custom' && (
          <div className="custom-date-inputs">
            <label className="date-input-label">
              <span>De:</span>
              <input
                type="date"
                aria-label="Data inicial"
                value={customInicio}
                max={customFim || undefined}
                onChange={(e) => setCustomInicio(e.target.value)}
              />
            </label>
            <label className="date-input-label">
              <span>Até:</span>
              <input
                type="date"
                aria-label="Data final"
                value={customFim}
                min={customInicio || undefined}
                onChange={(e) => setCustomFim(e.target.value)}
              />
            </label>
          </div>
        )}
      </div>

      {error && <LoadError message={error} retry={reload} />}

      <section className="store-overview" aria-label="Resumo da loja">
        <div className="today-sales">
          <span className="today-label">
            <Banknote size={17} strokeWidth={2} aria-hidden="true" />{' '}
            {preset === 'hoje' ? 'Vendas de Hoje' : 'Vendas no Período'}
          </span>
          <p className="today-value">
            <AnimatedNumber
              value={decimalSeguro(
                preset === 'hoje'
                  ? data.totalVendasHoje
                  : (data.totalVendasPeriodo ?? data.totalVendasHoje)
              ).toNumber()}
              formatter={(v) => formatMoney(v.toFixed(2))}
            />
          </p>
          <span className="today-description">
            {preset === 'hoje'
              ? 'Total registado no dia'
              : `${intervalo.label}: ${formatarDataCurta(intervalo.inicio)} a ${formatarDataCurta(intervalo.fim)}`}
          </span>
        </div>

        {(data.lucroHoje !== undefined || data.lucroPeriodo !== undefined) && (() => {
          const lucroVal = decimalSeguro(
            preset === 'hoje'
              ? data.lucroHoje
              : (data.lucroPeriodo ?? data.lucroHoje)
          ).toNumber();
          const vendasVal = decimalSeguro(
            preset === 'hoje'
              ? data.totalVendasHoje
              : (data.totalVendasPeriodo ?? data.totalVendasHoje)
          ).toNumber();
          const margemPct = vendasVal > 0 && lucroVal > 0 ? ((lucroVal / vendasVal) * 100).toFixed(1) : null;

          return (
            <div className="today-sales">
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 6 }}>
                <span className="today-label">
                  <Coins size={17} strokeWidth={2} aria-hidden="true" />{' '}
                  {preset === 'hoje' ? 'Lucro Estimado de Hoje' : 'Lucro no Período'}
                </span>
                {margemPct && (
                  <span className="badge badge-success" style={{ fontSize: 11, fontWeight: 700, padding: '2px 8px' }}>
                    +{margemPct}% margem
                  </span>
                )}
              </div>
              <p className="today-value">
                <AnimatedNumber
                  value={lucroVal}
                  formatter={(v) => formatMoney(v.toFixed(2))}
                />
              </p>
              <span className="today-description">
                {preset === 'hoje'
                  ? (data.lucroUltimos7Dias ? `7 dias: ${formatMoney(data.lucroUltimos7Dias)}` : 'Vendas menos custo mercadorias')
                  : 'Vendas com custo conhecido menos custo'}
              </span>
              <small
                style={{
                  display: 'block',
                  fontSize: '0.72rem',
                  color: 'var(--text-muted)',
                  marginTop: '4px',
                }}
              >
                Vendas antigas sem custo não entram no lucro
              </small>
            </div>
          );
        })()}

        {data.valorTotalStockCusto !== undefined && (
          <div className="today-sales">
            <span className="today-label">
              <Package size={17} strokeWidth={2} aria-hidden="true" /> Valor do Stock a Custo
            </span>
            <p className="today-value">
              <AnimatedNumber
                value={decimalSeguro(data.valorTotalStockCusto).toNumber()}
                formatter={(v) => formatMoney(v.toFixed(2))}
              />
            </p>
            <span className="today-description">Capital retido no inventário</span>
          </div>
        )}

        {data.perdasMes !== undefined && (
          <div className="today-sales">
            <span className="today-label">
              <TrendingDown size={17} strokeWidth={2} aria-hidden="true" /> Perdas no Mês
            </span>
            <p className="today-value">
              <AnimatedNumber
                value={decimalSeguro(data.perdasMes).toNumber()}
                formatter={(v) => formatMoney(v.toFixed(2))}
              />
            </p>
            <span className="today-description">Ajustes a custo este mês</span>
          </div>
        )}

        {/* CARD DE METAS DO DIA */}
        <div className="today-sales">
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <span className="today-label">
              <Target size={17} strokeWidth={2} aria-hidden="true" /> Meta de Hoje
            </span>
            <button
              type="button"
              onClick={handleEditarMeta}
              title="Ajustar meta diária de vendas"
              style={{
                background: 'none',
                border: 'none',
                cursor: 'pointer',
                color: 'var(--text-muted)',
                padding: 2,
                display: 'inline-flex',
                alignItems: 'center',
              }}
            >
              <Edit2 size={13} />
            </button>
          </div>
          <p className="today-value" style={{ fontSize: '1.4rem' }}>
            {formatMoney(data.totalVendasHoje || '0')}
            <span style={{ fontSize: '0.85rem', fontWeight: 500, color: 'var(--text-muted)', marginLeft: 6 }}>
              / {formatMoney(metaDiaria)}
            </span>
          </p>
          <div style={{ marginTop: 6 }}>
            {(() => {
              const hojeNum = decimalSeguro(data.totalVendasHoje).toNumber();
              const pct = metaDiaria > 0 ? Math.min(100, Math.round((hojeNum / metaDiaria) * 100)) : 0;
              const concluida = hojeNum >= metaDiaria;
              return (
                <div>
                  <div
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      fontSize: 11,
                      color: 'var(--text-secondary)',
                      marginBottom: 4,
                    }}
                  >
                    <span>{pct}% concluído</span>
                    <span>{concluida ? 'Meta alcançada!' : `Faltam ${formatMoney(Math.max(0, metaDiaria - hojeNum))}`}</span>
                  </div>
                  <div
                    style={{
                      height: 6,
                      borderRadius: 3,
                      background: 'var(--border-color)',
                      overflow: 'hidden',
                    }}
                  >
                    <div
                      style={{
                        height: '100%',
                        width: `${pct}%`,
                        background: concluida ? '#16a34a' : 'var(--color-brand, #ea580c)',
                        transition: 'width 0.4s ease',
                      }}
                    />
                  </div>
                </div>
              );
            })()}
          </div>
        </div>

        <div className="store-metrics">
          {metrics.map(({ label, value, icon: Icon, page }) => (
            <button key={label} className="store-metric" onClick={() => navigate(page)}>
              <span className="metric-label">
                <span className="metric-icon-badge" aria-hidden="true">
                  <Icon size={16} strokeWidth={2} />
                </span>
                {label}
              </span>
              <span className="metric-value">
                <AnimatedNumber value={value} />
                <ArrowUpRight
                  size={15}
                  strokeWidth={2}
                  className="metric-arrow"
                  aria-hidden="true"
                />
              </span>
            </button>
          ))}
        </div>
      </section>

      {/* PAINEL ATENÇÃO HOJE */}
      {((alertasStock && alertasStock.length > 0) || (data.totalDevedores && data.totalDevedores > 0)) && (
        <section
          style={{
            background: 'var(--bg-card)',
            border: '1px solid var(--border-color)',
            borderLeft: '4px solid #ea580c',
            borderRadius: 8,
            padding: '14px 18px',
            marginBottom: 20,
            display: 'flex',
            flexDirection: 'column',
            gap: 10,
          }}
          aria-label="Atenção hoje"
        >
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 8 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <AlertTriangle size={18} color="#ea580c" />
              <strong style={{ fontSize: 14 }}>Atenção Hoje: Ações Prioritárias</strong>
            </div>
            <span style={{ fontSize: 12, color: 'var(--text-secondary)' }}>Itens que exigem acompanhamento do operador</span>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 12 }}>
            {alertasStock.length > 0 && (
              <div
                style={{
                  background: 'var(--bg-subtle)',
                  padding: 10,
                  borderRadius: 6,
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                }}
              >
                <div>
                  <span style={{ fontSize: 13, fontWeight: 600, display: 'block' }}>
                    {alertasStock.length} produto(s) no stock crítico
                  </span>
                  <span style={{ fontSize: 11, color: 'var(--text-secondary)' }}>
                    Ex: {alertasStock.slice(0, 2).map((a) => a.nome).join(', ')}
                  </span>
                </div>
                <button
                  type="button"
                  className="btn-secondary"
                  style={{ fontSize: 11, padding: '4px 8px' }}
                  onClick={() => navigate('reposicao')}
                >
                  Repor Stock
                </button>
              </div>
            )}

            {data.totalDevedores !== undefined && data.totalDevedores > 0 && (
              <div
                style={{
                  background: 'var(--bg-subtle)',
                  padding: 10,
                  borderRadius: 6,
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                }}
              >
                <div>
                  <span style={{ fontSize: 13, fontWeight: 600, display: 'block' }}>
                    {data.totalDevedores} cliente(s) com fiado pendente
                  </span>
                  <span style={{ fontSize: 11, color: 'var(--text-secondary)' }}>
                    Cobranças pendentes na caderneta
                  </span>
                </div>
                <button
                  type="button"
                  className="btn-secondary"
                  style={{ fontSize: 11, padding: '4px 8px' }}
                  onClick={() => navigate('devedores')}
                >
                  Ver Devedores
                </button>
              </div>
            )}
          </div>
        </section>
      )}

      <div className="dashboard-grid">
        <section className="card dashboard-chart" aria-labelledby="sales-chart-title">
          <div className="section-header">
            <div>
              <h3 id="sales-chart-title" className="section-title-with-icon">
                <TrendingUp size={18} strokeWidth={2} aria-hidden="true" /> Vendas dos últimos 7
                dias
              </h3>
              <p>Total diário em meticais (MT)</p>
            </div>
            <button
              className="btn-secondary chart-toggle"
              aria-pressed={tableView}
              onClick={() => setTableView((value) => !value)}
            >
              {tableView ? (
                <>
                  <BarChart3 size={15} strokeWidth={2} aria-hidden="true" /> Ver gráfico
                </>
              ) : (
                <>
                  <FileText size={15} strokeWidth={2} aria-hidden="true" /> Ver tabela
                </>
              )}
            </button>
          </div>

          {tableView ? (
            <div className="table-wrapper">
              <table>
                <caption className="sr-only">Vendas dos últimos sete dias em meticais</caption>
                <thead>
                  <tr>
                    <th scope="col">Dia</th>
                    <th scope="col" className="numeric">
                      Total
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {vendasPorDia.map((item) => (
                    <tr key={item.dia}>
                      <td>{item.dia}</td>
                      <td className="numeric">{formatMoney(item.total)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <>
              <div className="chart-container">
                <ResponsiveContainer width="100%" height="100%" minWidth={0}>
                  <BarChart
                    data={chartData}
                    margin={{ top: 12, right: 8, left: 0, bottom: 0 }}
                    accessibilityLayer
                  >
                    <CartesianGrid vertical={false} stroke="var(--border-color)" />
                    <XAxis
                      dataKey="dia"
                      tick={{ fill: 'var(--text-secondary)', fontSize: 11 }}
                      tickLine={false}
                      axisLine={false}
                      interval="preserveStartEnd"
                      minTickGap={12}
                    />
                    <YAxis
                      width={44}
                      tick={{ fill: 'var(--text-secondary)', fontSize: 11 }}
                      tickLine={false}
                      axisLine={false}
                      tickFormatter={(value) =>
                        new Intl.NumberFormat('pt-MZ', {
                          notation: 'compact',
                          maximumFractionDigits: 1,
                        }).format(value)
                      }
                    />
                    <Tooltip
                      cursor={{ fill: 'var(--bg-subtle)' }}
                      contentStyle={{
                        background: 'var(--bg-card)',
                        border: '1px solid var(--input-border)',
                        borderRadius: 8,
                        color: 'var(--text-primary)',
                        fontSize: 13,
                      }}
                      itemStyle={{ color: 'var(--text-primary)' }}
                      formatter={(value) => [formatMoney(String(value)), 'Vendas']}
                      labelFormatter={(label) => `Dia ${label}`}
                    />
                    <Bar
                      dataKey="total"
                      name="Vendas"
                      fill="var(--chart-color)"
                      maxBarSize={22}
                      radius={[4, 4, 0, 0]}
                      isAnimationActive={false}
                    />
                  </BarChart>
                </ResponsiveContainer>
              </div>
              {chartData.every((item) => item.total === 0) && (
                <p className="chart-note">Ainda não há vendas registadas neste período.</p>
              )}
            </>
          )}
        </section>

        <section className="card dashboard-alerts" aria-labelledby="stock-title">
          <div className="section-header">
            <div>
              <h3 id="stock-title" className="section-title-with-icon">
                <AlertTriangle size={18} strokeWidth={2} aria-hidden="true" /> Atenção ao stock
              </h3>
              <p>Produtos no mínimo ou abaixo</p>
            </div>
          </div>

          {alertasStock.length > 0 ? (
            <>
              <ul className="alert-list">
                {alertasStock.slice(0, 5).map((p) => {
                  const qtdStock = Number(p.stock) || 0;
                  const esgotado = qtdStock <= 0;
                  return (
                    <li key={p.id}>
                      <strong>{p.nome}</strong>
                      <span className={`badge ${esgotado ? 'badge-danger' : 'badge-warning'}`}>
                        <AlertTriangle size={12} strokeWidth={2} aria-hidden="true" />{' '}
                        {esgotado ? 'Esgotado (0)' : `${formatQuantity(p.stock, p.unidade || '')} (Ruptura iminente)`}
                      </span>
                      <small>Mínimo definido: {formatQuantity(p.stockMinimo, p.unidade || '')}</small>
                    </li>
                  );
                })}
              </ul>
              <button className="btn-secondary stock-action" onClick={() => navigate('produtos')}>
                Consultar stock
                {alertasStock.length > 5 ? ` (${alertasStock.length} alertas)` : ''}
              </button>
            </>
          ) : (
            <div className="stock-ok">
              <span className="stock-ok-icon" aria-hidden="true">
                <CheckCircle2 size={32} strokeWidth={2} />
              </span>
              <h4>Sem alertas de stock</h4>
              <p>
                {metrics[0].value === 0
                  ? 'Os níveis de stock aparecerão aqui depois de adicionar produtos.'
                  : 'Todos os produtos estão acima do stock mínimo.'}
              </p>
              <button className="btn-secondary" onClick={() => navigate('produtos')}>
                <Package size={15} strokeWidth={2} aria-hidden="true" /> Ver produtos
              </button>
            </div>
          )}
        </section>
      </div>

      {Object.keys(vendasPorMetodo).length > 0 && (
        <section className="card" style={{ marginTop: 24 }} aria-labelledby="payments-title">
          <div className="section-header">
            <div>
              <h3 id="payments-title" className="section-title-with-icon">
                <CreditCard size={18} strokeWidth={2} aria-hidden="true" /> Vendas por Forma de
                Pagamento (Hoje)
              </h3>
              <p>Valores recebidos por canal</p>
            </div>
          </div>
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(130px, 1fr))',
              gap: 12,
            }}
          >
            {Object.entries(vendasPorMetodo).map(([metodo, valor]) => (
              <div
                key={metodo}
                className="card"
                style={{ padding: 12, textAlign: 'center', background: 'var(--bg-muted, #f8fafc)' }}
              >
                <span
                  style={{
                    fontSize: 12,
                    color: 'var(--text-muted, #666)',
                    textTransform: 'uppercase',
                  }}
                >
                  {metodo}
                </span>
                <strong style={{ display: 'block', fontSize: 16, marginTop: 4 }}>
                  {formatMoney(valor)}
                </strong>
              </div>
            ))}
          </div>
        </section>
      )}

      {produtosMaisVendidos.length > 0 && (
        <section className="card" style={{ marginTop: 24 }} aria-labelledby="top-products-title">
          <div className="section-header">
            <div>
              <h3 id="top-products-title" className="section-title-with-icon">
                <Trophy size={18} strokeWidth={2} aria-hidden="true" /> Produtos Mais Vendidos
              </h3>
              <p>Top produtos com maior saída</p>
            </div>
          </div>
          <div className="table-wrapper">
            <table className="responsive-table">
              <thead>
                <tr>
                  <th>Produto</th>
                  <th className="numeric">Quantidade Vendida</th>
                  <th className="numeric">Total Faturado</th>
                </tr>
              </thead>
              <tbody>
                {produtosMaisVendidos.map((p) => (
                  <tr key={p.produtoId}>
                    <td className="cell-name">
                      <strong>{p.nome}</strong>
                    </td>
                    <td className="numeric font-mono">
                      {formatQuantity(p.quantidadeTotal || p.quantidade || '0', '')}
                    </td>
                    <td className="numeric font-mono" style={{ fontWeight: 600 }}>
                      {formatMoney(p.valorTotal || p.total || '0')}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}

      <section
        className="card recent-sales"
        aria-labelledby="recent-title"
        style={{ marginTop: 24 }}
      >
        <div className="section-header">
          <div>
            <h3 id="recent-title" className="section-title-with-icon">
              <Receipt size={18} strokeWidth={2} aria-hidden="true" /> Vendas recentes
            </h3>
            <p>Os últimos movimentos da loja</p>
          </div>
          <button className="btn-secondary" onClick={() => navigate('vendas')}>
            <ShoppingCart size={15} strokeWidth={2} aria-hidden="true" /> Ir para vendas
          </button>
        </div>

        {vendasRecentes.length === 0 ? (
          <EmptyState
            title="Ainda não há vendas"
            description="As vendas registadas aparecerão aqui, com produto, quantidade e valor."
            icon={<Receipt size={36} strokeWidth={1.8} aria-hidden="true" />}
          />
        ) : (
          <div className="table-wrapper">
            <table className="responsive-table">
              <caption className="sr-only">Vendas recentes da loja</caption>
              <thead>
                <tr>
                  <th scope="col">Produto</th>
                  <th scope="col" className="numeric">
                    Quantidade
                  </th>
                  <th scope="col" className="numeric">
                    Total
                  </th>
                  <th scope="col">Data</th>
                </tr>
              </thead>
              <tbody>
                {vendasRecentes.map((v) => (
                  <tr key={v.id}>
                    <td className="cell-name" data-label="Produto">
                      {v.produto}
                    </td>
                    <td className="numeric" data-label="Quantidade">
                      {formatQuantity(v.quantidade, '')}
                    </td>
                    <td className="numeric" data-label="Total">
                      {formatMoney(v.total)}
                    </td>
                    <td className="cell-secondary" data-label="Data">
                      {v.data}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
