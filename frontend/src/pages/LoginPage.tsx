import React, { useState } from 'react';
import { Store, Key, Mail, LogIn } from 'lucide-react';
import './LoginPage.css';

interface UserSession { email: string; role: 'superuser' | 'operator'; }
interface LoginPageProps { setUser: (u: UserSession) => void; }

const LoginPage: React.FC<LoginPageProps> = ({ setUser }) => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);

  const handleLogin = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    const trimmed = email.trim().toLowerCase();

    let users: any[] = [];
    const saved = localStorage.getItem('registeredUsers');
    if (saved) {
      users = JSON.parse(saved);
    } else {
      users = [
        { id: '1', name: 'Administrador', email: 'admin@bstore.com', role: 'superuser', password: '123456' },
        { id: '2', name: 'Operador',       email: 'operador@bstore.com', role: 'operator', password: '1234' },
      ];
      localStorage.setItem('registeredUsers', JSON.stringify(users));
    }

    const match = users.find((u: any) => u.email === trimmed);
    if (!match) { setError('E-mail não cadastrado.'); return; }
    if (match.password !== password) { setError('Senha incorreta.'); return; }

    const session: UserSession = { email: trimmed, role: match.role };
    localStorage.setItem('currentUser', JSON.stringify(session));
    localStorage.setItem('profileName', match.name.split(' ')[0]);
    localStorage.setItem('profileFullName', match.name);
    localStorage.setItem('profileRole', match.role === 'superuser' ? 'Superusuário' : 'Operador');
    setUser(session);
  };

  return (
    <div className="login-overlay">
      <div className="card login-card">
        <div className="login-logo"><Store size={32} /></div>
        <h2>Flex Stock</h2>
        <p>Faça login para gerenciar seu negócio</p>
        <form onSubmit={handleLogin}>
          {error && <div className="login-error">{error}</div>}
          <div className="form-group">
            <label>E-mail</label>
            <div className="input-group">
              <Mail size={16} className="input-icon" />
              <input type="email" placeholder="seu@email.com" value={email} onChange={e => setEmail(e.target.value)} required />
            </div>
          </div>
          <div className="form-group">
            <label>Senha</label>
            <div className="input-group">
              <Key size={16} className="input-icon" />
              <input type="password" placeholder="••••••••" value={password} onChange={e => setPassword(e.target.value)} required />
            </div>
          </div>
          <button type="submit" className="login-btn"><LogIn size={18} /> Entrar</button>
        </form>
        <p className="login-hint">Admin padrão: admin@bstore.com / 123456</p>
      </div>
    </div>
  );
};

export default LoginPage;
