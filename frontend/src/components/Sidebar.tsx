import { useEffect, useRef, useState } from 'react';
import {
  Store,
  LayoutDashboard,
  Tag,
  Package,
  ShoppingCart,
  Users,
  Shield,
  UserCircle,
  X,
} from 'lucide-react';
import type { PageId } from '../App';
import { useDialog } from '../utils/useDialog';
import './Sidebar.css';

interface SidebarProps {
  currentPage: PageId;
  setCurrentPage: (page: PageId) => void;
  userRole: 'superuser' | 'operator';
  isOpen: boolean;
  onClose: () => void;
}
const navItems = [
  { id: 'dashboard', label: 'Visão geral', icon: LayoutDashboard },
  { id: 'vendas', label: 'Vendas', icon: ShoppingCart },
  { id: 'produtos', label: 'Produtos', icon: Package },
  { id: 'categorias', label: 'Categorias', icon: Tag },
  { id: 'devedores', label: 'Devedores', icon: Users },
] as const;

export default function Sidebar({
  currentPage,
  setCurrentPage,
  userRole,
  isOpen,
  onClose,
}: SidebarProps) {
  const [mobile, setMobile] = useState(() => window.matchMedia('(max-width: 767px)').matches);
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const query = window.matchMedia('(max-width: 767px)');
    const change = () => setMobile(query.matches);
    query.addEventListener('change', change);
    return () => query.removeEventListener('change', change);
  }, []);
  useDialog(ref, mobile && isOpen);

  const content = (
    <>
      <div className="sidebar-logo">
        <span className="sidebar-logo-icon">
          <Store size={24} />
        </span>
        <div>
          <span className="sidebar-logo-text">BStore</span>
          <span className="sidebar-tagline">Gestão de loja</span>
        </div>
        {mobile && (
          <button className="icon-btn sidebar-close" aria-label="Fechar menu" onClick={onClose}>
            <X size={20} />
          </button>
        )}
      </div>
      <nav className="sidebar-nav" aria-label="Navegação principal">
        {navItems.map(({ id, label, icon: Icon }) => (
          <button
            key={id}
            className={`sidebar-item${currentPage === id ? ' active' : ''}`}
            aria-current={currentPage === id ? 'page' : undefined}
            onClick={() => setCurrentPage(id)}
          >
            <Icon size={19} aria-hidden="true" />
            <span>{label}</span>
          </button>
        ))}
        {userRole === 'superuser' && (
          <div className="sidebar-section">
            <p>Acessos</p>
            <button
              className={`sidebar-item${currentPage === 'usuarios' ? ' active' : ''}`}
              aria-current={currentPage === 'usuarios' ? 'page' : undefined}
              onClick={() => setCurrentPage('usuarios')}
            >
              <Shield size={19} />
              <span>Utilizadores</span>
            </button>
          </div>
        )}
      </nav>
      <div className="sidebar-footer">
        <button
          className={`sidebar-item${currentPage === 'perfil' ? ' active' : ''}`}
          aria-current={currentPage === 'perfil' ? 'page' : undefined}
          onClick={() => setCurrentPage('perfil')}
        >
          <UserCircle size={19} />
          <span>Perfil e segurança</span>
        </button>
        <p>O seu negócio, organizado.</p>
      </div>
    </>
  );
  if (mobile)
    return isOpen ? (
      <dialog
        id="navigation-menu"
        ref={ref}
        className="sidebar sidebar-dialog"
        aria-label="Menu de navegação"
        onCancel={(event) => {
          event.preventDefault();
          onClose();
        }}
        onClick={(event) => {
          if (event.target === event.currentTarget) {
            const box = event.currentTarget.getBoundingClientRect();
            if (event.clientX > box.right) onClose();
          }
        }}
      >
        {content}
      </dialog>
    ) : null;
  return (
    <aside id="navigation-menu" className="sidebar">
      {content}
    </aside>
  );
}
