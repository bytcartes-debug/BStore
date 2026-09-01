import React, { useEffect, useState } from 'react';
import { Plus, Pencil, Trash2, X, AlertTriangle } from 'lucide-react';

interface Categoria { id: number; nome: string; }
interface Produto { id: number; nome: string; preco: number; stock: number; stockMinimo: number; categoriaId: number; categoriaNome?: string; }

const ProdutosPage: React.FC = () => {
  const [produtos, setProdutos] = useState<Produto[]>([]);
  const [categorias, setCategorias] = useState<Categoria[]>([]);
  const [search, setSearch] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [editing, setEditing] = useState<Produto | null>(null);
  const [form, setForm] = useState({ nome: '', preco: '', stock: '', stockMinimo: '5', categoriaId: '' });

  const load = () => {
    fetch('/api/produtos').then(r => r.json()).then(setProdutos).catch(() => {});
    fetch('/api/categorias').then(r => r.json()).then(setCategorias).catch(() => {});
  };

  useEffect(() => { load(); }, []);

  const filtered = produtos.filter(p => p.nome.toLowerCase().includes(search.toLowerCase()));

  const openNew = () => {
    setEditing(null);
    setForm({ nome: '', preco: '', stock: '', stockMinimo: '5', categoriaId: categorias[0]?.id.toString() || '' });
    setShowModal(true);
  };

  const openEdit = (p: Produto) => {
    setEditing(p);
    setForm({ nome: p.nome, preco: p.preco.toString(), stock: p.stock.toString(), stockMinimo: p.stockMinimo.toString(), categoriaId: p.categoriaId.toString() });
    setShowModal(true);
  };

  const handleSave = async () => {
    if (!form.nome.trim() || !form.preco || !form.categoriaId) return;
    const body = {
      nome: form.nome.trim(),
      preco: parseFloat(form.preco),
      stock: parseInt(form.stock) || 0,
      stockMinimo: parseInt(form.stockMinimo) || 0,
      categoriaId: parseInt(form.categoriaId),
    };
    if (editing) {
      await fetch(`/api/produtos/${editing.id}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
    } else {
      await fetch('/api/produtos', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
    }
    setShowModal(false);
    load();
  };

  const handleDelete = async (id: number) => {
    if (!confirm('Apagar este produto?')) return;
    await fetch(`/api/produtos/${id}`, { method: 'DELETE' });
    load();
  };

  return (
    <div>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 20, flexWrap: 'wrap', gap: 12 }}>
        <div>
          <h2 style={{ fontSize: 22, fontWeight: 700, color: 'var(--text-primary)' }}>Produtos</h2>
          <p style={{ fontSize: 14, color: 'var(--text-secondary)', marginTop: 4 }}>{produtos.length} produto(s) cadastrado(s)</p>
        </div>
        <button className="btn-primary" onClick={openNew}><Plus size={18} /> Novo Produto</button>
      </div>

      {/* Search */}
      <div style={{ marginBottom: 16 }}>
        <input
          style={{ width: '100%', maxWidth: 360, padding: '10px 14px', background: 'var(--bg-card)', border: '1px solid var(--border-color)', borderRadius: 8, color: 'var(--text-primary)', fontSize: 14, outline: 'none' }}
          placeholder="🔍 Pesquisar produto..."
          value={search}
          onChange={e => setSearch(e.target.value)}
        />
      </div>

      {/* Table */}
      <div className="card">
        <div className="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>Produto</th>
                <th>Categoria</th>
                <th>Preço</th>
                <th>Stock</th>
                <th>Status</th>
                <th>Ações</th>
              </tr>
            </thead>
            <tbody>
              {filtered.length === 0
                ? <tr><td colSpan={6} style={{ textAlign: 'center', color: 'var(--text-secondary)', padding: 32 }}>Nenhum produto encontrado.</td></tr>
                : filtered.map(p => (
                  <tr key={p.id}>
                    <td style={{ fontWeight: 600 }}>{p.nome}</td>
                    <td style={{ color: 'var(--text-secondary)' }}>{p.categoriaNome || '—'}</td>
                    <td>MT {p.preco.toFixed(2)}</td>
                    <td>{p.stock}</td>
                    <td>
                      {p.stock <= p.stockMinimo
                        ? <span className="badge badge-warning"><AlertTriangle size={10} style={{ marginRight: 4 }} />Baixo</span>
                        : <span className="badge badge-success">OK</span>
                      }
                    </td>
                    <td>
                      <div style={{ display: 'flex', gap: 6 }}>
                        <button className="icon-btn" onClick={() => openEdit(p)}><Pencil size={14} /></button>
                        <button className="icon-btn delete" onClick={() => handleDelete(p.id)}><Trash2 size={14} /></button>
                      </div>
                    </td>
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
              <h3>{editing ? 'Editar Produto' : 'Novo Produto'}</h3>
              <button className="modal-close-btn" onClick={() => setShowModal(false)}><X size={20} /></button>
            </div>
            <div className="form-group">
              <label>Nome *</label>
              <input value={form.nome} onChange={e => setForm({ ...form, nome: e.target.value })} placeholder="Nome do produto" />
            </div>
            <div className="form-group">
              <label>Categoria *</label>
              <select value={form.categoriaId} onChange={e => setForm({ ...form, categoriaId: e.target.value })}>
                <option value="">Selecionar...</option>
                {categorias.map(c => <option key={c.id} value={c.id}>{c.nome}</option>)}
              </select>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              <div className="form-group">
                <label>Preço de Venda (MT) *</label>
                <input type="number" min="0" step="0.01" value={form.preco} onChange={e => setForm({ ...form, preco: e.target.value })} placeholder="0.00" />
              </div>
              <div className="form-group">
                <label>Stock Atual</label>
                <input type="number" min="0" value={form.stock} onChange={e => setForm({ ...form, stock: e.target.value })} placeholder="0" />
              </div>
              <div className="form-group">
                <label>Stock Mínimo</label>
                <input type="number" min="0" value={form.stockMinimo} onChange={e => setForm({ ...form, stockMinimo: e.target.value })} placeholder="5" />
              </div>
            </div>
            <div className="modal-actions">
              <button className="btn-secondary" onClick={() => setShowModal(false)}>Cancelar</button>
              <button className="btn-primary" onClick={handleSave}>{editing ? 'Guardar' : 'Criar'}</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default ProdutosPage;
