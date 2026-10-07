import { useEffect, useRef, useState } from 'react';
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
  { id: 'dashboard', label: 'Visão Geral', emoji: '📊' },
  { id: 'vendas', label: 'Vendas & Caixa', emoji: '🛒' },
  { id: 'produtos', label: 'Produtos', emoji: '📦' },
  { id: 'categorias', label: 'Categorias', emoji: '🏷️' },
  { id: 'devedores', label: 'Devedores', emoji: '👥' },
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
        <span className="sidebar-logo-icon" aria-hidden="true">
          🏪
        </span>
        <div className="sidebar-logo-text-group">
          <span className="sidebar-logo-text">BStore</span>
          <span className="sidebar-tagline">Gestão de Loja</span>
        </div>
        {mobile && (
          <button className="icon-btn sidebar-close" aria-label="Fechar menu" onClick={onClose}>
            ✕
          </button>
        )}
      </div>

      <nav className="sidebar-nav" aria-label="Navegação principal">
        {navItems.map(({ id, label, emoji }) => (
          <button
            key={id}
            className={`sidebar-item${currentPage === id ? ' active' : ''}`}
            aria-current={currentPage === id ? 'page' : undefined}
            onClick={() => setCurrentPage(id)}
          >
            <span className="sidebar-emoji" aria-hidden="true">
              {emoji}
            </span>
            <span>{label}</span>
          </button>
        ))}

        {userRole === 'superuser' && (
          <div className="sidebar-section">
            <p className="sidebar-section-title">Administração</p>
            <button
              className={`sidebar-item${currentPage === 'usuarios' ? ' active' : ''}`}
              aria-current={currentPage === 'usuarios' ? 'page' : undefined}
              onClick={() => setCurrentPage('usuarios')}
            >
              <span className="sidebar-emoji" aria-hidden="true">
                👤
              </span>
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
          <span className="sidebar-emoji" aria-hidden="true">
            🔒
          </span>
          <span>Perfil & Segurança</span>
        </button>
        <p className="sidebar-motto">O seu negócio, organizado.</p>
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
