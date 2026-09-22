import { apiFetch } from '../utils/api';
import React, { useEffect, useState } from 'react';
import { Plus, Pencil, Trash2, X } from 'lucide-react';

const UNIDADES = ['un', 'kg', 'g', 'L', 'ml', 'cx', 'pct', 'dz', 'm', 'par'];

interface Categoria { id: number; nome: string; }
interface Produto   { id: number; nome: string; preco: number; stock: number; stockMinimo: number; unidade: string; categoriaId: number; categoriaNome?: string; }

const fmt = (n: number, u: string) =>
  Number.isInteger(n) ? `${n} ${u}` : `${n.toFixed(3).replace(/\.?0+$/, '')} ${u}`;

const ProdutosPage: React.FC = () => {
  const [produtos, setProdutos]   = useState<Produto[]>([]);
  const [categorias, setCategorias] = useState<Categoria[]>([]);
  const [search, setSearch]       = useState('');
  const [showModal, setShowModal] = useState(false);
  const [editing, setEditing]     = useState<Produto | null>(null);
  const [form, setForm]           = useState({ nome: '', preco: '', stock: '', stockMinimo: '5', unidade: 'un', categoriaId: '' });

  const load = () => {
    apiFetch('/api/produtos').then(r => r.json()).then(setProdutos).catch(() => {});
    apiFetch('/api/categorias').then(r => r.json()).then(setCategorias).catch(() => {});
  };

  useEffect(() => { load(); }, []);

  const filtered = produtos.filter(p => p.nome.toLowerCase().includes(search.toLowerCase()));

  const openNew = () => {
    setEditing(null);
    setForm({ nome:'', preco:'', stock:'', stockMinimo:'5', unidade:'un', categoriaId: categorias[0]?.id.toString() || '' });
    setShowModal(true);
  };

  const openEdit = (p: Produto) => {
    setEditing(p);
    setForm({ nome: p.nome, preco: p.preco.toString(), stock: p.stock.toString(), stockMinimo: p.stockMinimo.toString(), unidade: p.unidade || 'un', categoriaId: p.categoriaId.toString() });
    setShowModal(true);
  };

  const handleSave = async () => {
    if (!form.nome.trim() || !form.preco || !form.categoriaId) return;
    const body = {
      nome:        form.nome.trim(),
      preco:       parseFloat(form.preco),
      stock:       parseFloat(form.stock) || 0,
      stockMinimo: parseFloat(form.stockMinimo) || 0,
      unidade:     form.unidade || 'un',
      categoriaId: parseInt(form.categoriaId),
    };
    if (editing) {
      await apiFetch(`/api/produtos/${editing.id}`, { method:'PUT', body: JSON.stringify(body) });
    } else {
      await apiFetch('/api/produtos', { method:'POST', body: JSON.stringify(body) });
    }
    setShowModal(false);
    load();
  };

  const handleDelete = async (id: number) => {
    if (!confirm('Apagar este produto?')) return;
    await apiFetch(`/api/produtos/${id}`, { method:'DELETE' });
    load();
  };

  return (
    <div>
      {/* Header */}
      <div style={{ display:'flex', justifyContent:'space-between', alignItems:'flex-start', marginBottom:20, flexWrap:'wrap', gap:12 }}>
        <div>
          <h2 style={{ fontSize:22, fontWeight:700, color:'var(--text-primary)' }}>Produtos</h2>
          <p style={{ fontSize:14, color:'var(--text-secondary)', marginTop:4 }}>{produtos.length} produto(s) cadastrado(s)</p>
        </div>
        <button className="btn-primary" onClick={openNew}><Plus size={18} /> Novo Produto</button>
      </div>

      {/* Search */}
      <div style={{ marginBottom:16 }}>
        <input
          style={{ width:'100%', maxWidth:360, padding:'10px 14px', background:'var(--bg-card)', border:'1px solid var(--border-color)', borderRadius:8, color:'var(--text-primary)', fontSize:14, outline:'none' }}
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
              <tr><th>Nome</th><th>Preço (MT)</th><th>Stock</th><th>Stock Mín.</th><th>Categoria</th><th>Ações</th></tr>
            </thead>
            <tbody>
              {filtered.length === 0
                ? <tr><td colSpan={6} style={{ textAlign:'center', color:'var(--text-secondary)', padding:32 }}>Nenhum produto.</td></tr>
                : filtered.map(p => (
                  <tr key={p.id}>
                    <td style={{ fontWeight:600 }}>{p.nome}</td>
                    <td>MT {p.preco.toFixed(2)}</td>
                    <td style={{ color: p.stock <= p.stockMinimo ? 'var(--color-danger)' : 'var(--text-primary)', fontWeight: p.stock <= p.stockMinimo ? 700 : 400 }}>
                      {fmt(p.stock, p.unidade || 'un')}
                    </td>
                    <td style={{ color:'var(--text-secondary)' }}>{fmt(p.stockMinimo, p.unidade || 'un')}</td>
                    <td style={{ color:'var(--text-secondary)' }}>{p.categoriaNome}</td>
                    <td style={{ display:'flex', gap:6 }}>
                      <button className="icon-btn" onClick={() => openEdit(p)}><Pencil size={14} /></button>
                      <button className="icon-btn delete" onClick={() => handleDelete(p.id)}><Trash2 size={14} /></button>
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

            <div className="form-group"><label>Nome *</label><input value={form.nome} onChange={e => setForm({...form, nome:e.target.value})} placeholder="Ex: Arroz 5kg" /></div>
            <div className="form-group"><label>Preço (MT) *</label><input type="number" min="0" step="0.01" value={form.preco} onChange={e => setForm({...form, preco:e.target.value})} placeholder="0.00" /></div>

            <div style={{ display:'grid', gridTemplateColumns:'1fr 1fr', gap:12 }}>
              <div className="form-group">
                <label>Stock actual</label>
                <input type="number" min="0" step="0.001" value={form.stock} onChange={e => setForm({...form, stock:e.target.value})} placeholder="0" />
              </div>
              <div className="form-group">
                <label>Stock mínimo</label>
                <input type="number" min="0" step="0.001" value={form.stockMinimo} onChange={e => setForm({...form, stockMinimo:e.target.value})} placeholder="5" />
              </div>
            </div>

            <div className="form-group">
              <label>Unidade de medida</label>
              <div style={{ display:'flex', flexWrap:'wrap', gap:6 }}>
                {UNIDADES.map(u => (
                  <button
                    key={u} type="button"
                    onClick={() => setForm({...form, unidade:u})}
                    style={{
                      padding:'6px 14px', borderRadius:6, border:'1px solid',
                      borderColor: form.unidade === u ? 'var(--color-primary)' : 'var(--border-color)',
                      background:  form.unidade === u ? 'var(--color-primary)' : 'transparent',
                      color:       form.unidade === u ? '#fff' : 'var(--text-primary)',
                      fontWeight:  form.unidade === u ? 700 : 400,
                      cursor:'pointer', fontSize:13,
                    }}
                  >{u}</button>
                ))}
              </div>
            </div>

            <div className="form-group">
              <label>Categoria *</label>
              <select value={form.categoriaId} onChange={e => setForm({...form, categoriaId:e.target.value})}>
                <option value="">-- Selecionar --</option>
                {categorias.map(c => <option key={c.id} value={c.id}>{c.nome}</option>)}
              </select>
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
