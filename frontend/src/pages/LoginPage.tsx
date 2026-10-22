import { useRef, useState } from 'react';
import {
  Store,
  Eye,
  EyeOff,
  LogIn,
  Zap,
  ShieldCheck,
  Coins,
  PackageCheck,
  TrendingUp,
  Smartphone,
  Activity,
  Receipt,
  Radio,
  Lock,
} from 'lucide-react';
import type { UserSession } from '../App';
import { Field, Notice, Spinner } from '../components/UI';
import { AITypewriter, type AIPhrase } from '../components/ui/AITypewriter';
import './LoginPage.css';

const AI_STREAM_PHRASES: AIPhrase[] = [
  {
    id: 'vendas',
    category: 'Vendas & Balcão',
    command: 'bstore.terminal.processar_venda()',
    text: 'Registo de venda em 3 segundos. Leitura de código de barras pela câmara, cálculo instantâneo de troco e impressão de comprovativo térmico.',
    metricLabel: 'Atendimento 3x mais veloz',
    metricIcon: Zap,
    color: '#34d399',
  },
  {
    id: 'caixa',
    category: 'Auditoria de Caixa',
    command: 'bstore.caixa.fecho_cego()',
    text: 'Controlo rigoroso da gaveta. Apuração de quebra de caixa na contagem física, registo de despesas e envio automático do relatório ao proprietário.',
    metricLabel: '100% de conciliação de gaveta',
    metricIcon: ShieldCheck,
    color: '#60a5fa',
  },
  {
    id: 'fiado',
    category: 'Fiado & Devedores',
    command: 'bstore.clientes.auditar_devedores()',
    text: 'Fim dos cadernos de papel e cobranças esquecidas. Histórico individual de compras a prazo, limites de crédito e registo de amortizações parciais.',
    metricLabel: 'Zero dívidas perdidas',
    metricIcon: Coins,
    color: '#fbbf24',
  },
  {
    id: 'stock',
    category: 'Gestão de Inventário',
    command: 'bstore.stock.analisar_ruptura()',
    text: 'Monitorização contínua de saldo em tempo real. Alertas preditivos antes de produtos esgotarem na prateleira e identificação de itens em falta.',
    metricLabel: 'Reposição precisa no tempo certo',
    metricIcon: PackageCheck,
    color: '#a78bfa',
  },
  {
    id: 'lucro',
    category: 'Lucro Líquido Real',
    command: 'bstore.dre.apurar_margem()',
    text: 'Visão financeira de ponta a ponta. Cruzamento automático do faturamento bruto contra o custo das mercadorias vendidas e saídas operacionais.',
    metricLabel: 'Lucro real apurado ao vivo',
    metricIcon: TrendingUp,
    color: '#f472b6',
  },
  {
    id: 'mobilidade',
    category: 'Multi-Dispositivo',
    command: 'bstore.rede.sincronizar_dispositivos()',
    text: 'A sua loja sincronizada no telemóvel Android, tablet ou computador do balcão. Sem configurações complexas e com alta disponibilidade.',
    metricLabel: 'Acesso seguro em qualquer lugar',
    metricIcon: Smartphone,
    color: '#38bdf8',
  },
];

const TICKER_ITEMS = [
  'VENDAS EM 3 SEGUNDOS',
  'FECHO DE CAIXA AUDITADO',
  'COMPROVATIVOS TÉRMICOS',
  'GESTÃO DE FIADO & CLIENTES',
  'SCANNER DE CÓDIGO DE BARRAS',
  'CONTROLO DE STOCK EM TEMPO REAL',
  'LUCRO LÍQUIDO NO BOLSO',
  'COMPATÍVEL COM PC & ANDROID',
];

