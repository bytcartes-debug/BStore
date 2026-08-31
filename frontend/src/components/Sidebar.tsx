import React from 'react';
import { Store, LayoutDashboard, Tag, Package, ShoppingCart, Users, Shield, UserCircle, X } from 'lucide-react';
import type { PageId } from '../App';
import './Sidebar.css';

interface SidebarProps {
  currentPage: PageId;
  setCurrentPage: (page: PageId) => void;
  userRole: 'superuser' | 'operator';
  isOpen: boolean;
  onClose: () => void;
}

const navItems: { id: PageId; label: string; icon: React.ReactNode }[] = [
  { id: 'dashboard',  label: 'Dashboard',   icon: <LayoutDashboard size={18} /> },
  { id: 'categorias', label: 'Categorias',   icon: <Tag size={18} /> },
  { id: 'produtos',   label: 'Produtos',     icon: <Package size={18} /> },
  { id: 'vendas',     label: 'Vendas',       icon: <ShoppingCart size={18} /> },
  { id: 'devedores',  label: 'Devedores',    icon: <Users size={18} /> },
];

const Sidebar: React.FC<SidebarProps> = ({ currentPage, setCurrentPage, userRole, isOpen, onClose }) => {
  return (
    <>
      {isOpen && <div className="sidebar-overlay" onClick={onClose} />}
      <aside className={`sidebar ${isOpen ? 'sidebar-open' : ''}`}>
        <div className="sidebar-logo">
          <div className="sidebar-logo-icon">
            <Store size={22} />
          </div>
          <span className="sidebar-logo-text">Flex Stock</span>
          <button className="sidebar-close-btn" onClick={onClose}>
            <X size={20} />
          </button>
        </div>

        <nav className="sidebar-nav">
          <p className="sidebar-section-label">Principal</p>
          {navItems.map(item => (
            <button
              key={item.id}
              className={`sidebar-item ${currentPage === item.id ? 'active' : ''}`}
              onClick={() => setCurrentPage(item.id)}
            >
              {item.icon}
              <span>{item.label}</span>
            </button>
          ))}

          {userRole === 'superuser' && (
            <>
              <p className="sidebar-section-label" style={{ marginTop: '16px' }}>Administração</p>
              <button
                className={`sidebar-item ${currentPage === 'usuarios' ? 'active' : ''}`}
                onClick={() => setCurrentPage('usuarios')}
              >
                <Shield size={18} />
                <span>Utilizadores</span>
              </button>
            </>
          )}

          <p className="sidebar-section-label" style={{ marginTop: '16px' }}>Conta</p>
          <button
            className={`sidebar-item ${currentPage === 'perfil' ? 'active' : ''}`}
            onClick={() => setCurrentPage('perfil')}
          >
            <UserCircle size={18} />
            <span>Perfil & Segurança</span>
          </button>
        </nav>
      </aside>
    </>
  );
};

export default Sidebar;
