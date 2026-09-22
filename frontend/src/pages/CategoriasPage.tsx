import { apiFetch } from '../utils/api';
import React, { useEffect, useState } from 'react';
import { Plus, Pencil, Trash2, X, AlertTriangle } from 'lucide-react';

const EMOJIS = ['🛍️','🏷️','🍎','🥤','🧴','📦','🍞','🥩','🧀','🥦','🍺','☕','🧹','🪣','💊','👕','👟','📱','🔧','💡','🐔','🥚','🌽','🫙','🧂','🫒','🍫','🍬'];

interface Categoria { id: number; nome: string; descricao: string; icone: string; totalProdutos?: number; }
interface ProdutoSimples { id: number; nome: string; preco: number; stock: number; }

const CategoriasPage: React.FC = () => {
  const [categorias, setCategorias] = useState<Categoria[]>([]);
  const [showModal, setShowModal]   = useState(false);
  const [editing, setEditing]       = useState<Categoria | null>(null);
  const [form, setForm]             = useState({ nome: '', descricao: '', icone: '🏷️' });
  const [loading, setLoading]       = useState(false);
  const [erro, setErro]             = useState<string | null>(null);

  // Modal de confirmação de exclusão
  const [confirmarDelete, setConfirmarDelete] = useState<{ cat: Categoria; produtos: ProdutoSimples[] } | null>(null);
  const [loadingDelete, setLoadingDelete]     = useState(false);

  const load = async () => {
    try {
      const r = await apiFetch('/api/categorias');
      if (!r.ok) throw new Error('Erro ao carregar categorias');
      setCategorias(await r.json());
    } catch {
      setErro('Não foi possível ligar ao servidor.');
    }
  };

  useEffect(() => { load(); }, []);

  const openNew  = () => { setEditing(null); setForm({ nome: '', descricao: '', icone: '🏷️' }); setErro(null); setShowModal(true); };
  const openEdit = (c: Categoria) => { setEditing(c); setForm({ nome: c.nome, descricao: c.descricao || '', icone: c.icone || '🏷️' }); setErro(null); setShowModal(true); };

  const handleSave = async () => {
    if (!form.nome.trim()) return;
    setLoading(true); setErro(null);
    try {
      const body = { nome: form.nome.trim(), descricao: form.descricao.trim(), icone: form.icone };
      const url    = editing ? `/api/categorias/${editing.id}` : '/api/categorias';
      const method = editing ? 'PUT' : 'POST';
      const r = await apiFetch(url, { method, body: JSON.stringify(body) });
      if (!r.ok) { const d = await r.json().catch(() => ({})); throw new Error(d.erro || `Erro ${r.status}`); }
      setShowModal(false);
      await load();
    } catch (e: any) {
      setErro(e.message || 'Erro ao guardar. Tente novamente.');
    } finally { setLoading(false); }
  };

  // Clique em 🗑️ — busca produtos antes de perguntar
  const handleDeleteClick = async (cat: Categoria) => {
    try {
      const r = await apiFetch(`/api/categorias/${cat.id}/produtos`);
      const produtos: ProdutoSimples[] = r.ok ? await r.json() : [];
      setConfirmarDelete({ cat, produtos });
    } catch {
      setConfirmarDelete({ cat, produtos: [] });
    }
  };

  // Confirma e executa a exclusão
  const handleDeleteConfirm = async () => {
    if (!confirmarDelete) return;
    setLoadingDelete(true);
    try {
      const r = await apiFetch(`/api/categorias/${confirmarDelete.cat.id}`, { method: 'DELETE' });
      if (!r.ok) { const d = await r.json().catch(() => ({})); alert(d.erro || 'Erro ao apagar.'); return; }
      setConfirmarDelete(null);
      await load();
    } catch { alert('Não foi possível ligar ao servidor.'); }
    finally { setLoadingDelete(false); }
  };

  return (
    <div>
      {/* Header */}
      <div style={{ display:'flex', justifyContent:'space-between', alignItems:'flex-start', marginBottom:24, flexWrap:'wrap', gap:12 }}>
        <div>
          <h2 style={{ fontSize:22, fontWeight:700, color:'var(--text-primary)' }}>Categorias</h2>
          <p style={{ fontSize:14, color:'var(--text-secondary)', marginTop:4 }}>Organize os seus produtos por categoria</p>
        </div>
        <button className="btn-primary" onClick={openNew}><Plus size={18} /> Nova Categoria</button>
      </div>

      {/* Grid */}
      {categorias.length === 0
        ? <div className="empty-state"><h3>Nenhuma categoria criada</h3><p>Clique em "Nova Categoria" para começar.</p></div>
        : (
          <div style={{ display:'grid', gridTemplateColumns:'repeat(auto-fill, minmax(280px, 1fr))', gap:16 }}>
            {categorias.map(c => (
              <div className="card" key={c.id} style={{ display:'flex', alignItems:'center', gap:14, padding:18 }}>
                <div style={{ width:48, height:48, borderRadius:12, backgroundColor:'var(--color-brand-light)', display:'flex', alignItems:'center', justifyContent:'center', fontSize:24, flexShrink:0 }}>
                  {c.icone || '🏷️'}
                </div>
                <div style={{ flex:1, minWidth:0 }}>
                  <p style={{ fontWeight:600, color:'var(--text-primary)', fontSize:14 }}>{c.nome}</p>
                  {c.descricao && <p style={{ fontSize:12, color:'var(--text-secondary)', marginTop:2, overflow:'hidden', textOverflow:'ellipsis', whiteSpace:'nowrap' }}>{c.descricao}</p>}
                  {c.totalProdutos !== undefined && <p style={{ fontSize:12, color:'var(--text-muted)', marginTop:2 }}>{c.totalProdutos} produto(s)</p>}
                </div>
                <div style={{ display:'flex', gap:6 }}>
                  <button className="icon-btn" onClick={() => openEdit(c)}><Pencil size={14} /></button>
                  <button className="icon-btn delete" onClick={() => handleDeleteClick(c)}><Trash2 size={14} /></button>
                </div>
              </div>
            ))}
          </div>
        )
      }

      {/* Modal criar/editar */}
      {showModal && (
        <div className="modal-overlay">
          <div className="modal-container">
            <div className="modal-header">
              <h3>{editing ? 'Editar Categoria' : 'Nova Categoria'}</h3>
              <button className="modal-close-btn" onClick={() => setShowModal(false)}><X size={20} /></button>
            </div>
            {erro && <p style={{ color:'var(--color-danger)', fontSize:13, background:'rgba(239,68,68,0.1)', borderRadius:6, padding:'8px 10px', marginBottom:8 }}>⚠️ {erro}</p>}
            <div className="form-group">
              <label>Nome *</label>
              <input value={form.nome} onChange={e => setForm({...form, nome:e.target.value})} placeholder="Ex: Bebidas" />
            </div>
            <div className="form-group">
              <label>Descrição</label>
              <input value={form.descricao} onChange={e => setForm({...form, descricao:e.target.value})} placeholder="Descrição opcional" />
            </div>
            <div className="form-group">
              <label>Ícone</label>
              <div style={{ display:'grid', gridTemplateColumns:'repeat(8, 1fr)', gap:6, maxHeight:160, overflowY:'auto' }}>
                {EMOJIS.map(em => (
                  <button key={em} onClick={() => setForm({...form, icone:em})} style={{
                    background: form.icone === em ? 'var(--color-brand-light)' : 'transparent',
                    border: `1px solid ${form.icone === em ? 'var(--color-brand)' : 'var(--border-color)'}`,
                    borderRadius:6, padding:6, fontSize:18, cursor:'pointer'
                  }}>{em}</button>
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

      {/* Modal de confirmação de exclusão */}
      {confirmarDelete && (
        <div className="modal-overlay">
          <div className="modal-container">
            <div className="modal-header">
              <h3 style={{ color:'var(--color-danger)', display:'flex', alignItems:'center', gap:8 }}>
                <AlertTriangle size={20} /> Remover Categoria
              </h3>
              <button className="modal-close-btn" onClick={() => setConfirmarDelete(null)}><X size={20} /></button>
            </div>

            <p style={{ fontSize:14, color:'var(--text-primary)', marginBottom:12 }}>
              Tem a certeza que quer remover a categoria <strong>"{confirmarDelete.cat.nome}"</strong>?
            </p>

            {confirmarDelete.produtos.length > 0 ? (
              <>
                <div style={{ background:'rgba(239,68,68,0.1)', border:'1px solid rgba(239,68,68,0.3)', borderRadius:8, padding:12, marginBottom:14 }}>
                  <p style={{ fontSize:13, fontWeight:700, color:'var(--color-danger)', marginBottom:8 }}>
                    ⚠️ Esta categoria contém {confirmarDelete.produtos.length} produto(s). Todos serão apagados:
                  </p>
                  <ul style={{ margin:0, paddingLeft:18, fontSize:13, color:'var(--text-secondary)' }}>
                    {confirmarDelete.produtos.map(p => (
                      <li key={p.id} style={{ marginBottom:2 }}>
                        <strong>{p.nome}</strong> — MT {p.preco.toFixed(2)} · Stock: {p.stock}
                      </li>
                    ))}
                  </ul>
                </div>
                <p style={{ fontSize:12, color:'var(--text-muted)', marginBottom:16 }}>
                  ⚠️ As vendas associadas a estes produtos também serão removidas permanentemente.
                </p>
              </>
            ) : (
              <p style={{ fontSize:13, color:'var(--text-secondary)', marginBottom:16 }}>
                Esta categoria está vazia. A remoção é segura.
              </p>
            )}

            <div className="modal-actions">
              <button className="btn-secondary" onClick={() => setConfirmarDelete(null)}>Cancelar</button>
              <button
                onClick={handleDeleteConfirm}
                disabled={loadingDelete}
                style={{ background:'var(--color-danger)', color:'#fff', border:'none', padding:'10px 18px', borderRadius:8, fontWeight:600, cursor:'pointer', opacity: loadingDelete ? 0.7 : 1 }}
              >
                <Trash2 size={16} style={{ marginRight:6, verticalAlign:'middle' }} />
                {loadingDelete ? 'A remover...' : confirmarDelete.produtos.length > 0 ? `Remover categoria e ${confirmarDelete.produtos.length} produto(s)` : 'Remover categoria'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default CategoriasPage;
