import React, { useState } from 'react';
import { Plus, Trash2, X } from 'lucide-react';

interface User { id: string; name: string; email: string; role: string; password: string; }

const UsuariosPage: React.FC = () => {
  const getUsers = (): User[] => {
    const s = localStorage.getItem('registeredUsers');
    return s ? JSON.parse(s) : [];
  };
  const [users, setUsers] = useState<User[]>(getUsers);
  const [showModal, setShowModal] = useState(false);
  const [form, setForm] = useState({ name: '', email: '', password: '', role: 'operator' });

  const save = (us: User[]) => { localStorage.setItem('registeredUsers', JSON.stringify(us)); setUsers(us); };

  const handleAdd = () => {
    if (!form.name.trim() || !form.email.trim() || !form.password) return;
    const newUser: User = { id: Date.now().toString(), name: form.name.trim(), email: form.email.trim().toLowerCase(), role: form.role, password: form.password };
    save([...users, newUser]);
    setForm({ name: '', email: '', password: '', role: 'operator' });
    setShowModal(false);
  };

  const handleDelete = (id: string) => {
    if (!confirm('Remover este utilizador?')) return;
    save(users.filter(u => u.id !== id));
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 20, flexWrap: 'wrap', gap: 12 }}>
        <div>
          <h2 style={{ fontSize: 22, fontWeight: 700, color: 'var(--text-primary)' }}>Utilizadores</h2>
          <p style={{ fontSize: 14, color: 'var(--text-secondary)', marginTop: 4 }}>Gerir acessos ao sistema</p>
        </div>
        <button className="btn-primary" onClick={() => setShowModal(true)}><Plus size={18} /> Novo Utilizador</button>
      </div>

      <div className="card">
        <div className="table-wrapper">
          <table>
            <thead>
              <tr><th>Nome</th><th>E-mail</th><th>Perfil</th><th>Ação</th></tr>
            </thead>
            <tbody>
              {users.map(u => (
                <tr key={u.id}>
                  <td style={{ fontWeight: 600 }}>{u.name}</td>
                  <td style={{ color: 'var(--text-secondary)' }}>{u.email}</td>
                  <td>
                    <span className={`badge ${u.role === 'superuser' ? 'badge-warning' : 'badge-info'}`}>
                      {u.role === 'superuser' ? '⭐ Superusuário' : 'Operador'}
                    </span>
                  </td>
                  <td>
                    <button className="icon-btn delete" onClick={() => handleDelete(u.id)}><Trash2 size={14} /></button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {showModal && (
        <div className="modal-overlay">
          <div className="modal-container">
            <div className="modal-header">
              <h3>Novo Utilizador</h3>
              <button className="modal-close-btn" onClick={() => setShowModal(false)}><X size={20} /></button>
            </div>
            <div className="form-group"><label>Nome *</label><input value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} placeholder="Nome completo" /></div>
            <div className="form-group"><label>E-mail *</label><input type="email" value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} placeholder="email@exemplo.com" /></div>
            <div className="form-group"><label>Senha *</label><input type="password" value={form.password} onChange={e => setForm({ ...form, password: e.target.value })} placeholder="Senha de acesso" /></div>
            <div className="form-group">
              <label>Perfil</label>
              <select value={form.role} onChange={e => setForm({ ...form, role: e.target.value })}>
                <option value="operator">Operador</option>
                <option value="superuser">Superusuário</option>
              </select>
            </div>
            <div className="modal-actions">
              <button className="btn-secondary" onClick={() => setShowModal(false)}>Cancelar</button>
              <button className="btn-primary" onClick={handleAdd}>Criar</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default UsuariosPage;
