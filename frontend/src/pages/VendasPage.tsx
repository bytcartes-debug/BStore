import React, { useEffect, useState } from 'react';
import { Plus, X } from 'lucide-react';

interface Produto { id: number; nome: string; preco: number; stock: number; }
interface Venda { id: number; produto: string; quantidade: number; total: number; data: string; }

const VendasPage: React.FC = () => {
  const [vendas, setVendas] = useState<Venda[]>([]);
  const [produtos, setProdutos] = useState<Produto[]>([]);
  const [showModal, setShowModal] = useState(false);
  const [form, setForm] = useState({ produtoId: '', quantidade: '1' });
  const [troco, setTroco] = useState<number | null>(null);
  const [valorEntregue, setValorEntregue] = useState('');
  const [selectedProd, setSelectedProd] = useState<Produto | null>(null);

  const load = () => {
    fetch('/api/vendas').then(r => r.json()).then(setVendas).catch(() => {});
    fetch('/api/produtos').then(r => r.json()).then(setProdutos).catch(() => {});
  };

  useEffect(() => { load(); }, []);

  const totalVenda = selectedProd ? selectedProd.preco * (parseInt(form.quantidade) || 1) : 0;

  const handleProdutoChange = (id: string) => {
    const p = produtos.find(x => x.id.toString() === id) || null;
    setSelectedProd(p);
    setForm({ ...form, produtoId: id });
    setTroco(null);
    setValorEntregue('');
  };

  const calcTroco = () => {
    const v = parseFloat(valorEntregue);
    if (!isNaN(v)) setTroco(v - totalVenda);
  };

  const handleSave = async () => {
    if (!form.produtoId || !form.quantidade) return;
    await fetch('/api/vendas', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ produtoId: parseInt(form.produtoId), quantidade: parseInt(form.quantidade) }),
    });
    setShowModal(false);
    setTroco(null);
    setValorEntregue('');
    setSelectedProd(null);
    setForm({ produtoId: '', quantidade: '1' });
    load();
  };

  return (
    <div>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 20, flexWrap: 'wrap', gap: 12 }}>
        <div>
          <h2 style={{ fontSize: 22, fontWeight: 700, color: 'var(--text-primary)' }}>Vendas</h2>
          <p style={{ fontSize: 14, color: 'var(--text-secondary)', marginTop: 4 }}>{vendas.length} venda(s) registada(s)</p>
        </div>
        <button className="btn-primary" onClick={() => setShowModal(true)}><Plus size={18} /> Nova Venda</button>
      </div>

      {/* Table */}
      <div className="card">
        <div className="table-wrapper">
          <table>
            <thead>
              <tr><th>Produto</th><th>Qtd</th><th>Total (MT)</th><th>Data</th></tr>
            </thead>
            <tbody>
              {vendas.length === 0
                ? <tr><td colSpan={4} style={{ textAlign: 'center', color: 'var(--text-secondary)', padding: 32 }}>Nenhuma venda registada.</td></tr>
                : vendas.map(v => (
                  <tr key={v.id}>
                    <td style={{ fontWeight: 600 }}>{v.produto}</td>
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

      {/* Modal */}
      {showModal && (
        <div className="modal-overlay">
          <div className="modal-container">
            <div className="modal-header">
              <h3>Registar Venda</h3>
              <button className="modal-close-btn" onClick={() => setShowModal(false)}><X size={20} /></button>
            </div>
            <div className="form-group">
              <label>Produto *</label>
              <select value={form.produtoId} onChange={e => handleProdutoChange(e.target.value)}>
                <option value="">Selecionar produto...</option>
                {produtos.map(p => <option key={p.id} value={p.id}>{p.nome} — MT {p.preco.toFixed(2)} (Stock: {p.stock})</option>)}
              </select>
            </div>
            <div className="form-group">
              <label>Quantidade *</label>
              <input type="number" min="1" value={form.quantidade} onChange={e => { setForm({ ...form, quantidade: e.target.value }); setTroco(null); }} />
            </div>

            {selectedProd && (
              <div style={{ background: 'var(--color-brand-light)', border: '1px solid var(--color-brand)', borderRadius: 8, padding: 14, marginBottom: 16 }}>
                <p style={{ fontSize: 13, color: 'var(--color-brand)', fontWeight: 600 }}>
                  Total a pagar: <strong>MT {totalVenda.toFixed(2)}</strong>
                </p>
              </div>
            )}

            {/* Calculadora de troco */}
            {selectedProd && (
              <div className="form-group">
                <label>💰 Valor entregue pelo cliente (MT)</label>
                <div style={{ display: 'flex', gap: 8 }}>
                  <input type="number" min="0" step="0.01" value={valorEntregue} onChange={e => { setValorEntregue(e.target.value); setTroco(null); }} placeholder="0.00" style={{ flex: 1 }} />
                  <button className="btn-secondary" onClick={calcTroco} style={{ whiteSpace: 'nowrap' }}>Calcular Troco</button>
                </div>
                {troco !== null && (
                  <div style={{ marginTop: 8, padding: '10px 12px', background: troco >= 0 ? 'var(--color-brand-light)' : 'var(--color-danger-light)', borderRadius: 8, fontSize: 14, fontWeight: 700, color: troco >= 0 ? 'var(--color-brand)' : 'var(--color-danger)' }}>
                    {troco >= 0 ? `✅ Troco: MT ${troco.toFixed(2)}` : `❌ Falta MT ${Math.abs(troco).toFixed(2)}`}
                  </div>
                )}
              </div>
            )}

            <div className="modal-actions">
              <button className="btn-secondary" onClick={() => setShowModal(false)}>Cancelar</button>
              <button className="btn-primary" onClick={handleSave}>✅ Confirmar Venda</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default VendasPage;
