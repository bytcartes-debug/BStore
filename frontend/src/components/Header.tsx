import React from 'react';
import { Sun, Moon, LogOut, Menu } from 'lucide-react';
import type { PageId } from '../App';
import './Header.css';

const PAGE_TITLES: Record<PageId, string> = {
  dashboard:  '📊 Dashboard',
  categorias: '🏷️ Categorias',
  produtos:   '📦 Produtos',
  vendas:     '🛒 Vendas',
  devedores:  '👥 Devedores',
  usuarios:   '🛡️ Utilizadores',
  perfil:     '👤 Perfil & Segurança',
};

interface HeaderProps {
  currentPage: PageId;
  isDark: boolean;
  toggleTheme: () => void;
  onLogout: () => void;
  userEmail: string;
  onMenuClick: () => void;
}

const Header: React.FC<HeaderProps> = ({ currentPage, isDark, toggleTheme, onLogout, userEmail, onMenuClick }) => {
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

        <div className="header-user">
          {profilePic
            ? <img src={profilePic} alt="Avatar" className="header-avatar-img" />
            : <div className="header-avatar">{profileName.charAt(0).toUpperCase()}</div>
          }
          <span className="header-username">{profileName}</span>
        </div>

        <button className="header-icon-btn header-logout" onClick={onLogout} title="Sair">
          <LogOut size={18} />
        </button>
      </div>
    </header>
  );
};

export default Header;
