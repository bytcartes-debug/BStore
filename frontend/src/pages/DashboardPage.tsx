import React, { useEffect, useState } from 'react';
import { TrendingUp, Package, ShoppingCart, Users, AlertTriangle } from 'lucide-react';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import './DashboardPage.css';

interface DashboardData {
  totalVendasHoje: number;
  totalProdutos: number;
  totalCategorias: number;
  totalDevedores: number;
  alertasStock: { id: number; nome: string; stock: number; stockMinimo: number }[];
  vendasRecentes: { id: number; produto: string; quantidade: number; total: number; data: string }[];
  vendasPorDia: { dia: string; total: number }[];
}

const DashboardPage: React.FC = () => {
  const [data, setData] = useState<DashboardData | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetch('/api/dashboard')
      .then(r => r.json())
      .then(d => { setData(d); setLoading(false); })
      .catch(() => setLoading(false));
  }, []);

  if (loading) return <div style={{ color: 'var(--text-secondary)', padding: 40 }}>A carregar...</div>;
  if (!data)   return <div style={{ color: 'var(--color-danger)', padding: 40 }}>Erro ao carregar dados.</div>;

  const cards = [
    { label: 'Vendas Hoje', value: `MT ${data.totalVendasHoje.toFixed(2)}`, icon: <TrendingUp size={22} />, color: 'var(--color-brand)' },
    { label: 'Produtos',    value: data.totalProdutos,    icon: <Package size={22} />,     color: 'var(--color-info)' },
    { label: 'Vendas',      value: data.totalCategorias,  icon: <ShoppingCart size={22} />, color: 'var(--color-warning)' },
    { label: 'Devedores',   value: data.totalDevedores,   icon: <Users size={22} />,        color: 'var(--color-danger)' },
  ];

  return (
    <div className="dashboard-page">
      {/* KPI Cards */}
      <div className="kpi-grid">
        {cards.map(c => (
          <div className="card kpi-card" key={c.label}>
            <div className="kpi-icon" style={{ backgroundColor: c.color + '20', color: c.color }}>{c.icon}</div>
            <div>
              <p className="kpi-label">{c.label}</p>
              <p className="kpi-value">{c.value}</p>
            </div>
          </div>
        ))}
      </div>

      <div className="dashboard-grid">
        {/* Chart */}
        <div className="card dashboard-chart">
          <div className="section-header">
            <h3>Vendas dos Últimos 7 Dias</h3>
          </div>
          <ResponsiveContainer width="100%" height={220}>
            <BarChart data={data.vendasPorDia} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="var(--border-color)" />
              <XAxis dataKey="dia" tick={{ fill: 'var(--text-secondary)', fontSize: 12 }} />
              <YAxis tick={{ fill: 'var(--text-secondary)', fontSize: 12 }} />
              <Tooltip
                contentStyle={{ backgroundColor: 'var(--bg-card)', border: '1px solid var(--border-color)', borderRadius: 8 }}
                labelStyle={{ color: 'var(--text-primary)' }}
              />
              <Bar dataKey="total" fill="var(--color-brand)" radius={[4,4,0,0]} name="MT" />
            </BarChart>
          </ResponsiveContainer>
        </div>

        {/* Alertas de Stock */}
        {data.alertasStock.length > 0 && (
          <div className="card dashboard-alerts">
            <div className="section-header">
              <h3><AlertTriangle size={16} style={{ color: 'var(--color-warning)', marginRight: 6 }} />Alertas de Stock</h3>
            </div>
            <ul className="alert-list">
              {data.alertasStock.map(p => (
                <li key={p.id} className="alert-item">
                  <span className="alert-name">{p.nome}</span>
                  <span className="badge badge-warning">{p.stock} / {p.stockMinimo} mín.</span>
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>

      {/* Vendas Recentes */}
      <div className="card" style={{ marginTop: 24 }}>
        <div className="section-header" style={{ padding: '16px 20px' }}>
          <h3>Vendas Recentes</h3>
        </div>
        <div className="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>Produto</th>
                <th>Qtd</th>
                <th>Total</th>
                <th>Data</th>
              </tr>
            </thead>
            <tbody>
              {data.vendasRecentes.length === 0
                ? <tr><td colSpan={4} style={{ textAlign: 'center', color: 'var(--text-secondary)', padding: 24 }}>Nenhuma venda registada.</td></tr>
                : data.vendasRecentes.map(v => (
                  <tr key={v.id}>
                    <td>{v.produto}</td>
                    <td>{v.quantidade}</td>
                    <td>MT {v.total.toFixed(2)}</td>
                    <td style={{ color: 'var(--text-secondary)' }}>{v.data}</td>
                  </tr>
                ))
              }
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};

export default DashboardPage;
