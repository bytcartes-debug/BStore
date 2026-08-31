import React from 'react';
import { Sun, Moon, LogOut, Menu, UserCircle } from 'lucide-react';
import type { PageId } from '../App';
import './Header.css';

const PAGE_TITLES: Record<PageId, string> = {
  dashboard:  '📊 Dashboard',
  categorias: '🏷️ Categorias',
  produtos:   '📦 Produtos',
  vendas:     '🛒 Vendas',
  devedores:  '👥 Devedores',
  usuarios:   '🛡️ Utilizadores',
  perfil:     '👤 Conta',
};

interface HeaderProps {
  currentPage: PageId;
  isDark: boolean;
  toggleTheme: () => void;
  onLogout: () => void;
  userEmail: string;
  onMenuClick: () => void;
  onProfileClick: () => void;
}

const Header: React.FC<HeaderProps> = ({ currentPage, isDark, toggleTheme, onLogout, userEmail, onMenuClick, onProfileClick }) => {
  const profilePic = localStorage.getItem('profilePic');
  const profileName = localStorage.getItem('profileName') || userEmail.split('@')[0];

  return (
    <header className="app-header">
      <div className="header-left">
        <button className="header-menu-btn" onClick={onMenuClick} title="Menu">
          <Menu size={20} />
        </button>
        <h1 className="header-title">{PAGE_TITLES[currentPage]}</h1>
      </div>

      <div className="header-right">
        <button className="header-icon-btn" onClick={toggleTheme} title="Alternar tema">
          {isDark ? <Sun size={18} /> : <Moon size={18} />}
        </button>

        <button
          onClick={onProfileClick}
          title="Minha Conta"
          style={{ background: 'transparent', border: 'none', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 8, padding: '4px 8px', borderRadius: 8 }}
        >
          {profilePic
            ? <img src={profilePic} alt="Avatar" className="header-avatar-img" />
            : <div className="header-avatar">{profileName.charAt(0).toUpperCase()}</div>
          }
          <span className="header-username">{profileName}</span>
          <UserCircle size={14} style={{ color: 'var(--text-muted)' }} />
        </button>

        <button className="header-icon-btn header-logout" onClick={onLogout} title="Sair">
          <LogOut size={18} />
        </button>
      </div>
    </header>
  );
};

export default Header;