export default function LoginPage({ setUser }: { setUser: (user: UserSession) => void }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [passo2FA, setPasso2FA] = useState(false);
  const [totpCodigo, setTotpCodigo] = useState('');
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
      const payload: any = { email: email.trim().toLowerCase(), password };
      if (passo2FA) {
        payload.totpCodigo = totpCodigo.trim();
      }

      const response = await fetch('/api/auth/login', {
        method: 'POST',
        credentials: 'same-origin',
        signal: AbortSignal.timeout(20000),
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });

      const data = await response.json().catch(() => ({}));

      // Se o servidor exigir o segundo fator (2FA)
      if (response.status === 202 && data.requer2FA) {
        setPasso2FA(true);
        setError(null);
        return;
      }

      if (!response.ok) {
        setError(
          response.status >= 500
            ? 'O servidor não conseguiu iniciar a sessão. Tente novamente.'
            : data.erro || 'Email ou senha incorretos.',
        );
        return;
      }

      const user = data;
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
        {/* LADO STREAMING IA AUTOMÁTICO (PC & TABLET) */}
        <section
          className="login-story"
          aria-label="Transmissão contínua de funcionalidades do BStore"
        >
          {/* Header da Marca + Status de Transmissão */}
          <div className="login-story-header">
            <div className="login-brand">
              <span className="login-brand-mark" aria-hidden="true">
                <Store size={26} />
              </span>
              <div className="login-brand-meta">
                <span className="login-brand-name">BStore</span>
                <span className="login-brand-badge">SISTEMA COMERCIAL</span>
              </div>
            </div>

            <div className="stream-live-pill" title="Transmissão contínua em tempo real">
              <span className="live-dot" aria-hidden="true" />
              <Radio size={13} className="live-icon" aria-hidden="true" />
              <span>TRANSMISSÃO AO VIVO</span>
            </div>
          </div>

          {/* Marquee Ticker de Alta Velocidade (Sem estrelas, sem emojis) */}
          <div className="stream-ticker-rail" aria-hidden="true">
            <div className="stream-ticker-track">
              {[...TICKER_ITEMS, ...TICKER_ITEMS].map((item, i) => (
                <span key={`${item}-${i}`} className="ticker-item">
                  <Activity size={10} className="ticker-symbol" />
                  {item}
                </span>
              ))}
            </div>
          </div>

          {/* Terminal de Streaming IA Letra por Letra (100% Automático) */}
          <div className="ai-stream-wrapper">
            <AITypewriter phrases={AI_STREAM_PHRASES} />
          </div>

          {/* Cards Flutuantes de Telemetria Operacional */}
          <div className="stream-telemetry-row" aria-hidden="true">
            <div className="telemetry-chip">
              <Receipt size={13} className="telemetry-icon" />
              <span>Venda balcão processada: 450,00 MT</span>
            </div>
            <div className="telemetry-chip">
              <ShieldCheck size={13} className="telemetry-icon" />
              <span>Sessão de caixa auditada: 0,00 MT quebra</span>
            </div>
          </div>

          <p className="login-story-footer">
            BStore — Gestão de stock e vendas para pequenos e médios estabelecimentos comerciais.
          </p>
        </section>

        {/* LADO FORMULÁRIO DE LOGIN */}
        <section className="login-form-panel" aria-labelledby="login-title">
          <div className="login-card">
            {/* Header Mobile com Terminal IA Compacto */}
            <div className="login-mobile-header">
              <div className="login-mobile-brand">
                <span className="login-mobile-icon" aria-hidden="true">
                  <Store size={20} />
                </span>
                <span className="login-mobile-title">BStore</span>
                <span className="stream-live-pill mobile-pill">
                  <span className="live-dot" /> AO VIVO
                </span>
              </div>

              {/* Streaming IA no Mobile (Automático Letra por Letra) */}
              <div className="login-mobile-ai-preview">
                <AITypewriter phrases={AI_STREAM_PHRASES} />
              </div>
            </div>

            <div className="login-header-group">
              <h1 id="login-title">
                {passo2FA ? 'Autenticação 2FA' : 'Aceder ao Sistema'}
              </h1>
              <p className="login-subtitle">
                {passo2FA
                  ? 'Introduza o código de 6 dígitos da sua aplicação autenticadora.'
                  : 'Introduza as suas credenciais para continuar.'}
              </p>
            </div>

            <form onSubmit={handleLogin} aria-busy={loading} className="login-form">
              {error && <Notice>{error}</Notice>}

              <fieldset disabled={loading}>
                {!passo2FA ? (
                  <>
                    <Field id="login-email" label="Email">
                      <input
                        id="login-email"
                        name="email"
                        type="email"
                        autoComplete="username"
                        autoCapitalize="none"
                        spellCheck={false}
                        placeholder="ex: admin@bstore.com"
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
                          placeholder="Introduza a sua senha"
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
                      {loading ? (
                        <Spinner size="small" />
                      ) : (
                        <span aria-hidden="true">
                          <LogIn size={17} />
                        </span>
                      )}
                      <span>{loading ? 'A entrar…' : 'Entrar na minha loja'}</span>
                    </button>
                  </>
                ) : (
                  <>
                    <div style={{ textAlign: 'center', marginBottom: '1.25rem' }}>
                      <span
                        style={{
                          display: 'inline-flex',
                          padding: '0.75rem',
                          borderRadius: '50%',
                          background: 'rgba(52, 211, 153, 0.12)',
                          color: '#059669',
                          marginBottom: '0.75rem',
                        }}
                      >
                        <ShieldCheck size={32} />
                      </span>
                      <p style={{ fontSize: '0.875rem', color: 'var(--text-muted, #64748b)' }}>
                        Conta protegida por 2FA (RFC 6238 TOTP).
                      </p>
                    </div>

                    <Field id="login-totp" label="Código de Verificação (6 dígitos)">
                      <input
                        id="login-totp"
                        name="totpCodigo"
                        type="text"
                        inputMode="numeric"
                        autoComplete="one-time-code"
                        maxLength={6}
                        autoFocus
                        placeholder="000000"
                        value={totpCodigo}
                        onChange={(e) => setTotpCodigo(e.target.value.replace(/\D/g, ''))}
                        style={{
                          textAlign: 'center',
                          fontSize: '1.5rem',
                          letterSpacing: '0.35em',
                          fontFamily: 'monospace',
                          fontWeight: 'bold',
                        }}
                        required
                      />
                    </Field>

                    <button type="submit" className="btn-primary login-submit" disabled={loading || totpCodigo.length < 6}>
                      {loading ? (
                        <Spinner size="small" />
                      ) : (
                        <span aria-hidden="true">
                          <ShieldCheck size={17} />
                        </span>
                      )}
                      <span>{loading ? 'A verificar…' : 'Confirmar e Aceder'}</span>
                    </button>

                    <button
                      type="button"
                      className="btn-secondary"
                      style={{ width: '100%', marginTop: '0.75rem', justifyContent: 'center' }}
                      onClick={() => {
                        setPasso2FA(false);
                        setTotpCodigo('');
                        setError(null);
                      }}
                      disabled={loading}
                    >
                      ← Voltar ao login normal
                    </button>
                  </>
                )}
              </fieldset>
            </form>

            <div className="login-trust-footer">
              <div className="login-security-tag">
                <Lock size={12} aria-hidden="true" />
                <span>Ligação segura com encriptação de ponta a ponta</span>
              </div>
              <p className="login-help">
                Dificuldades de acesso? Contacte o administrador do sistema.
              </p>
            </div>
          </div>
        </section>
      </div>
    </main>
  );
}
