import { useEffect, useState, useCallback } from 'react';
import type { PageId } from '../App';
import { Download, LogOut, Menu, Moon, Sun } from 'lucide-react';
import { Spinner } from './UI';
import { StatusBadge, type StatusVariant } from './ui/StatusBadge';
import { SyncResilienceModal } from './SyncResilienceModal';
import { listarVendasPendentes } from '../utils/offlineQueue';
import './Header.css';

const titles: Record<PageId, string> = {
  dashboard: 'Visão Geral',
  categorias: 'Categorias',
  produtos: 'Produtos',
  vendas: 'Registo de Vendas',
  devedores: 'Gestão de Devedores',
  usuarios: 'Utilizadores & Acessos',
  auditoria: 'Trilha de Auditoria',
  perfil: 'Perfil & Segurança',
  reposicao: 'Falta Repor',
};

interface HeaderProps {
  currentPage: PageId;
  isDark: boolean;
  toggleTheme: () => void;
  onLogout: () => void;
  loggingOut: boolean;
  userEmail: string;
  onMenuClick: () => void;
  menuOpen: boolean;
  onProfileClick: () => void;
  canInstall?: boolean;
  onInstall?: () => void;
}

export default function Header({
  currentPage,
  isDark,
  toggleTheme,
  onLogout,
  loggingOut,
  userEmail,
  onMenuClick,
  menuOpen,
  onProfileClick,
  canInstall,
  onInstall,
}: HeaderProps) {
  const [, refresh] = useState(0);
  const [isOnline, setIsOnline] = useState(() => navigator.onLine);
  const [pendentesCount, setPendentesCount] = useState(0);
  const [syncModalOpen, setSyncModalOpen] = useState(false);

  const atualizarPendentes = useCallback(async () => {
    try {
      const lista = await listarVendasPendentes();
      setPendentesCount(lista.length);
    } catch {
      setPendentesCount(0);
    }
  }, []);

  useEffect(() => {
    const update = () => refresh((value) => value + 1);
    window.addEventListener('bstore:profile', update);
    return () => window.removeEventListener('bstore:profile', update);
  }, []);

  useEffect(() => {
    void atualizarPendentes();

    const handleOnline = () => setIsOnline(true);
    const handleOffline = () => setIsOnline(false);
    const handlePendentes = () => void atualizarPendentes();

    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);
    window.addEventListener('bstore:pendentes-atualizadas', handlePendentes);

    // Verificação periódica de conectividade a cada 30 segundos
    const interval = setInterval(() => {
      void atualizarPendentes();
      if (navigator.onLine) {
        fetch('/api/health', { method: 'GET', credentials: 'same-origin' })
          .then((res) => setIsOnline(res.ok))
          .catch(() => setIsOnline(false));
      } else {
        setIsOnline(false);
      }
    }, 30000);

    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
      window.removeEventListener('bstore:pendentes-atualizadas', handlePendentes);
      clearInterval(interval);
    };
  }, [atualizarPendentes]);

  const pic = localStorage.getItem('profilePic');
  const name = localStorage.getItem('profileName') || userEmail.split('@')[0];

  let badgeLabel = 'Online';
  let badgeVariant: StatusVariant = 'online';

  if (!isOnline) {
    badgeLabel = pendentesCount > 0 ? `Offline (${pendentesCount})` : 'Offline';
    badgeVariant = 'danger';
  } else if (pendentesCount > 0) {
    badgeLabel = `Sincronizar (${pendentesCount})`;
    badgeVariant = 'warning';
  }

  return (
    <>
      <header className="app-header">
        <div className="header-left">
          <button
            className="icon-btn header-menu-btn"
            onClick={onMenuClick}
            aria-label="Abrir menu"
            aria-expanded={menuOpen}
            aria-controls={menuOpen ? 'navigation-menu' : undefined}
          >
            <Menu size={20} strokeWidth={2} aria-hidden="true" />
          </button>
          <h1 className="header-title">{titles[currentPage]}</h1>
          <button
            type="button"
            onClick={() => setSyncModalOpen(true)}
            title="Abrir Monitor de Resiliência Offline e Sincronização"
            style={{
              background: 'transparent',
              border: 'none',
              padding: 0,
              cursor: 'pointer',
              display: 'inline-flex',
            }}
          >
            <StatusBadge
              label={badgeLabel}
              variant={badgeVariant}
              pulse={true}
              className="header-status-badge"
            />
          </button>
        </div>
      <div className="header-right">
        {canInstall && onInstall && (
          <button
            type="button"
            className="icon-btn header-install-btn"
            onClick={onInstall}
            aria-label="Instalar app"
            title="Instalar BStore no dispositivo"
          >
            <Download size={18} strokeWidth={2} aria-hidden="true" />
          </button>
        )}
        <button
          className="icon-btn header-theme-btn"
          onClick={toggleTheme}
          aria-label={isDark ? 'Ativar tema claro' : 'Ativar tema escuro'}
          title={isDark ? 'Tema claro' : 'Tema escuro'}
        >
          {isDark ? (
            <Sun size={18} strokeWidth={2} aria-hidden="true" />
          ) : (
            <Moon size={18} strokeWidth={2} aria-hidden="true" />
          )}
        </button>
        <button
          className="header-profile"
          onClick={onProfileClick}
          aria-label={`Abrir perfil de ${name}`}
          title="Perfil e segurança"
        >
          {pic ? (
            <img src={pic} alt="" className="header-avatar" />
          ) : (
            <span className="header-avatar">{name.charAt(0).toUpperCase()}</span>
          )}
          <span className="header-username">{name}</span>
        </button>
        <button
          className="icon-btn header-logout"
          onClick={onLogout}
          disabled={loggingOut}
          aria-label="Terminar sessão"
          title="Terminar sessão"
        >
          {loggingOut ? (
            <Spinner size="small" />
          ) : (
            <LogOut size={18} strokeWidth={2} aria-hidden="true" />
          )}
        </button>
      </div>
    </header>
    <SyncResilienceModal
      isOpen={syncModalOpen}
      onClose={() => setSyncModalOpen(false)}
    />
  </>
);
}
