import React, { useState } from 'react';
import { Store, Key, Mail, LogIn } from 'lucide-react';
import './LoginPage.css';

interface UserSession { userId: number; email: string; role: 'superuser' | 'operator'; diasRestantes: number; }
interface LoginPageProps { setUser: (u: UserSession) => void; }

const LoginPage: React.FC<LoginPageProps> = ({ setUser }) => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const r = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: email.trim().toLowerCase(), password }),
      });
      if (!r.ok) {
        const data = await r.json().catch(() => ({}));
        setError(data.erro || 'Email ou senha incorretos.');
        return;
      }
      const u = await r.json();
      const session: UserSession = { userId: u.id, email: u.email, role: u.role, diasRestantes: u.diasRestantes ?? -1 };
      localStorage.setItem('currentUser', JSON.stringify(session));
      localStorage.setItem('profileName', u.nome.split(' ')[0]);
      localStorage.setItem('profileFullName', u.nome);
      localStorage.setItem('profileRole', u.role === 'superuser' ? 'Superusuário' : 'Operador');
      setUser(session);
    } catch {
      setError('Não foi possível ligar ao servidor. Aguarde e tente novamente.');
    } finally {
      setLoading(false);
    }
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
          <button type="submit" className="login-btn" disabled={loading}>
            <LogIn size={18} /> {loading ? 'A entrar...' : 'Entrar'}
          </button>
        </form>
        <p className="login-hint">Admin padrão: admin@flexstock.com / admin123</p>
      </div>
    </div>
  );
};

export default LoginPage;
