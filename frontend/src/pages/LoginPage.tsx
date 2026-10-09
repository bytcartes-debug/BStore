import { useRef, useState } from 'react';
import { Store, Receipt, Package, Users, Eye, EyeOff, LogIn } from 'lucide-react';
import type { UserSession } from '../App';
import { Field, Notice, Spinner } from '../components/UI';
import './LoginPage.css';

export default function LoginPage({ setUser }: { setUser: (user: UserSession) => void }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const lock = useRef(false);

  const handleLogin = async (event: React.FormEvent) => {
    event.preventDefault();
    if (lock.current) return;
    lock.current = true;
    setError(null);
    setLoading(true);
    try {
      const response = await fetch('/api/auth/login', {
        method: 'POST',
        credentials: 'same-origin',
        signal: AbortSignal.timeout(20000),
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: email.trim().toLowerCase(), password }),
      });
      if (!response.ok) {
        const data = await response.json().catch(() => ({}));
        setError(
          response.status >= 500
            ? 'O servidor não conseguiu iniciar a sessão. Tente novamente.'
            : data.erro || 'Email ou senha incorretos.',
        );
        return;
      }
      const user = await response.json();
      localStorage.setItem('profileName', user.nome.split(' ')[0]);
      localStorage.setItem('profileFullName', user.nome);
      localStorage.setItem('profileRole', user.role === 'superuser' ? 'Administrador' : 'Operador');
      setUser({
        userId: user.id,
        email: user.email,
        role: user.role,
        diasRestantes: user.diasRestantes ?? -1,
      });
    } catch {
      setError('Não foi possível ligar ao servidor. Verifique a ligação e tente novamente.');
    } finally {
      lock.current = false;
      setLoading(false);
    }
  };

  return (
    <main className="login-page">
      <div className="login-wrapper">
        <section className="login-story" aria-label="BStore, gestão de loja">
          <div className="login-brand">
            <span className="login-brand-mark" aria-hidden="true">
              <Store size={30} />
            </span>
            <span>BStore</span>
          </div>

          <div className="login-intro">
            <h1>
              Mais atenção à loja.
              <br />
              Menos tempo nas contas.
            </h1>
            <p>Vendas, stock e clientes. Tudo no mesmo lugar, do primeiro produto ao fecho do dia.</p>

            <ul className="login-features">
              <li>
                <span className="feature-emoji" aria-hidden="true">
                  <Receipt size={20} />
                </span>
                <span>Registe vendas e calcule o troco na hora</span>
              </li>
              <li>
                <span className="feature-emoji" aria-hidden="true">
                  <Package size={20} />
                </span>
                <span>Acompanhe o stock dos seus produtos</span>
              </li>
              <li>
                <span className="feature-emoji" aria-hidden="true">
                  <Users size={20} />
                </span>
                <span>Tenha as dívidas e clientes organizados</span>
              </li>
            </ul>
          </div>

          <p className="login-story-footer">FlexStock — O seu negócio, organizado.</p>
        </section>

        <section className="login-form-panel" aria-labelledby="login-title">
          <div className="login-card">
            <div className="login-mobile-brand">
              <span aria-hidden="true"><Store size={26} /></span> BStore
            </div>

            <h2 id="login-title">Bem-vindo de volta</h2>
            <p className="login-subtitle">Entre para continuar a gerir a sua loja.</p>

            <form onSubmit={handleLogin} aria-busy={loading}>
              {error && <Notice>{error}</Notice>}

              <fieldset disabled={loading}>
                <Field id="login-email" label="Email">
                  <input
                    id="login-email"
                    name="email"
                    type="email"
                    autoComplete="username"
                    autoCapitalize="none"
                    spellCheck={false}
                    placeholder="nome@exemplo.com"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    required
                  />
                </Field>

                <Field id="login-password" label="Senha">
                  <div className="password-field">
                    <input
                      id="login-password"
                      name="password"
                      type={showPassword ? 'text' : 'password'}
                      autoComplete="current-password"
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      required
                    />
                    <button
                      type="button"
                      className="password-toggle"
                      aria-label={showPassword ? 'Ocultar senha' : 'Mostrar senha'}
                      aria-pressed={showPassword}
                      onClick={() => setShowPassword((value) => !value)}
                    >
                      {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                    </button>
                  </div>
                </Field>

                <button type="submit" className="btn-primary login-submit" disabled={loading}>
                  {loading ? <Spinner size="small" /> : <span aria-hidden="true"><LogIn size={18} /></span>}
                  <span>{loading ? 'A entrar…' : 'Entrar na minha loja'}</span>
                </button>
              </fieldset>
            </form>

            <p className="login-help">
              Precisa de acesso ou de recuperar a senha?
              <br />
              Contacte o administrador da loja.
            </p>
          </div>
        </section>
      </div>
    </main>
  );
}
