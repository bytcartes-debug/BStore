import { useState } from 'react';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { apiRequest } from '../utils/api';
import { useResource } from '../utils/useResource';
import { decimal, formatMoney, formatQuantity } from '../utils/decimal';
import { EmptyState, Loading, LoadError, PageHeading } from '../components/UI';
import type { PageId } from '../App';
import './DashboardPage.css';

interface DashboardData {
  totalVendasHoje: string;
  totalProdutos: number;
  totalCategorias: number;
  totalDevedores: number;
  alertasStock: {
    id: number;
    nome: string;
    stock: string;
    stockMinimo: string;
    unidade?: string;
  }[];
  vendasRecentes: {
    id: number;
    produto: string;
    quantidade: string;
    total: string;
    data: string;
  }[];
  vendasPorDia: { dia: string; total: string }[];
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

  const chartData = data.vendasPorDia.map((item) => ({
    ...item,
    total: decimal(item.total).toNumber(),
  }));

  const metrics = [
    {
      label: 'Produtos no Catálogo',
      value: data.totalProdutos,
      emoji: '📦',
      page: 'produtos' as const,
    },
    {
      label: 'Categorias Ativas',
      value: data.totalCategorias,
      emoji: '🏷️',
      page: 'categorias' as const,
    },
    {
      label: 'Total de Devedores',
      value: data.totalDevedores,
      emoji: '👥',
      page: 'devedores' as const,
    },
  ];

  return (
    <div className="dashboard-page">
      <PageHeading title="A sua loja, hoje" description={today}>
        <button className="btn-primary" onClick={() => navigate('vendas')}>
          <span aria-hidden="true">🛒</span> Nova Venda
        </button>
      </PageHeading>

      {error && <LoadError message={error} retry={reload} />}

      <section className="store-overview" aria-label="Resumo da loja">
        <div className="today-sales">
          <span className="today-label">
            <span aria-hidden="true">💰</span> Vendas de Hoje
          </span>
          <p className="today-value">{formatMoney(data.totalVendasHoje)}</p>
          <span className="today-description">Total registado no dia</span>
        </div>

        <div className="store-metrics">
          {metrics.map(({ label, value, emoji, page }) => (
            <button key={label} className="store-metric" onClick={() => navigate(page)}>
              <span className="metric-label">
                <span className="metric-emoji" aria-hidden="true">
                  {emoji}
                </span>
                {label}
              </span>
              <span className="metric-value">
                {value}
                <span className="metric-arrow" aria-hidden="true">
                  ↗️
                </span>
              </span>
            </button>
          ))}
        </div>
      </section>

      <div className="dashboard-grid">
        <section className="card dashboard-chart" aria-labelledby="sales-chart-title">
          <div className="section-header">
            <div>
              <h3 id="sales-chart-title">📈 Vendas dos últimos 7 dias</h3>
              <p>Total diário em meticais (MT)</p>
            </div>
            <button
              className="btn-secondary chart-toggle"
              aria-pressed={tableView}
              onClick={() => setTableView((value) => !value)}
            >
              {tableView ? '📊 Ver gráfico' : '📋 Ver tabela'}
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
                  {data.vendasPorDia.map((item) => (
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
              <h3 id="stock-title">⚠️ Atenção ao stock</h3>
              <p>Produtos no mínimo ou abaixo</p>
            </div>
          </div>

          {data.alertasStock.length > 0 ? (
            <>
              <ul className="alert-list">
                {data.alertasStock.slice(0, 5).map((p) => (
                  <li key={p.id}>
                    <strong>{p.nome}</strong>
                    <span className="badge badge-warning">
                      ⚠️ {formatQuantity(p.stock, p.unidade || '')} disponível
                    </span>
                    <small>Mínimo: {formatQuantity(p.stockMinimo, p.unidade || '')}</small>
                  </li>
                ))}
              </ul>
              <button className="btn-secondary stock-action" onClick={() => navigate('produtos')}>
                Consultar stock
                {data.alertasStock.length > 5 ? ` (${data.alertasStock.length} alertas)` : ''}
              </button>
            </>
          ) : (
            <div className="stock-ok">
              <span className="stock-ok-emoji" aria-hidden="true">
                ✅
              </span>
              <h4>Sem alertas de stock</h4>
              <p>
                {data.totalProdutos === 0
                  ? 'Os níveis de stock aparecerão aqui depois de adicionar produtos.'
                  : 'Todos os produtos estão acima do stock mínimo.'}
              </p>
              <button className="btn-secondary" onClick={() => navigate('produtos')}>
                📦 Ver produtos
              </button>
            </div>
          )}
        </section>
      </div>

      <section className="card recent-sales" aria-labelledby="recent-title">
        <div className="section-header">
          <div>
            <h3 id="recent-title">🧾 Vendas recentes</h3>
            <p>Os últimos movimentos da loja</p>
          </div>
          <button className="btn-secondary" onClick={() => navigate('vendas')}>
            🛒 Ir para vendas
          </button>
        </div>

        {data.vendasRecentes.length === 0 ? (
          <EmptyState
            title="Ainda não há vendas"
            description="As vendas registadas aparecerão aqui, com produto, quantidade e valor."
            icon="🧾"
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
                {data.vendasRecentes.map((v) => (
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
