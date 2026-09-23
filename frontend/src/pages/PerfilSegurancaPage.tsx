import React, { useState, useRef } from 'react';
import { UserCircle, Key, Save, Camera } from 'lucide-react';
import type { UserSession } from '../App';
import { apiFetch } from '../utils/api';

interface Props { user: UserSession | null; }

const PerfilSegurancaPage: React.FC<Props> = ({ user }) => {
  const [nome, setNome]   = useState(localStorage.getItem('profileFullName') || '');
  const [pic, setPic]     = useState(localStorage.getItem('profilePic') || '');
  const [senhaAtual, setSenhaAtual]       = useState('');
  const [novaSenha, setNovaSenha]         = useState('');
  const [confirmarSenha, setConfirmarSenha] = useState('');
  const [msgPerfil, setMsgPerfil]   = useState('');
  const [msgSenha, setMsgSenha]     = useState('');
  const [msgSenhaTipo, setMsgSenhaTipo] = useState<'ok' | 'err'>('ok');
  const [loadingSenha, setLoadingSenha] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);

  const handleFoto = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      const data = reader.result as string;
      setPic(data);
      localStorage.setItem('profilePic', data);
    };
    reader.readAsDataURL(file);
  };

  const handleSalvarPerfil = () => {
    localStorage.setItem('profileFullName', nome);
    localStorage.setItem('profileName', nome.split(' ')[0]);
    setMsgPerfil('✅ Perfil atualizado! Recarregue a página para ver o novo nome no cabeçalho.');
    setTimeout(() => setMsgPerfil(''), 4000);
  };

  const handleAlterarSenha = async () => {
    if (novaSenha.length < 4) { setMsgSenha('❌ A nova senha deve ter pelo menos 4 caracteres.'); setMsgSenhaTipo('err'); return; }
    if (novaSenha !== confirmarSenha) { setMsgSenha('❌ As senhas não coincidem.'); setMsgSenhaTipo('err'); return; }
    if (!user?.userId) { setMsgSenha('❌ Sessão inválida. Faça login novamente.'); setMsgSenhaTipo('err'); return; }

    setLoadingSenha(true);
    try {
      const r = await apiFetch(`/api/usuarios/${user.userId}/alterar-senha`, {
        method: 'POST',
        body: JSON.stringify({ senhaAtual, novaSenha }),
      });
      const d = await r.json().catch(() => ({}));
      if (!r.ok) {
        setMsgSenha(`❌ ${d.erro || 'Erro ao alterar senha.'}`); setMsgSenhaTipo('err');
      } else {
        setSenhaAtual(''); setNovaSenha(''); setConfirmarSenha('');
        setMsgSenha('✅ Senha alterada com sucesso!'); setMsgSenhaTipo('ok');
        setTimeout(() => setMsgSenha(''), 4000);
      }
    } catch {
      setMsgSenha('❌ Não foi possível ligar ao servidor.'); setMsgSenhaTipo('err');
    } finally {
      setLoadingSenha(false);
    }
  };

  return (
    <div style={{ maxWidth: 600 }}>
      {/* Foto de Perfil */}
      <div className="card" style={{ padding: 24, marginBottom: 20 }}>
        <h3 style={{ fontSize: 16, fontWeight: 700, marginBottom: 20, color: 'var(--text-primary)' }}>📸 Foto de Perfil</h3>
        <div style={{ display: 'flex', alignItems: 'center', gap: 20 }}>
          <div style={{ position: 'relative' }}>
            {pic
              ? <img src={pic} style={{ width: 80, height: 80, borderRadius: '50%', objectFit: 'cover', border: '3px solid var(--color-brand)' }} alt="Perfil" />
              : <div style={{ width: 80, height: 80, borderRadius: '50%', background: 'var(--color-brand)', color: 'white', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 32, fontWeight: 700 }}>
                  {nome.charAt(0).toUpperCase() || <UserCircle size={40} />}
                </div>
            }
          </div>
          <div>
            <button className="btn-secondary" onClick={() => fileRef.current?.click()} style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
              <Camera size={16} /> Alterar Foto
            </button>
            <p style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 6 }}>JPG ou PNG. Máx 2MB.</p>
            <input ref={fileRef} type="file" accept="image/*" style={{ display: 'none' }} onChange={handleFoto} />
          </div>
        </div>
      </div>

      {/* Dados Pessoais */}
      <div className="card" style={{ padding: 24, marginBottom: 20 }}>
        <h3 style={{ fontSize: 16, fontWeight: 700, marginBottom: 20, color: 'var(--text-primary)' }}>👤 Dados Pessoais</h3>
        <div className="form-group">
          <label>Nome Completo</label>
          <input value={nome} onChange={e => setNome(e.target.value)} placeholder="Seu nome" />
        </div>
        <div className="form-group">
          <label>E-mail</label>
          <input value={user?.email || ''} disabled style={{ opacity: 0.5 }} />
        </div>
        {msgPerfil && <p style={{ fontSize: 13, color: 'var(--color-brand)', marginBottom: 12 }}>{msgPerfil}</p>}
        <button className="btn-primary" onClick={handleSalvarPerfil} style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
          <Save size={16} /> Salvar Perfil
        </button>
      </div>

      {/* Alterar Senha */}
      <div className="card" style={{ padding: 24 }}>
        <h3 style={{ fontSize: 16, fontWeight: 700, marginBottom: 20, color: 'var(--text-primary)' }}>🔒 Alterar Senha</h3>
        <div className="form-group">
          <label>Senha Atual</label>
          <input type="password" value={senhaAtual} onChange={e => setSenhaAtual(e.target.value)} placeholder="••••••••" />
        </div>
        <div className="form-group">
          <label>Nova Senha</label>
          <input type="password" value={novaSenha} onChange={e => setNovaSenha(e.target.value)} placeholder="Mínimo 4 caracteres" />
        </div>
        <div className="form-group">
          <label>Confirmar Nova Senha</label>
          <input type="password" value={confirmarSenha} onChange={e => setConfirmarSenha(e.target.value)} placeholder="Repita a nova senha" />
        </div>
        {msgSenha && <p style={{ fontSize: 13, color: msgSenhaTipo === 'ok' ? 'var(--color-brand)' : 'var(--color-danger)', marginBottom: 12 }}>{msgSenha}</p>}
        <button className="btn-primary" onClick={handleAlterarSenha} disabled={loadingSenha} style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
          <Key size={16} /> {loadingSenha ? 'A alterar...' : 'Alterar Senha'}
        </button>
      </div>
    </div>
  );
};

export default PerfilSegurancaPage;
