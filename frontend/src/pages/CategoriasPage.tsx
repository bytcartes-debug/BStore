import { apiFetch } from '../utils/api';
import React, { useEffect, useState } from 'react';
import { Plus, Pencil, Trash2, X } from 'lucide-react';

const EMOJIS = ['🛍️','🏷️','🍎','🥤','🧴','📦','🍞','🥩','🧀','🥦','🍺','☕','🧹','🪣','💊','👕','👟','📱','🔧','💡','🐔','🥚','🌽','🫙','🧂','🫒','🍫','🍬'];

interface Categoria { id: number; nome: string; descricao: string; icone: string; totalProdutos?: number; }

const CategoriasPage: React.FC = () => {
  const [categorias, setCategorias] = useState<Categoria[]>([]);
  const [showModal, setShowModal] = useState(false);
  const [editing, setEditing] = useState<Categoria | null>(null);
  const [form, setForm] = useState({ nome: '', descricao: '', icone: '🏷️' });
  const [loading, setLoading] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  const load = async () => {
    try {
      const r = await apiFetch('/api/categorias');
      if (!r.ok) throw new Error('Erro ao carregar categorias');
      setCategorias(await r.json());
    } catch (e: any) {
      setErro('Não foi possível ligar ao servidor. Aguarde e tente novamente.');
    }
  };

  useEffect(() => { load(); }, []);

  const openNew = () => { setEditing(null); setForm({ nome: '', descricao: '', icone: '🏷️' }); setErro(null); setShowModal(true); };
  const openEdit = (c: Categoria) => { setEditing(c); setForm({ nome: c.nome, descricao: c.descricao || '', icone: c.icone || '🏷️' }); setErro(null); setShowModal(true); };

  const handleSave = async () => {
    if (!form.nome.trim()) return;
    setLoading(true);
    setErro(null);
    try {
      const body = { nome: form.nome.trim(), descricao: form.descricao.trim(), icone: form.icone };
      const url = editing ? `/api/categorias/${editing.id}` : '/api/categorias';
      const method = editing ? 'PUT' : 'POST';
      const r = await fetch(url, { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
      if (!r.ok) {
        const data = await r.json().catch(() => ({}));
        throw new Error(data.erro || `Erro ${r.status}`);
      }
      setShowModal(false);
      await load();
    } catch (e: any) {
      setErro(e.message || 'Erro ao guardar. Tente novamente.');
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (id: number) => {
    if (!confirm('Apagar esta categoria?')) return;
    try {
      const r = await fetch(`/api/categorias/${id}`, { method: 'DELETE' });
      if (!r.ok) {
        const data = await r.json().catch(() => ({}));
        alert(data.erro || 'Erro ao apagar categoria');
        return;
      }
      await load();
    } catch {
      alert('Não foi possível ligar ao servidor.');
    }
  };

  return (
    <div>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 24, flexWrap: 'wrap', gap: 12 }}>
        <div>
          <h2 style={{ fontSize: 22, fontWeight: 700, color: 'var(--text-primary)' }}>Categorias</h2>
          <p style={{ fontSize: 14, color: 'var(--text-secondary)', marginTop: 4 }}>Organize os seus produtos por categoria</p>
        </div>
        <button className="btn-primary" onClick={openNew}><Plus size={18} /> Nova Categoria</button>
      </div>

      {/* Grid */}
      {categorias.length === 0
        ? <div className="empty-state"><h3>Nenhuma categoria criada</h3><p>Clique em "Nova Categoria" para começar.</p></div>
        : (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: 16 }}>
            {categorias.map(c => (
              <div className="card" key={c.id} style={{ display: 'flex', alignItems: 'center', gap: 14, padding: 18 }}>
                <div style={{ width: 48, height: 48, borderRadius: 12, backgroundColor: 'var(--color-brand-light)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 24, flexShrink: 0 }}>
                  {c.icone || '🏷️'}
                </div>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <p style={{ fontWeight: 600, color: 'var(--text-primary)', fontSize: 14 }}>{c.nome}</p>
                  {c.descricao && <p style={{ fontSize: 12, color: 'var(--text-secondary)', marginTop: 2, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{c.descricao}</p>}
                  {c.totalProdutos !== undefined && <p style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 2 }}>{c.totalProdutos} produto(s)</p>}
                </div>
                <div style={{ display: 'flex', gap: 6 }}>
                  <button className="icon-btn" onClick={() => openEdit(c)}><Pencil size={14} /></button>
                  <button className="icon-btn delete" onClick={() => handleDelete(c.id)}><Trash2 size={14} /></button>
                </div>
              </div>
            ))}
          </div>
        )
      }

      {/* Modal */}
      {showModal && (
        <div className="modal-overlay">
          <div className="modal-container">
            <div className="modal-header">
              <h3>{editing ? 'Editar Categoria' : 'Nova Categoria'}</h3>
              <button className="modal-close-btn" onClick={() => setShowModal(false)}><X size={20} /></button>
            </div>
            {erro && <p style={{ color: 'var(--color-danger)', fontSize: 13, padding: '8px 0', background: 'rgba(239,68,68,0.1)', borderRadius: 6, paddingInline: 10, marginBottom: 8 }}>⚠️ {erro}</p>}
            <div className="form-group">
              <label>Nome *</label>
              <input value={form.nome} onChange={e => setForm({ ...form, nome: e.target.value })} placeholder="Ex: Bebidas" />
            </div>
            <div className="form-group">
              <label>Descrição</label>
              <input value={form.descricao} onChange={e => setForm({ ...form, descricao: e.target.value })} placeholder="Descrição opcional" />
            </div>
            <div className="form-group">
              <label>Ícone</label>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(8, 1fr)', gap: 6, maxHeight: 160, overflowY: 'auto' }}>
                {EMOJIS.map(em => (
                  <button
                    key={em}
                    onClick={() => setForm({ ...form, icone: em })}
                    style={{
                      background: form.icone === em ? 'var(--color-brand-light)' : 'transparent',
                      border: `1px solid ${form.icone === em ? 'var(--color-brand)' : 'var(--border-color)'}`,
                      borderRadius: 6, padding: 6, fontSize: 18, cursor: 'pointer'
                    }}
                  >{em}</button>
                ))}
              </div>
            </div>
            <div className="modal-actions">
              <button className="btn-secondary" onClick={() => setShowModal(false)}>Cancelar</button>
              <button className="btn-primary" onClick={handleSave} disabled={loading}>
                {loading ? 'A guardar...' : (editing ? 'Guardar' : 'Criar')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default CategoriasPage;
