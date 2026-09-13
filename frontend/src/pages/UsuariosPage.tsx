import React, { useEffect, useState } from 'react';
import { Plus, Pencil, Trash2, X, RefreshCw, AlertTriangle } from 'lucide-react';
import { apiFetch } from '../utils/api';

interface Usuario {
  id: number; nome: string; email: string; role: string;
  diasAcesso: number | null; dataExpiracao: string | null;
  diasRestantes: number; expirado: boolean;
}

const OPCOES_DIAS = [
  { label: '14 dias', value: 14 },
  { label: '30 dias', value: 30 },
  { label: 'Permanente', value: 0 },
];

const UsuariosPage: React.FC = () => {
  const session    = JSON.parse(localStorage.getItem('currentUser') || '{}');
  const isSuperuser = session.role === 'superuser';

  const [usuarios, setUsuarios] = useState<Usuario[]>([]);
  const [showModal, setShowModal] = useState(false);
  const [editing, setEditing]   = useState<Usuario | null>(null);
  const [form, setForm]         = useState({ nome: '', email: '', password: '', role: 'operator', diasAcesso: 30 });
  const [loading, setLoading]   = useState(false);
  const [erro, setErro]         = useState<string | null>(null);

  const load = async () => {
    try {
      const r = await apiFetch('/api/usuarios');
      if (r.ok) setUsuarios(await r.json());
    } catch {}
  };

  useEffect(() => { load(); }, []);

  const openNew = () => {
    setEditing(null);
    setForm({ nome: '', email: '', password: '', role: 'operator', diasAcesso: 30 });
    setErro(null);
    setShowModal(true);
  };

  const openEdit = (u: Usuario) => {
    setEditing(u);
    setForm({ nome: u.nome, email: u.email, password: '', role: u.role, diasAcesso: u.diasAcesso ?? 0 });
    setErro(null);
    setShowModal(true);
  };

  const handleSave = async () => {
    if (!form.nome.trim() || !form.email.trim()) return;
    if (!editing && !form.password) { setErro('A senha é obrigatória para novos utilizadores.'); return; }
    setLoading(true); setErro(null);
    try {
      const body: any = { nome: form.nome.trim(), email: form.email.trim(), role: form.role, diasAcesso: form.diasAcesso };
      if (form.password) body.password = form.password;
      const url    = editing ? `/api/usuarios/${editing.id}` : '/api/usuarios';
      const method = editing ? 'PUT' : 'POST';
      const r = await apiFetch(url, { method, body: JSON.stringify(body) });
      if (!r.ok) { const d = await r.json().catch(() => ({})); throw new Error(d.erro || `Erro ${r.status}`); }
      setShowModal(false);
      await load();
    } catch (e: any) { setErro(e.message || 'Erro ao guardar.'); }
    finally { setLoading(false); }
  };

  const handleDelete = async (u: Usuario) => {
    if (!confirm(`Remover o utilizador "${u.nome}"?`)) return;
    try {
      const r = await apiFetch(`/api/usuarios/${u.id}`, { method: 'DELETE' });
      if (!r.ok) { const d = await r.json().catch(() => ({})); alert(d.erro || 'Erro ao remover.'); return; }
      await load();
    } catch { alert('Não foi possível ligar ao servidor.'); }
  };

  const handleRenovar = (u: Usuario) => {
    setEditing(u);
    setForm({ nome: u.nome, email: u.email, password: '', role: u.role, diasAcesso: u.diasAcesso ?? 30 });
    setErro(null);
    setShowModal(true);
  };

  const badgeExpiracao = (u: Usuario) => {
    if (u.role === 'superuser') return null;
    if (u.expirado)
      return <span className="badge badge-danger">⛔ Expirado</span>;
    if (u.diasRestantes === -1)
      return <span className="badge badge-success">∞ Permanente</span>;
    if (u.diasRestantes <= 7)
      return <span className="badge badge-warning"><AlertTriangle size={12} style={{marginRight:3}}/>{u.diasRestantes}d restantes</span>;
    return <span className="badge badge-info">{u.diasRestantes}d restantes</span>;
  };

  return (
    <div>
      <div style={{ display:'flex', justifyContent:'space-between', alignItems:'flex-start', marginBottom:20, flexWrap:'wrap', gap:12 }}>
        <div>
          <h2 style={{ fontSize:22, fontWeight:700, color:'var(--text-primary)' }}>Utilizadores</h2>
          <p style={{ fontSize:14, color:'var(--text-secondary)', marginTop:4 }}>Gerir acessos ao sistema</p>
        </div>
        {isSuperuser && (
          <button className="btn-primary" onClick={openNew}><Plus size={18} /> Novo Utilizador</button>
        )}
      </div>

      <div className="card">
        <div className="table-wrapper">
          <table>
            <thead>
              <tr><th>Nome</th><th>E-mail</th><th>Perfil</th><th>Acesso</th>{isSuperuser && <th>Ações</th>}</tr>
            </thead>
            <tbody>
              {usuarios.length === 0
                ? <tr><td colSpan={5} style={{ textAlign:'center', color:'var(--text-muted)', padding:24 }}>Nenhum utilizador</td></tr>
                : usuarios.map(u => (
                  <tr key={u.id}>
                    <td style={{ fontWeight:600 }}>{u.nome}</td>
                    <td style={{ color:'var(--text-secondary)' }}>{u.email}</td>
                    <td>
                      <span className={`badge ${u.role === 'superuser' ? 'badge-warning' : 'badge-info'}`}>
                        {u.role === 'superuser' ? '⭐ Superusuário' : 'Operador'}
                      </span>
                    </td>
                    <td>{badgeExpiracao(u)}</td>
                    {isSuperuser && (
                      <td style={{ display:'flex', gap:6 }}>
                        {u.role !== 'superuser' && (
                          <button className="icon-btn" title="Renovar acesso" onClick={() => handleRenovar(u)}>
                            <RefreshCw size={14} />
                          </button>
                        )}
                        <button className="icon-btn" onClick={() => openEdit(u)}><Pencil size={14} /></button>
                        <button className="icon-btn delete" onClick={() => handleDelete(u)}><Trash2 size={14} /></button>
                      </td>
                    )}
                  </tr>
                ))
              }
            </tbody>
          </table>
        </div>
      </div>

      {showModal && (
        <div className="modal-overlay">
          <div className="modal-container">
            <div className="modal-header">
              <h3>{editing ? 'Editar Utilizador' : 'Novo Utilizador'}</h3>
              <button className="modal-close-btn" onClick={() => setShowModal(false)}><X size={20} /></button>
            </div>
            {erro && <p style={{ color:'var(--color-danger)', fontSize:13, background:'rgba(239,68,68,0.1)', borderRadius:6, padding:'8px 10px', marginBottom:8 }}>⚠️ {erro}</p>}

            <div className="form-group"><label>Nome *</label><input value={form.nome} onChange={e => setForm({...form, nome:e.target.value})} placeholder="Nome completo" /></div>
            <div className="form-group"><label>E-mail *</label><input type="email" value={form.email} onChange={e => setForm({...form, email:e.target.value})} placeholder="email@exemplo.com" /></div>
            <div className="form-group">
              <label>{editing ? 'Nova Senha (deixe vazio para manter)' : 'Senha *'}</label>
              <input type="password" value={form.password} onChange={e => setForm({...form, password:e.target.value})} placeholder={editing ? 'Nova senha (opcional)' : 'Senha de acesso'} />
            </div>
            <div className="form-group">
              <label>Perfil</label>
              <select value={form.role} onChange={e => setForm({...form, role:e.target.value})}>
                <option value="operator">Operador</option>
                <option value="superuser">Superusuário</option>
              </select>
            </div>
            {form.role !== 'superuser' && (
              <div className="form-group">
                <label>⏱️ {editing ? 'Repor Acesso' : 'Tempo de Acesso'}</label>
                <div style={{ display:'flex', gap:8 }}>
                  {OPCOES_DIAS.map(op => (
                    <button
                      key={op.value}
                      type="button"
                      onClick={() => setForm({...form, diasAcesso: op.value})}
                      style={{
                        flex:1, padding:'8px 4px', borderRadius:8, border:'2px solid',
                        borderColor: form.diasAcesso === op.value ? 'var(--color-primary)' : 'var(--border)',
                        background: form.diasAcesso === op.value ? 'var(--color-primary)' : 'transparent',
                        color: form.diasAcesso === op.value ? '#fff' : 'var(--text-primary)',
                        fontWeight: form.diasAcesso === op.value ? 700 : 400,
                        cursor:'pointer', fontSize:13,
                      }}
                    >{op.label}</button>
                  ))}
                </div>
                {editing && form.diasAcesso > 0 && (
                  <p style={{ fontSize:12, color:'var(--text-secondary)', marginTop:6 }}>
                    ℹ️ A expiração será reposta para <b>{form.diasAcesso} dias a partir de hoje</b>.
                  </p>
                )}
              </div>
            )}

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

export default UsuariosPage;
