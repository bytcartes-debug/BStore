import { useToast } from '../utils/toast';
import { useEffect, useRef, useState } from 'react';
import {
  User,
  Camera,
  Save,
  KeyRound,
  Eye,
  EyeOff,
  Store,
  Plus,
  Power,
  CreditCard,
  Download,
  CheckCircle,
  Smartphone,
  Bot,
  MessageSquare,
  Send,
  ShieldCheck,
  Copy,
  Check,
} from 'lucide-react';
import type { UserSession } from '../App';
import { apiRequest } from '../utils/api';
import { useMutation } from '../utils/useResource';
import { PageHeading, Field, Notice, Spinner, Modal } from '../components/UI';
import { usePwaInstall } from '../utils/usePwaInstall';
import { useIa } from '../utils/useIa';

export default function PerfilSegurancaPage({ user }: { user: UserSession | null }) {
  const { canInstall, isStandalone, isIos, promptInstall } = usePwaInstall();
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

  // 2FA TOTP (RFC 6238)
  const [totpAtivo, setTotpAtivo] = useState(false);
  const [carregandoTotp, setCarregandoTotp] = useState(false);
  const [configurando2fa, setConfigurando2fa] = useState(false);
  const [secret2fa, setSecret2fa] = useState('');
  const [otpauthUri, setOtpauthUri] = useState('');
  const [codigoConfirmacao2fa, setCodigoConfirmacao2fa] = useState('');
  const [ativando2fa, setAtivando2fa] = useState(false);
  const [desativando2fa, setDesativando2fa] = useState(false);
  const [senhaDesativar2fa, setSenhaDesativar2fa] = useState('');
  const [mostrarModalDesativar2fa, setMostrarModalDesativar2fa] = useState(false);
  const [copiado, setCopiado] = useState(false);
  const [erro2fa, setErro2fa] = useState<string | null>(null);

  // Notificações WhatsApp (OpenWA)
  const [whatsappActivo, setWhatsappActivo] = useState(true);
  const [whatsappTelefone, setWhatsappTelefone] = useState('');
  const [whatsappConectado, setWhatsappConectado] = useState(false);
  const [whatsappStatusMsg, setWhatsappStatusMsg] = useState('');
  const [testandoWhatsapp, setTestandoWhatsapp] = useState(false);
  const [salvandoWhatsapp, setSalvandoWhatsapp] = useState(false);

  const fileRef = useRef<HTMLInputElement>(null);
  const password = useMutation();
  const toast = useToast();

  const ia = useIa();
  const [iaAtiva, setIaAtiva] = useState(false);
  const [iaProvedor, setIaProvedor] = useState('claude');
  const [iaApiKey, setIaApiKey] = useState('');
  const [iaLimiteDiario, setIaLimiteDiario] = useState(50);
  const [iaLimiteMensal, setIaLimiteMensal] = useState(500);

  useEffect(() => {
    if (ia.status) {
      setIaAtiva(ia.status.ativa);
      setIaProvedor(ia.status.provedor || 'claude');
      setIaLimiteDiario(ia.status.limiteDiario || 50);
      setIaLimiteMensal(ia.status.limiteMensal || 500);
    }
  }, [ia.status]);

  const salvarConfigIa = async (e: React.FormEvent) => {
    e.preventDefault();
    await ia.atualizarConfig({
      activa: iaAtiva,
      provedor: iaProvedor,
      apiKey: iaApiKey.trim() || undefined,
      limiteDiario: iaLimiteDiario,
      limiteMensal: iaLimiteMensal,
    });
  };

  const carregarDefinicoes = async () => {
    try {
      const [listaMetodos, def] = await Promise.all([
        apiRequest<any[]>('/api/metodos-pagamento').catch(() => []),
        apiRequest<any>('/api/definicoes').catch(() => ({ controloCaixa: false, nomeLoja: '' })),
      ]);
      setMetodos(listaMetodos || []);
      setControloCaixa(Boolean(def?.controloCaixa));
      setNomeLoja(def?.nomeLoja || '');
      setWhatsappActivo(def?.whatsappNotificacoesActivas !== false);
      setWhatsappTelefone(def?.whatsappTelefoneDono || '');
      setWhatsappConectado(Boolean(def?.whatsappConectado));
      setWhatsappStatusMsg(def?.whatsappMensagemStatus || '');
    } catch {}
  };

  const carregarStatus2fa = async () => {
    try {
      setCarregandoTotp(true);
      const res = await apiRequest<{ ativo: boolean }>('/api/auth/2fa/status');
      setTotpAtivo(Boolean(res?.ativo));
    } catch {
      setTotpAtivo(false);
    } finally {
      setCarregandoTotp(false);
    }
  };

  const iniciarConfiguracao2fa = async () => {
    setErro2fa(null);
    setCodigoConfirmacao2fa('');
    try {
      const res = await apiRequest<{ secret: string; otpauthUri: string }>('/api/auth/2fa/gerar', {
        method: 'POST',
      });
      setSecret2fa(res.secret);
      setOtpauthUri(res.otpauthUri);
      setConfigurando2fa(true);
    } catch (e: any) {
      toast('Erro ao gerar chave 2FA: ' + (e?.message || 'Falha de comunicação'));
    }
  };

  const confirmarAtivacao2fa = async (e: React.FormEvent) => {
    e.preventDefault();
    setErro2fa(null);
    setAtivando2fa(true);
    try {
      await apiRequest('/api/auth/2fa/ativar', {
        method: 'POST',
        body: JSON.stringify({ secret: secret2fa, codigo: codigoConfirmacao2fa }),
      });
      toast('✅ Autenticação de Dois Fatores ativada com sucesso!');
      setConfigurando2fa(false);
      setSecret2fa('');
      setOtpauthUri('');
      setCodigoConfirmacao2fa('');
      await carregarStatus2fa();
    } catch (e: any) {
      setErro2fa(e?.message || 'Código incorreto. Verifique a hora do seu telemóvel.');
    } finally {
      setAtivando2fa(false);
    }
  };

  const desativar2fa = async (e: React.FormEvent) => {
    e.preventDefault();
    setErro2fa(null);
    setDesativando2fa(true);
    try {
      await apiRequest('/api/auth/2fa/desativar', {
        method: 'POST',
        body: JSON.stringify({ senha: senhaDesativar2fa }),
      });
      toast('Autenticação de Dois Fatores desativada.');
      setMostrarModalDesativar2fa(false);
      setSenhaDesativar2fa('');
      await carregarStatus2fa();
    } catch (e: any) {
      setErro2fa(e?.message || 'Palavra-passe incorreta.');
    } finally {
      setDesativando2fa(false);
    }
  };

  const copiarChave = () => {
    if (!secret2fa) return;
    navigator.clipboard.writeText(secret2fa);
    setCopiado(true);
    toast('Chave secreta copiada para a área de transferência.');
    setTimeout(() => setCopiado(false), 3000);
  };

  useEffect(() => {
    carregarDefinicoes();
    carregarStatus2fa();
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

  const salvarConfigWhatsapp = async (e: React.FormEvent) => {
    e.preventDefault();
    setSalvandoWhatsapp(true);
    try {
      const res = await apiRequest<any>('/api/definicoes', {
        method: 'PUT',
        body: JSON.stringify({
          whatsappNotificacoesActivas: whatsappActivo,
          whatsappTelefoneDono: whatsappTelefone.trim(),
        }),
      });
      setWhatsappConectado(Boolean(res?.whatsappConectado));
      setWhatsappStatusMsg(res?.whatsappMensagemStatus || '');
      toast('Configurações de WhatsApp guardadas com sucesso.');
    } catch (err: any) {
      toast(err?.message || 'Erro ao guardar configurações de WhatsApp.');
    } finally {
      setSalvandoWhatsapp(false);
    }
  };

  const testarWhatsapp = async () => {
    setTestandoWhatsapp(true);
    try {
      const res = await apiRequest<{ sucesso: boolean; mensagem: string; erro?: string }>('/api/definicoes/whatsapp/testar', {
        method: 'POST',
      });
      if (res.sucesso) {
        toast('✅ ' + (res.mensagem || 'Mensagem de teste enviada!'));
      } else {
        toast('❌ ' + (res.erro || 'Falha ao testar envio.'));
      }
    } catch (err: any) {
      toast('❌ ' + (err?.message || 'Erro ao testar envio do WhatsApp.'));
    } finally {
      setTestandoWhatsapp(false);
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

      {/* SECÇÃO 2FA / TOTP (RFC 6238) */}
      <section className="card panel">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '0.75rem' }}>
          <div>
            <h3 className="section-title">
              <ShieldCheck size={18} aria-hidden="true" style={{ color: totpAtivo ? '#10b981' : undefined }} />
              Autenticação de Dois Fatores (2FA / TOTP)
            </h3>
            <p className="profile-note">
              Proteja o acesso administrativo com códigos temporários compatíveis com Google Authenticator, Authy e Microsoft Authenticator (RFC 6238).
            </p>
          </div>
          <span className={`badge ${totpAtivo ? 'badge-success' : 'badge-warning'}`} style={{ fontSize: '0.8rem', padding: '0.35rem 0.65rem' }}>
            {totpAtivo ? '2FA Ativo' : '2FA Desativado'}
          </span>
        </div>

        {totpAtivo ? (
          <div style={{ marginTop: '1rem', background: 'rgba(16, 185, 129, 0.08)', padding: '1rem', borderRadius: '0.75rem', border: '1px solid rgba(16, 185, 129, 0.2)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
              <div>
                <strong style={{ display: 'block', color: '#10b981', marginBottom: '0.25rem' }}>
                  A sua conta está protegida por verificação em duas etapas.
                </strong>
                <span style={{ fontSize: '0.875rem', color: 'var(--text-secondary, #94a3b8)' }}>
                  O código de 6 dígitos será exigido em cada início de sessão neste e noutros dispositivos.
                </span>
              </div>
              <button
                type="button"
                className="btn-danger btn-sm"
                onClick={() => {
                  setErro2fa(null);
                  setSenhaDesativar2fa('');
                  setMostrarModalDesativar2fa(true);
                }}
              >
                Desativar 2FA
              </button>
            </div>
          </div>
        ) : (
          <div style={{ marginTop: '1rem' }}>
            {!configurando2fa ? (
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem', background: 'var(--bg-subtle, rgba(0,0,0,0.02))', padding: '1rem', borderRadius: '0.75rem', border: '1px solid var(--border-color, rgba(255,255,255,0.08))' }}>
                <div>
                  <strong style={{ display: 'block', marginBottom: '0.25rem' }}>
                    Recomendado para administradores e gerentes de loja.
                  </strong>
                  <span style={{ fontSize: '0.875rem', color: 'var(--text-secondary, #94a3b8)' }}>
                    Impede invasões mesmo que terceiros descubram a sua palavra-passe.
                  </span>
                </div>
                <button
                  type="button"
                  className="btn-primary"
                  onClick={iniciarConfiguracao2fa}
                  disabled={carregandoTotp}
                >
                  <ShieldCheck size={16} />
                  <span>Configurar 2FA Agora</span>
                </button>
              </div>
            ) : (
              <div style={{ background: 'var(--bg-subtle, rgba(0,0,0,0.03))', padding: '1.25rem', borderRadius: '0.75rem', border: '1px solid var(--border-color, rgba(255,255,255,0.1))' }}>
                <h4 style={{ marginBottom: '0.5rem', fontSize: '1rem' }}>1. Digitalize o código QR na sua aplicação de autenticação</h4>
                <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary, #94a3b8)', marginBottom: '1rem' }}>
                  Abra o <strong>Google Authenticator</strong> ou <strong>Authy</strong> no seu telemóvel e aponte a câmara para o código abaixo:
                </p>

                <div style={{ display: 'flex', gap: '1.5rem', flexWrap: 'wrap', alignItems: 'center', marginBottom: '1.25rem' }}>
                  <div style={{ background: '#ffffff', padding: '0.5rem', borderRadius: '0.5rem', boxShadow: '0 2px 8px rgba(0,0,0,0.1)' }}>
                    <img
                      src={`https://api.qrserver.com/v1/create-qr-code/?size=160x160&data=${encodeURIComponent(otpauthUri)}`}
                      alt="Código QR 2FA"
                      width={160}
                      height={160}
                      style={{ display: 'block' }}
                    />
                  </div>
                  <div style={{ flex: '1 1 260px' }}>
                    <label style={{ display: 'block', fontSize: '0.8rem', color: 'var(--text-secondary, #94a3b8)', marginBottom: '0.35rem' }}>
                      Ou introduza a chave secreta manualmente no telemóvel:
                    </label>
                    <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
                      <code style={{ background: 'var(--bg-surface, rgba(0,0,0,0.05))', padding: '0.5rem 0.75rem', borderRadius: '4px', letterSpacing: '0.1em', fontWeight: 'bold', fontSize: '0.9rem' }}>
                        {secret2fa}
                      </code>
                      <button type="button" className="icon-btn" onClick={copiarChave} title="Copiar chave">
                        {copiado ? <Check size={16} color="#10b981" /> : <Copy size={16} />}
                      </button>
                    </div>
                  </div>
                </div>

                <h4 style={{ marginBottom: '0.5rem', fontSize: '1rem' }}>2. Confirme com o código gerado no telemóvel</h4>
                {erro2fa && <Notice>{erro2fa}</Notice>}
                <form onSubmit={confirmarAtivacao2fa} style={{ display: 'flex', gap: '0.75rem', alignItems: 'flex-end', flexWrap: 'wrap' }}>
                  <div style={{ maxWidth: '200px' }}>
                    <label htmlFor="codigo-confirmacao-2fa" style={{ display: 'block', fontSize: '0.8rem', marginBottom: '0.35rem' }}>
                      Código de 6 dígitos:
                    </label>
                    <input
                      id="codigo-confirmacao-2fa"
                      type="text"
                      inputMode="numeric"
                      maxLength={6}
                      placeholder="000000"
                      value={codigoConfirmacao2fa}
                      onChange={(e) => setCodigoConfirmacao2fa(e.target.value.replace(/\D/g, ''))}
                      style={{ textAlign: 'center', fontSize: '1.25rem', letterSpacing: '0.25em', fontFamily: 'monospace', fontWeight: 'bold' }}
                      required
                    />
                  </div>
                  <button type="submit" className="btn-primary" disabled={ativando2fa || codigoConfirmacao2fa.length < 6}>
                    {ativando2fa ? <Spinner size="small" /> : <Check size={16} />}
                    <span>{ativando2fa ? 'A validar…' : 'Validar e Ativar 2FA'}</span>
                  </button>
                  <button type="button" className="btn-secondary" onClick={() => setConfigurando2fa(false)} disabled={ativando2fa}>
                    Cancelar
                  </button>
                </form>
              </div>
            )}
          </div>
        )}

        {/* Modal para Desativar 2FA com Confirmação de Senha */}
        {mostrarModalDesativar2fa && (
          <Modal title="Desativar Autenticação de Dois Fatores" onClose={() => setMostrarModalDesativar2fa(false)}>
            <form onSubmit={desativar2fa} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <p style={{ margin: 0, fontSize: '0.875rem', color: 'var(--text-secondary, #94a3b8)' }}>
                Para confirmar a desativação do 2FA, por favor introduza a sua palavra-passe atual:
              </p>
              {erro2fa && <Notice>{erro2fa}</Notice>}
              <Field id="senha-desativar-2fa" label="Palavra-passe atual">
                <input
                  id="senha-desativar-2fa"
                  type="password"
                  placeholder="Introduza a sua palavra-passe"
                  value={senhaDesativar2fa}
                  onChange={(e) => setSenhaDesativar2fa(e.target.value)}
                  autoFocus
                  required
                />
              </Field>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem', marginTop: '0.5rem' }}>
                <button
                  type="button"
                  className="btn-secondary"
                  onClick={() => setMostrarModalDesativar2fa(false)}
                  disabled={desativando2fa}
                >
                  Cancelar
                </button>
                <button type="submit" className="btn-danger" disabled={desativando2fa || !senhaDesativar2fa}>
                  {desativando2fa ? <Spinner size="small" /> : 'Confirmar e Desativar 2FA'}
                </button>
              </div>
            </form>
          </Modal>
        )}
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

      {/* PAINEL DE NOTIFICAÇÕES WHATSAPP (OpenWA) */}
      <section className="panel" style={{ marginTop: '2rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem', flexWrap: 'wrap', gap: '0.5rem' }}>
          <h3 style={{ fontSize: '1.25rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <MessageSquare size={20} color="#25D366" /> Notificações WhatsApp (OpenWA)
          </h3>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
            <span
              style={{
                fontSize: '0.8rem',
                padding: '0.25rem 0.6rem',
                borderRadius: '999px',
                fontWeight: 600,
                background: whatsappConectado ? 'rgba(16, 185, 129, 0.15)' : 'rgba(234, 88, 12, 0.15)',
                color: whatsappConectado ? '#10b981' : '#ea580c',
                border: `1px solid ${whatsappConectado ? 'rgba(16, 185, 129, 0.3)' : 'rgba(234, 88, 12, 0.3)'}`,
              }}
            >
              {whatsappConectado ? 'WhatsApp Conectado' : 'Sessão Desligada'}
            </span>
            <button
              type="button"
              className="btn-secondary"
              onClick={() => setWhatsappActivo(!whatsappActivo)}
              style={{
                minHeight: '40px',
                minWidth: '95px',
                background: whatsappActivo ? '#10b981' : undefined,
                color: whatsappActivo ? '#ffffff' : undefined,
                borderColor: whatsappActivo ? '#10b981' : undefined,
                fontWeight: 600,
              }}
            >
              <Power size={15} />
              {whatsappActivo ? 'Ativo' : 'Desativado'}
            </button>
          </div>
        </div>

        {!whatsappConectado && (
          <div
            style={{
              padding: '0.75rem 1rem',
              marginBottom: '1rem',
              borderRadius: '8px',
              background: 'rgba(234, 88, 12, 0.1)',
              border: '1px solid rgba(234, 88, 12, 0.3)',
              color: '#ea580c',
              fontSize: '0.875rem',
              display: 'flex',
              alignItems: 'center',
              gap: '0.5rem',
            }}
          >
            ⚠️ {whatsappStatusMsg || 'Sessão do WhatsApp desligada. Volte a ler o QR no OpenWA.'}
          </div>
        )}

        <p style={{ fontSize: '0.9rem', color: 'var(--text-secondary, #94a3b8)', marginBottom: '1.25rem', lineHeight: 1.5 }}>
          Envia relatórios automáticos e imediatos para o WhatsApp do dono da loja em tempo real para:
          <strong> novas vendas</strong>, <strong>produtos adicionados</strong>, <strong>baixo estoque</strong> e <strong>abertura/fecho de caixa</strong>.
          As credenciais do servidor OpenWA são configuradas de forma segura nas variáveis de ambiente do sistema.
        </p>

        <form onSubmit={salvarConfigWhatsapp} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <Field
            id="whatsapp-phone"
            label="Número de WhatsApp do Dono / Gerente *"
            hint="Código do país + DDD/Prefixo + Número, apenas dígitos (ex: 258841234567 para Moçambique ou 5511999999999 para o Brasil)."
          >
            <input
              id="whatsapp-phone"
              type="tel"
              placeholder="Ex: 258841234567"
              value={whatsappTelefone}
              onChange={(e) => setWhatsappTelefone(e.target.value)}
              style={{ minHeight: '44px' }}
            />
          </Field>

          <div style={{ display: 'flex', gap: '0.75rem', marginTop: '0.5rem', flexWrap: 'wrap' }}>
            <button
              type="submit"
              className="btn-primary"
              disabled={salvandoWhatsapp}
              style={{ minHeight: '44px', display: 'inline-flex', alignItems: 'center', gap: '8px' }}
            >
              {salvandoWhatsapp ? <Spinner size="small" /> : <Save size={16} />}
              Guardar Configurações WhatsApp
            </button>
            <button
              type="button"
              className="btn-secondary"
              onClick={testarWhatsapp}
              disabled={testandoWhatsapp}
              style={{ minHeight: '44px', display: 'inline-flex', alignItems: 'center', gap: '8px' }}
            >
              {testandoWhatsapp ? <Spinner size="small" /> : <Send size={16} />}
              Testar Envio no WhatsApp
            </button>
          </div>
        </form>
      </section>

      {/* PAINEL DE INTELIGÊNCIA ARTIFICIAL */}
      <section className="panel" style={{ marginTop: '2rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem', flexWrap: 'wrap', gap: '0.5rem' }}>
          <h3 style={{ fontSize: '1.25rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <Bot size={20} color="#ea580c" /> Inteligência Artificial (BStore IA)
          </h3>
          <button
            type="button"
            className="btn-secondary"
            onClick={() => setIaAtiva(!iaAtiva)}
            style={{
              minHeight: '44px',
              minWidth: '100px',
              background: iaAtiva ? '#10b981' : undefined,
              color: iaAtiva ? '#ffffff' : undefined,
              borderColor: iaAtiva ? '#10b981' : undefined,
              fontWeight: 600,
            }}
          >
            <Power size={16} />
            {iaAtiva ? 'Ativa' : 'Desativada'}
          </button>
        </div>

        <p style={{ fontSize: '0.9rem', color: 'var(--text-secondary, #94a3b8)', marginBottom: '1.25rem' }}>
          Ativa o assistente de cadastro por foto, extração de faturas, resumos do dia e mensagens de cobrança inteligentes.
        </p>

        {ia.status && (
          <div
            style={{
              background: 'var(--bg-subtle, rgba(255,255,255,0.03))',
              border: '1px solid var(--border-color, rgba(255,255,255,0.08))',
              borderRadius: '0.75rem',
              padding: '1rem 1.25rem',
              marginBottom: '1.5rem',
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.85rem', marginBottom: '0.5rem' }}>
              <span>Consumo de hoje: <strong>{ia.status.usadosHoje} / {ia.status.limiteDiario} chamadas</strong></span>
              <span>Mês: <strong>{ia.status.usadosMes} / {ia.status.limiteMensal}</strong></span>
            </div>
            <div style={{ height: '6px', borderRadius: '3px', background: 'var(--border-color)', overflow: 'hidden' }}>
              <div
                style={{
                  height: '100%',
                  width: `${Math.min(100, Math.round((ia.status.usadosHoje / (ia.status.limiteDiario || 1)) * 100))}%`,
                  background: ia.status.usadosHoje >= ia.status.limiteDiario ? '#dc2626' : '#10b981',
                }}
              />
            </div>
          </div>
        )}

        <form onSubmit={salvarConfigIa} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '1rem' }}>
            <Field id="ia-provedor" label="Provedor de IA">
              <select
                id="ia-provedor"
                value={iaProvedor}
                onChange={(e) => setIaProvedor(e.target.value)}
              >
                <option value="claude">Anthropic Claude (Sonnet 5 - Padrão)</option>
                <option value="gemini">Google Gemini</option>
                <option value="openai">OpenAI (GPT-4o mini)</option>
              </select>
            </Field>

            <Field id="ia-key" label="Chave de API da loja (opcional)" hint="O sistema inclui o Claude Sonnet 5 pré-configurado. Deixe vazio para usar a IA padrão.">
              <input
                id="ia-key"
                type="password"
                placeholder="Chave padrão do Claude ativa no servidor"
                value={iaApiKey}
                onChange={(e) => setIaApiKey(e.target.value)}
              />
            </Field>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '1rem' }}>
            <Field id="ia-lim-dia" label="Limite Diário de Chamadas">
              <input
                id="ia-lim-dia"
                type="number"
                min="5"
                max="500"
                value={iaLimiteDiario}
                onChange={(e) => setIaLimiteDiario(Number(e.target.value))}
              />
            </Field>

            <Field id="ia-lim-mes" label="Limite Mensal de Chamadas">
              <input
                id="ia-lim-mes"
                type="number"
                min="50"
                max="5000"
                value={iaLimiteMensal}
                onChange={(e) => setIaLimiteMensal(Number(e.target.value))}
              />
            </Field>
          </div>

          <div style={{ display: 'flex', gap: '0.75rem', marginTop: '0.5rem', flexWrap: 'wrap' }}>
            <button type="submit" className="btn-primary" style={{ minHeight: '44px', display: 'inline-flex', alignItems: 'center', gap: '8px' }}>
              <Save size={16} /> Salvar Definições de IA
            </button>
            <button
              type="button"
              className="btn-secondary"
              onClick={() => void ia.testarLigacao()}
              disabled={ia.testando}
              style={{ minHeight: '44px', display: 'inline-flex', alignItems: 'center', gap: '8px' }}
            >
              {ia.testando ? <Spinner size="small" /> : <Bot size={16} />}
              Testar Ligação com a IA
            </button>
          </div>
        </form>
      </section>

      <section className="panel" style={{ marginTop: '2rem' }}>
        <h2 style={{ fontSize: '1.1rem', fontWeight: 600, display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.75rem' }}>
          <Smartphone size={18} /> Aplicação no Dispositivo (PWA)
        </h2>
        {isStandalone ? (
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', color: '#065f46', fontSize: '0.95rem' }}>
            <CheckCircle size={18} />
            <span>O BStore está instalado e a ser executado em modo de aplicação independente.</span>
          </div>
        ) : canInstall ? (
          <div>
            <p style={{ fontSize: '0.9rem', color: 'var(--text-secondary)', marginBottom: '1rem' }}>
              Instale o BStore no seu telemóvel ou computador para acesso instantâneo sem barra do navegador.
            </p>
            <button
              type="button"
              className="btn-primary"
              onClick={() => void promptInstall()}
              style={{ minHeight: '44px', display: 'inline-flex', alignItems: 'center', gap: '8px' }}
            >
              <Download size={16} /> Instalar app
            </button>
          </div>
        ) : isIos ? (
          <p style={{ fontSize: '0.9rem', color: 'var(--text-secondary)', lineHeight: 1.5 }}>
            No Safari do iPhone/iPad: toque no botão <strong>Partilhar</strong> e selecione <strong>Adicionar ao ecrã principal</strong> para instalar a app.
          </p>
        ) : (
          <p style={{ fontSize: '0.9rem', color: 'var(--text-secondary)' }}>
            Aplicação web pronta para instalação nos navegadores compatíveis (Chrome, Edge, Safari).
          </p>
        )}
      </section>
    </div>
  );
}
