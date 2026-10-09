import { useEffect, useState } from 'react';
import type { PageId } from '../App';
import { LogOut, Menu, Moon, Sun } from 'lucide-react';
import { Spinner } from './UI';
import './Header.css';

const titles: Record<PageId, string> = {
  dashboard: 'Visão Geral',
  categorias: 'Categorias',
  produtos: 'Produtos',
  vendas: 'Registo de Vendas',
  devedores: 'Gestão de Devedores',
  usuarios: 'Utilizadores & Acessos',
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
}: HeaderProps) {
  const [, refresh] = useState(0);

  useEffect(() => {
    const update = () => refresh((value) => value + 1);
    window.addEventListener('bstore:profile', update);
    return () => window.removeEventListener('bstore:profile', update);
  }, []);

  const pic = localStorage.getItem('profilePic');
  const name = localStorage.getItem('profileName') || userEmail.split('@')[0];

  return (
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
      </div>
      <div className="header-right">
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
  );
}
