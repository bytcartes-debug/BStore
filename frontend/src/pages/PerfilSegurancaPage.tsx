import { useToast } from '../utils/toast';
import { useEffect, useRef, useState } from 'react';
import { User, Camera, Save, KeyRound, Eye, EyeOff, Store, Plus, Power, CreditCard } from 'lucide-react';
import type { UserSession } from '../App';
import { apiRequest } from '../utils/api';
import { useMutation } from '../utils/useResource';
import { PageHeading, Field, Notice, Spinner } from '../components/UI';

export default function PerfilSegurancaPage({ user }: { user: UserSession | null }) {
  const [name, setName] = useState(localStorage.getItem('profileFullName') || '');
  const [pic, setPic] = useState(localStorage.getItem('profilePic') || '');
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [profileError, setProfileError] = useState<string | null>(null);
  const [metodos, setMetodos] = useState<any[]>([]);
  const [controloCaixa, setControloCaixa] = useState(false);
  const [nomeLoja, setNomeLoja] = useState('');
  const [novoMetodoNome, setNovoMetodoNome] = useState('');
  const fileRef = useRef<HTMLInputElement>(null);
  const password = useMutation();
  const toast = useToast();

  const carregarDefinicoes = async () => {
    try {
      const [listaMetodos, def] = await Promise.all([
        apiRequest<any[]>('/api/metodos-pagamento').catch(() => []),
        apiRequest<any>('/api/definicoes').catch(() => ({ controloCaixa: false, nomeLoja: '' })),
      ]);
      setMetodos(listaMetodos || []);
      setControloCaixa(Boolean(def?.controloCaixa));
      setNomeLoja(def?.nomeLoja || '');
    } catch {}
  };

  useEffect(() => {
    carregarDefinicoes();
  }, []);

  const salvarNomeLoja = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await apiRequest('/api/definicoes', {
        method: 'PUT',
        body: JSON.stringify({ nomeLoja: nomeLoja.trim(), controloCaixa }),
      });
      toast('Nome da loja atualizado.');
    } catch {
      toast('Erro ao atualizar nome da loja.');
    }
  };

  const toggleControloCaixa = async () => {
    const novoValor = !controloCaixa;
    try {
      await apiRequest('/api/definicoes', {
        method: 'PUT',
        body: JSON.stringify({ nomeLoja: nomeLoja.trim(), controloCaixa: novoValor }),
      });
      setControloCaixa(novoValor);
      toast(novoValor ? 'Controlo de caixa ativado.' : 'Controlo de caixa desativado.');
    } catch {
      toast('Erro ao atualizar controlo de caixa.');
    }
  };

  const toggleMetodo = async (id: number) => {
    try {
      const res = await apiRequest<any>(`/api/metodos-pagamento/${id}/alternar`, {
        method: 'PATCH',
      });
      setMetodos((prev) => prev.map((m) => (m.id === id ? { ...m, ativo: res.ativo } : m)));
      toast(`Método ${res.ativo ? 'ativado' : 'desativado'}.`);
    } catch {
      toast('Erro ao alterar método de pagamento.');
    }
  };

  const adicionarMetodo = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!novoMetodoNome.trim()) return;
    try {
      const novo = await apiRequest<any>('/api/metodos-pagamento', {
        method: 'POST',
        body: JSON.stringify({ nome: novoMetodoNome.trim(), tipo: 'DIGITAL' }),
      });
      setMetodos((prev) => [...prev, novo]);
      setNovoMetodoNome('');
      toast(`Método ${novo.nome} adicionado.`);
    } catch (err: any) {
      toast(err?.message || 'Erro ao adicionar método de pagamento.');
    }
  };
  const updateProfile = () => window.dispatchEvent(new Event('bstore:profile'));
  const handlePhoto = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;
    setProfileError(null);
    if (!['image/jpeg', 'image/png'].includes(file.type) || file.size > 2 * 1024 * 1024) {
      setProfileError('Escolha uma imagem JPG ou PNG com até 2 MB.');
      event.target.value = '';
      return;
    }
    const reader = new FileReader();
    reader.onerror = () =>
      setProfileError('Não foi possível ler a imagem. Escolha outro ficheiro.');
    reader.onload = () => {
      try {
        const result = String(reader.result);
        localStorage.setItem('profilePic', result);
        setPic(result);
        updateProfile();
        toast('Foto atualizada neste dispositivo.');
      } catch {
        setProfileError(
          'Não há espaço no navegador para guardar esta imagem. Escolha uma imagem menor.',
        );
      }
    };
    reader.readAsDataURL(file);
  };
  const saveProfile = (event: React.FormEvent) => {
    event.preventDefault();
    setProfileError(null);
    if (!name.trim()) {
      setProfileError('Preencha o seu nome.');
      return;
    }
    try {
      localStorage.setItem('profileFullName', name.trim());
      localStorage.setItem('profileName', name.trim().split(' ')[0]);
      updateProfile();
      toast('Perfil guardado neste dispositivo.');
    } catch {
      setProfileError('Não foi possível guardar o perfil no navegador.');
    }
  };
  const changePassword = (event: React.FormEvent) => {
    event.preventDefault();
    if (newPassword !== confirmation) {
      password.setError('As senhas não coincidem. Confirme a nova senha.');
      return;
    }
    if (!user) return;
    void password.run(async () => {
      await apiRequest(`/api/usuarios/${user.userId}/alterar-senha`, {
        method: 'POST',
        body: JSON.stringify({ senhaAtual: currentPassword, novaSenha: newPassword }),
      });
      setCurrentPassword('');
      setNewPassword('');
      setConfirmation('');
      toast('Senha alterada. Entre novamente com a nova senha.');
      window.dispatchEvent(new Event('bstore:unauthenticated'));
    });
  };
  return (
    <div className="profile-page">
      <PageHeading
        title="Perfil e segurança"
        description="Personalize a sua conta e mantenha o acesso protegido."
      />
      <section className="card panel">
        <h3 className="section-title">
          <User size={18} aria-hidden="true" /> O seu perfil
        </h3>
        <p className="profile-note">
          O nome de apresentação e a foto são guardados apenas neste navegador. Não alteram os dados
          da conta no servidor.
        </p>
        {profileError && <Notice>{profileError}</Notice>}
        <div className="profile-photo-row">
          {pic ? (
            <img src={pic} className="profile-photo" alt="Foto do perfil" />
          ) : (
            <div className="profile-photo" aria-hidden="true">
              {name.charAt(0).toUpperCase() || <User size={24} />}
            </div>
          )}
          <div>
            <button
              type="button"
              className="btn-secondary"
              onClick={() => fileRef.current?.click()}
            >
              <Camera size={16} aria-hidden="true" /> Alterar foto
            </button>
            <p className="field-hint">JPG ou PNG. Até 2 MB.</p>
            <input
              ref={fileRef}
              type="file"
              accept="image/jpeg,image/png"
              className="sr-only"
              tabIndex={-1}
              aria-label="Escolher foto de perfil"
              onChange={handlePhoto}
            />
          </div>
        </div>
        <form className="profile-form" onSubmit={saveProfile}>
          <Field id="profile-name" label="Nome de apresentação *">
            <input
              id="profile-name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              autoComplete="name"
              required
            />
          </Field>
          <Field id="profile-email" label="Email da conta">
            <input id="profile-email" value={user?.email || ''} disabled />
          </Field>
          <button className="btn-primary" type="submit">
            <Save size={16} aria-hidden="true" /> Guardar perfil
          </button>
        </form>
      </section>
      <section className="card panel">
        <h3 className="section-title">
          <KeyRound size={18} aria-hidden="true" /> Alterar senha
        </h3>
        <p className="profile-note">Depois de alterar a senha, terá de entrar novamente.</p>
        <form onSubmit={changePassword}>
          {password.error && <Notice>{password.error}</Notice>}
          <fieldset disabled={password.pending}>
            <Field
              id="current-password"
              label={user?.role === 'superuser' ? 'Senha atual' : 'Senha atual *'}
            >
              <input
                id="current-password"
                type={showPassword ? 'text' : 'password'}
                autoComplete="current-password"
                value={currentPassword}
                onChange={(e) => setCurrentPassword(e.target.value)}
                required={user?.role !== 'superuser'}
              />
            </Field>
            <Field
              id="new-password"
              label="Nova senha *"
              hint="Use uma senha única. Mínimo de 4 caracteres."
            >
              <input
                id="new-password"
                type={showPassword ? 'text' : 'password'}
                autoComplete="new-password"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                minLength={4}
                required
                aria-describedby="new-password-hint"
              />
            </Field>
            <Field id="confirm-password" label="Confirmar nova senha *">
              <input
                id="confirm-password"
                type={showPassword ? 'text' : 'password'}
                autoComplete="new-password"
                value={confirmation}
                onChange={(e) => setConfirmation(e.target.value)}
                minLength={4}
                required
                aria-invalid={
                  confirmation.length > 0 && confirmation !== newPassword ? true : undefined
                }
                aria-describedby={
                  confirmation.length > 0 && confirmation !== newPassword
                    ? 'password-match-error'
                    : undefined
                }
              />
              {confirmation.length > 0 && confirmation !== newPassword && (
                <p className="field-error" id="password-match-error">
                  As senhas não coincidem.
                </p>
              )}
            </Field>
            <div className="profile-password-actions">
              <button
                type="button"
                className="btn-secondary"
                onClick={() => setShowPassword((value) => !value)}
                aria-pressed={showPassword}
              >
                <span aria-hidden="true">{showPassword ? <EyeOff size={16} /> : <Eye size={16} />}</span>
                {showPassword ? 'Ocultar senhas' : 'Mostrar senhas'}
              </button>
              <button type="submit" className="btn-primary">
                {password.pending ? <Spinner size="small" /> : <KeyRound size={16} aria-hidden="true" />}
                {password.pending ? 'A alterar…' : 'Alterar senha'}
              </button>
            </div>
          </fieldset>
        </form>
      </section>

      <section className="card panel">
        <h3 className="section-title">
          <Store size={18} aria-hidden="true" /> Definições da loja
        </h3>
        <p className="profile-note">
          Configure as opções de funcionamento do seu ponto de venda e os métodos de pagamento.
        </p>

        <form onSubmit={salvarNomeLoja} className="store-settings-form" style={{ marginBottom: '2rem' }}>
          <Field id="nome-loja" label="Nome da loja" hint="Aparece no ecrã de venda e na tela do cliente.">
            <div style={{ display: 'flex', gap: '0.75rem' }}>
              <input
                id="nome-loja"
                type="text"
                value={nomeLoja}
                onChange={(e) => setNomeLoja(e.target.value)}
                placeholder="Ex: Mercearia Central"
                style={{ flex: 1 }}
              />
              <button type="submit" className="btn-primary" style={{ minHeight: '44px' }}>
                <Save size={16} /> Guardar
              </button>
            </div>
          </Field>
        </form>

        <div className="setting-toggle-card" style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          padding: '1.25rem',
          background: 'var(--bg-surface, rgba(255,255,255,0.03))',
          borderRadius: '0.75rem',
          border: '1px solid var(--border-color, rgba(255,255,255,0.1))',
          marginBottom: '2rem'
        }}>
          <div>
            <strong style={{ display: 'block', fontSize: '1.05rem', marginBottom: '0.25rem' }}>
              Controlo de caixa
            </strong>
            <span style={{ fontSize: '0.9rem', color: 'var(--text-secondary, #94a3b8)' }}>
              Abre e fecha caixa com contagem do dinheiro na gaveta no início e fim do dia.
            </span>
          </div>
          <button
            type="button"
            className={`btn-secondary ${controloCaixa ? 'active-toggle' : ''}`}
            onClick={toggleControloCaixa}
            style={{
              minHeight: '44px',
              minWidth: '100px',
              background: controloCaixa ? '#10b981' : undefined,
              color: controloCaixa ? '#ffffff' : undefined,
              borderColor: controloCaixa ? '#10b981' : undefined,
            }}
          >
            <Power size={16} />
            {controloCaixa ? 'Ativo' : 'Desativado'}
          </button>
        </div>

        <h4 style={{ fontSize: '1.1rem', marginBottom: '1rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <CreditCard size={18} /> Métodos de pagamento
        </h4>
        <p style={{ fontSize: '0.9rem', color: 'var(--text-secondary, #94a3b8)', marginBottom: '1rem' }}>
          Ative ou desative os métodos disponíveis no momento da venda.
        </p>

        <div className="payment-methods-list" style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', marginBottom: '1.5rem' }}>
          {metodos.map((m) => (
            <div
              key={m.id}
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                padding: '0.85rem 1.25rem',
                background: 'var(--bg-surface, rgba(255,255,255,0.03))',
                borderRadius: '0.75rem',
                border: '1px solid var(--border-color, rgba(255,255,255,0.08))',
              }}
            >
              <div>
                <strong style={{ fontSize: '1rem' }}>{m.nome}</strong>
                <span style={{ marginLeft: '0.75rem', fontSize: '0.8rem', opacity: 0.7, textTransform: 'uppercase' }}>
                  ({m.tipo})
                </span>
              </div>
              <button
                type="button"
                className="btn-secondary"
                onClick={() => toggleMetodo(m.id)}
                style={{
                  minHeight: '40px',
                  padding: '0.4rem 0.9rem',
                  background: m.ativo ? '#065f46' : 'transparent',
                  color: m.ativo ? '#ffffff' : 'var(--text-primary)',
                  borderColor: m.ativo ? '#065f46' : 'var(--border-color)',
                  fontWeight: 600,
                }}
              >
                {m.ativo ? 'Ativo' : 'Desligado'}
              </button>
            </div>
          ))}
        </div>

        <form onSubmit={adicionarMetodo} style={{ display: 'flex', gap: '0.75rem' }}>
          <input
            type="text"
            placeholder="Nome do método digital (ex: Moza, Carteira)"
            value={novoMetodoNome}
            onChange={(e) => setNovoMetodoNome(e.target.value)}
            style={{ flex: 1, minHeight: '44px' }}
          />
          <button type="submit" className="btn-secondary" disabled={!novoMetodoNome.trim()} style={{ minHeight: '44px' }}>
            <Plus size={16} /> Adicionar
          </button>
        </form>
      </section>
    </div>
  );
}
