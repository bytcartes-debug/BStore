import { useToast } from '../utils/toast';
import { useRef, useState } from 'react';
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
  const fileRef = useRef<HTMLInputElement>(null);
  const password = useMutation();
  const toast = useToast();
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
          <span aria-hidden="true">👤</span> O seu perfil
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
              {name.charAt(0).toUpperCase() || '👤'}
            </div>
          )}
          <div>
            <button
              type="button"
              className="btn-secondary"
              onClick={() => fileRef.current?.click()}
            >
              <span aria-hidden="true">📷</span> Alterar foto
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
            <span aria-hidden="true">💾</span> Guardar perfil
          </button>
        </form>
      </section>
      <section className="card panel">
        <h3 className="section-title">
          <span aria-hidden="true">🔑</span> Alterar senha
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
                <span aria-hidden="true">{showPassword ? '🙈' : '👁️'}</span>
                {showPassword ? 'Ocultar senhas' : 'Mostrar senhas'}
              </button>
              <button type="submit" className="btn-primary">
                {password.pending ? <Spinner size="small" /> : <span aria-hidden="true">🔑</span>}
                {password.pending ? 'A alterar…' : 'Alterar senha'}
              </button>
            </div>
          </fieldset>
        </form>
      </section>
    </div>
  );
}
