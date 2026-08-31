import React, { useEffect, useState } from 'react';
import { Plus, X, CheckCircle } from 'lucide-react';

interface Devedor { id: number; nome: string; divida: number; descricao: string; data: string; }

const DevedoresPage: React.FC = () => {
  const [devedores, setDevedores] = useState<Devedor[]>([]);
  const [showModal, setShowModal] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [selectedDevedor, setSelectedDevedor] = useState<Devedor | null>(null);
  const [form, setForm] = useState({ nome: '', divida: '', descricao: '' });

  const load = () => fetch('/api/devedores').then(r => r.json()).then(setDevedores).catch(() => {});

  useEffect(() => { load(); }, []);

  const totalDivida = devedores.reduce((s, d) => s + d.divida, 0);

  const handleSave = async () => {
    if (!form.nome.trim() || !form.divida) return;
    await fetch('/api/devedores', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ nome: form.nome.trim(), divida: parseFloat(form.divida), descricao: form.descricao.trim() }),
    });
    setShowModal(false);
    setForm({ nome: '', divida: '', descricao: '' });
    load();
  };

  const confirmarPagamento = (d: Devedor) => {
    setSelectedDevedor(d);
    setShowConfirm(true);
  };

  const handleDarBaixa = async () => {
    if (!selectedDevedor) return;
    await fetch(`/api/devedores/${selectedDevedor.id}`, { method: 'DELETE' });
    setShowConfirm(false);
    setSelectedDevedor(null);
    load();
  };

  return (
    <div>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 20, flexWrap: 'wrap', gap: 12 }}>
        <div>
          <h2 style={{ fontSize: 22, fontWeight: 700, color: 'var(--text-primary)' }}>Devedores</h2>
          <p style={{ fontSize: 14, color: 'var(--text-secondary)', marginTop: 4 }}>
            {devedores.length} devedor(es) · Total em dívida: <strong style={{ color: 'var(--color-danger)' }}>MT {totalDivida.toFixed(2)}</strong>
          </p>
        </div>
        <button className="btn-primary" onClick={() => setShowModal(true)}><Plus size={18} /> Adicionar Devedor</button>
      </div>

      {/* Table */}
      <div className="card">
        <div className="table-wrapper">
          <table>
            <thead>
              <tr><th>Nome</th><th>Dívida (MT)</th><th>Descrição</th><th>Data</th><th>Ação</th></tr>
            </thead>
            <tbody>
              {devedores.length === 0
                ? <tr><td colSpan={5} style={{ textAlign: 'center', color: 'var(--text-secondary)', padding: 32 }}>Nenhum devedor cadastrado.</td></tr>
                : devedores.map(d => (
                  <tr key={d.id}>
                    <td style={{ fontWeight: 600 }}>{d.nome}</td>
                    <td style={{ color: 'var(--color-danger)', fontWeight: 700 }}>MT {d.divida.toFixed(2)}</td>
                    <td style={{ color: 'var(--text-secondary)' }}>{d.descricao || '—'}</td>
                    <td style={{ color: 'var(--text-secondary)' }}>{d.data}</td>
                    <td>
                      <button
                        onClick={() => confirmarPagamento(d)}
                        style={{
                          background: 'var(--color-brand)', color: 'white', border: 'none',
                          borderRadius: 8, padding: '8px 14px', fontSize: 13, fontWeight: 700,
                          cursor: 'pointer', display: 'inline-flex', alignItems: 'center', gap: 6
                        }}
                      >
                        <CheckCircle size={15} /> Pago
                      </button>
                    </td>
                  </tr>
                ))
              }
            </tbody>
          </table>
        </div>
      </div>

      {/* Modal — Novo Devedor */}
      {showModal && (
        <div className="modal-overlay">
          <div className="modal-container">
            <div className="modal-header">
              <h3>Adicionar Devedor</h3>
              <button className="modal-close-btn" onClick={() => setShowModal(false)}><X size={20} /></button>
            </div>
            <div className="form-group">
              <label>Nome *</label>
              <input value={form.nome} onChange={e => setForm({ ...form, nome: e.target.value })} placeholder="Nome do devedor" />
            </div>
            <div className="form-group">
              <label>Valor da Dívida (MT) *</label>
              <input type="number" min="0" step="0.01" value={form.divida} onChange={e => setForm({ ...form, divida: e.target.value })} placeholder="0.00" />
            </div>
            <div className="form-group">
              <label>Descrição (opcional)</label>
              <input value={form.descricao} onChange={e => setForm({ ...form, descricao: e.target.value })} placeholder="Ex: Crédito de cerveja" />
            </div>
            <div className="modal-actions">
              <button className="btn-secondary" onClick={() => setShowModal(false)}>Cancelar</button>
              <button className="btn-primary" onClick={handleSave}>Adicionar</button>
            </div>
          </div>
        </div>
      )}

      {/* Modal — Confirmar Pagamento */}
      {showConfirm && selectedDevedor && (
        <div className="modal-overlay">
          <div className="modal-container" style={{ maxWidth: 380, textAlign: 'center' }}>
            <div style={{ fontSize: 48, marginBottom: 12 }}>💰</div>
            <h3 style={{ fontSize: 20, fontWeight: 700, color: 'var(--text-primary)', marginBottom: 8 }}>
              {selectedDevedor.nome} pagou?
            </h3>
            <p style={{ fontSize: 15, color: 'var(--text-secondary)', marginBottom: 24 }}>
              Dívida de <strong style={{ color: 'var(--color-danger)' }}>MT {selectedDevedor.divida.toFixed(2)}</strong> será quitada.
            </p>
            <div style={{ display: 'flex', gap: 12, justifyContent: 'center' }}>
              <button className="btn-secondary" onClick={() => setShowConfirm(false)}>❌ Não</button>
              <button
                className="btn-primary"
                onClick={handleDarBaixa}
                style={{ fontSize: 16, padding: '12px 24px' }}
              >
                ✅ Sim, Pagou!
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default DevedoresPage;
