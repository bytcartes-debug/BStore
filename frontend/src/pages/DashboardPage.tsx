import { useState } from 'react';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import {
  AlertTriangle,
  ArrowUpRight,
  Banknote,
  BarChart3,
  CheckCircle2,
  CreditCard,
  FileText,
  Package,
  Receipt,
  ShoppingCart,
  Sparkles,
  Tag,
  TrendingDown,
  TrendingUp,
  Trophy,
  Users,
} from 'lucide-react';
import { apiRequest } from '../utils/api';
import { useResource } from '../utils/useResource';
import { decimalSeguro, formatMoney, formatQuantity } from '../utils/decimal';
import { EmptyState, Loading, LoadError, PageHeading } from '../components/UI';
import type { PageId } from '../App';
import './DashboardPage.css';

interface DashboardData {
  totalVendasHoje?: string;
  lucroHoje?: string;
  lucroUltimos7Dias?: string;
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
  vendasPorDia?: { dia: string; total: string }[];
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

const loadDashboard = (signal: AbortSignal) =>
  apiRequest<DashboardData>('/api/dashboard', { signal });

export default function DashboardPage({ navigate }: { navigate: (page: PageId) => void }) {
  const { data, loading, error, reload } = useResource(loadDashboard);
  const [tableView, setTableView] = useState(false);

  const today = new Intl.DateTimeFormat('pt-MZ', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  }).format(new Date());

  if (loading && !data) return <Loading label="A preparar a visão geral da loja…" />;
  if (!data)
    return (
      <LoadError message={error || 'Não foi possível carregar a visão geral.'} retry={reload} />
    );

  const vendasPorDia = Array.isArray(data.vendasPorDia) ? data.vendasPorDia : [];
  const alertasStock = Array.isArray(data.alertasStock) ? data.alertasStock : [];
  const vendasRecentes = Array.isArray(data.vendasRecentes) ? data.vendasRecentes : [];
  const produtosMaisVendidos = Array.isArray(data.produtosMaisVendidos) ? data.produtosMaisVendidos : [];
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
        <button className="btn-primary" onClick={() => navigate('vendas')}>
          <ShoppingCart size={16} strokeWidth={2.2} aria-hidden="true" /> Nova Venda
        </button>
      </PageHeading>

      {error && <LoadError message={error} retry={reload} />}

      <section className="store-overview" aria-label="Resumo da loja">
        <div className="today-sales">
          <span className="today-label">
            <Banknote size={17} strokeWidth={2} aria-hidden="true" /> Vendas de Hoje
          </span>
          <p className="today-value">{formatMoney(data.totalVendasHoje)}</p>
          <span className="today-description">Total registado no dia</span>
        </div>

        {data.lucroHoje !== undefined && (
          <div className="today-sales">
            <span className="today-label">
              <Sparkles size={17} strokeWidth={2} aria-hidden="true" /> Lucro Estimado de Hoje
            </span>
            <p className="today-value">{formatMoney(data.lucroHoje)}</p>
            <span className="today-description">
              {data.lucroUltimos7Dias ? `7 dias: ${formatMoney(data.lucroUltimos7Dias)}` : 'Vendas menos custo'}
            </span>
            <small style={{ display: 'block', fontSize: '0.72rem', color: 'var(--text-muted)', marginTop: '4px' }}>
              Vendas antigas sem custo não entram no lucro
            </small>
          </div>
        )}

        {data.valorTotalStockCusto !== undefined && (
          <div className="today-sales">
            <span className="today-label">
              <Package size={17} strokeWidth={2} aria-hidden="true" /> Valor do Stock a Custo
            </span>
            <p className="today-value">{formatMoney(data.valorTotalStockCusto)}</p>
            <span className="today-description">Capital retido no inventário</span>
          </div>
        )}

        {data.perdasMes !== undefined && (
          <div className="today-sales">
            <span className="today-label">
              <TrendingDown size={17} strokeWidth={2} aria-hidden="true" /> Perdas no Mês
            </span>
            <p className="today-value">{formatMoney(data.perdasMes)}</p>
            <span className="today-description">Ajustes a custo este mês</span>
          </div>
        )}

        <div className="store-metrics">
          {metrics.map(({ label, value, icon: Icon, page }) => (
            <button key={label} className="store-metric" onClick={() => navigate(page)}>
              <span className="metric-label">
                <span className="metric-emoji" aria-hidden="true">
                  <Icon size={16} strokeWidth={2} />
                </span>
                {label}
              </span>
              <span className="metric-value">
                {value}
                <ArrowUpRight size={15} strokeWidth={2} className="metric-arrow" aria-hidden="true" />
              </span>
            </button>
          ))}
        </div>
      </section>

      <div className="dashboard-grid">
        <section className="card dashboard-chart" aria-labelledby="sales-chart-title">
          <div className="section-header">
            <div>
              <h3 id="sales-chart-title" className="section-title-with-icon">
                <TrendingUp size={18} strokeWidth={2} aria-hidden="true" /> Vendas dos últimos 7 dias
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
                {alertasStock.slice(0, 5).map((p) => (
                  <li key={p.id}>
                    <strong>{p.nome}</strong>
                    <span className="badge badge-warning">
                      <AlertTriangle size={12} strokeWidth={2} aria-hidden="true" />{' '}
                      {formatQuantity(p.stock, p.unidade || '')} disponível
                    </span>
                    <small>Mínimo: {formatQuantity(p.stockMinimo, p.unidade || '')}</small>
                  </li>
                ))}
              </ul>
              <button className="btn-secondary stock-action" onClick={() => navigate('produtos')}>
                Consultar stock
                {alertasStock.length > 5 ? ` (${alertasStock.length} alertas)` : ''}
              </button>
            </>
          ) : (
            <div className="stock-ok">
              <span className="stock-ok-emoji" aria-hidden="true">
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
                <CreditCard size={18} strokeWidth={2} aria-hidden="true" /> Vendas por Forma de Pagamento (Hoje)
              </h3>
              <p>Valores recebidos por canal</p>
            </div>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(130px, 1fr))', gap: 12 }}>
            {Object.entries(vendasPorMetodo).map(([metodo, valor]) => (
              <div key={metodo} className="card" style={{ padding: 12, textAlign: 'center', background: 'var(--bg-muted, #f8fafc)' }}>
                <span style={{ fontSize: 12, color: 'var(--text-muted, #666)', textTransform: 'uppercase' }}>
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
                    <td className="numeric font-mono">{formatQuantity(p.quantidadeTotal || p.quantidade || '0', '')}</td>
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

      <section className="card recent-sales" aria-labelledby="recent-title" style={{ marginTop: 24 }}>
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
