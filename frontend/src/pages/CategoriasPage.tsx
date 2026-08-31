import React, { useEffect, useState } from 'react';
import { Plus, Pencil, Trash2, X } from 'lucide-react';

const EMOJIS = ['🛍️','🏷️','🍎','🥤','🧴','📦','🍞','🥩','🧀','🥦','🍺','☕','🧹','🪣','💊','👕','👟','📱','🔧','💡','🐔','🥚','🌽','🫙','🧂','🫒','🍫','🍬'];

interface Categoria { id: number; nome: string; descricao: string; icone: string; totalProdutos?: number; }

const CategoriasPage: React.FC = () => {
  const [categorias, setCategorias] = useState<Categoria[]>([]);
  const [showModal, setShowModal] = useState(false);
  const [editing, setEditing] = useState<Categoria | null>(null);
  const [form, setForm] = useState({ nome: '', descricao: '', icone: '🏷️' });

  const load = () => fetch('/api/categorias').then(r => r.json()).then(setCategorias).catch(() => {});

  useEffect(() => { load(); }, []);

  const openNew = () => { setEditing(null); setForm({ nome: '', descricao: '', icone: '🏷️' }); setShowModal(true); };
  const openEdit = (c: Categoria) => { setEditing(c); setForm({ nome: c.nome, descricao: c.descricao || '', icone: c.icone || '🏷️' }); setShowModal(true); };

  const handleSave = async () => {
    if (!form.nome.trim()) return;
    const body = { nome: form.nome.trim(), descricao: form.descricao.trim(), icone: form.icone };
    if (editing) {
      await fetch(`/api/categorias/${editing.id}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
    } else {
      await fetch('/api/categorias', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
    }
    setShowModal(false);
    load();
  };

  const handleDelete = async (id: number) => {
    if (!confirm('Apagar esta categoria?')) return;
    await fetch(`/api/categorias/${id}`, { method: 'DELETE' });
    load();
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
              <button className="btn-primary" onClick={handleSave}>{editing ? 'Guardar' : 'Criar'}</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default CategoriasPage;
