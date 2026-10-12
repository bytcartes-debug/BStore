import type { PageId } from '../App';
import { LayoutDashboard, Menu, Package, ShoppingCart, Users } from 'lucide-react';
import './BottomNav.css';

interface BottomNavProps {
  currentPage: PageId;
  setCurrentPage: (page: PageId) => void;
  onOpenMore: () => void;
  menuOpen: boolean;
}

export default function BottomNav({
  currentPage,
  setCurrentPage,
  onOpenMore,
  menuOpen,
}: BottomNavProps) {
  const isMoreActive =
    menuOpen ||
    currentPage === 'categorias' ||
    currentPage === 'usuarios' ||
    currentPage === 'reposicao' ||
    currentPage === 'perfil';

  return (
    <nav className="bottom-nav" aria-label="Navegação inferior mobile">
      <button
        type="button"
        className={`bottom-nav-item${currentPage === 'vendas' && !menuOpen ? ' active' : ''}`}
        aria-current={currentPage === 'vendas' && !menuOpen ? 'page' : undefined}
        aria-label="Ir para Vendas"
        onClick={() => setCurrentPage('vendas')}
      >
        <span className="bottom-nav-icon" aria-hidden="true">
          <ShoppingCart size={20} strokeWidth={currentPage === 'vendas' && !menuOpen ? 2.4 : 2} />
        </span>
        <span className="bottom-nav-label">Vendas</span>
      </button>

      <button
        type="button"
        className={`bottom-nav-item${currentPage === 'produtos' && !menuOpen ? ' active' : ''}`}
        aria-current={currentPage === 'produtos' && !menuOpen ? 'page' : undefined}
        aria-label="Ir para Produtos"
        onClick={() => setCurrentPage('produtos')}
      >
        <span className="bottom-nav-icon" aria-hidden="true">
          <Package size={20} strokeWidth={currentPage === 'produtos' && !menuOpen ? 2.4 : 2} />
        </span>
        <span className="bottom-nav-label">Produtos</span>
      </button>

      <button
        type="button"
        className={`bottom-nav-item${currentPage === 'dashboard' && !menuOpen ? ' active' : ''}`}
        aria-current={currentPage === 'dashboard' && !menuOpen ? 'page' : undefined}
        aria-label="Ir para Painel e Visão Geral"
        onClick={() => setCurrentPage('dashboard')}
      >
        <span className="bottom-nav-icon" aria-hidden="true">
          <LayoutDashboard size={20} strokeWidth={currentPage === 'dashboard' && !menuOpen ? 2.4 : 2} />
        </span>
        <span className="bottom-nav-label">Painel</span>
      </button>

      <button
        type="button"
        className={`bottom-nav-item${currentPage === 'devedores' && !menuOpen ? ' active' : ''}`}
        aria-current={currentPage === 'devedores' && !menuOpen ? 'page' : undefined}
        aria-label="Ir para Devedores"
        onClick={() => setCurrentPage('devedores')}
      >
        <span className="bottom-nav-icon" aria-hidden="true">
          <Users size={20} strokeWidth={currentPage === 'devedores' && !menuOpen ? 2.4 : 2} />
        </span>
        <span className="bottom-nav-label">Devedores</span>
      </button>

      <button
        type="button"
        className={`bottom-nav-item${isMoreActive ? ' active' : ''}`}
        aria-label="Abrir mais opções e menu"
        aria-haspopup="dialog"
        aria-expanded={menuOpen}
        onClick={onOpenMore}
      >
        <span className="bottom-nav-icon" aria-hidden="true">
          <Menu size={20} strokeWidth={isMoreActive ? 2.4 : 2} />
        </span>
        <span className="bottom-nav-label">Mais</span>
      </button>
    </nav>
  );
}
