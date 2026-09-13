import React, { useState, useEffect } from 'react';
import './index.css';
import Sidebar from './components/Sidebar';
import Header from './components/Header';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import CategoriasPage from './pages/CategoriasPage';
import ProdutosPage from './pages/ProdutosPage';
import VendasPage from './pages/VendasPage';
import DevedoresPage from './pages/DevedoresPage';
import UsuariosPage from './pages/UsuariosPage';
import PerfilSegurancaPage from './pages/PerfilSegurancaPage';
import { pedirPermissaoNotificacoes, verificarStockBaixo } from './utils/notificacoes';
import { AlertTriangle } from 'lucide-react';

export type PageId = 'dashboard' | 'categorias' | 'produtos' | 'vendas' | 'devedores' | 'usuarios' | 'perfil';

export interface UserSession {
  userId: number;
  email: string;
  role: 'superuser' | 'operator';
  diasRestantes: number; // -1 = permanente
}

const App: React.FC = () => {
  const [user, setUser]           = useState<UserSession | null>(null);
  const [currentPage, setCurrentPage] = useState<PageId>('dashboard');
  const [isDark, setIsDark]       = useState(true);
  const [sidebarOpen, setSidebarOpen] = useState(false);

  useEffect(() => {
    const saved = localStorage.getItem('currentUser');
    if (saved) setUser(JSON.parse(saved));
  }, []);

  useEffect(() => { document.body.className = isDark ? '' : 'light-theme'; }, [isDark]);

  // Notificações ao fazer login
  useEffect(() => { if (user) pedirPermissaoNotificacoes(); }, [user]);

  // Verifica stock baixo ao abrir dashboard
  useEffect(() => { if (user && currentPage === 'dashboard') verificarStockBaixo(); }, [user, currentPage]);

  const handleLogout = () => { localStorage.removeItem('currentUser'); setUser(null); };

  const renderPage = () => {
    switch (currentPage) {
      case 'dashboard':  return <DashboardPage />;
      case 'categorias': return <CategoriasPage />;
      case 'produtos':   return <ProdutosPage />;
      case 'vendas':     return <VendasPage />;
      case 'devedores':  return <DevedoresPage />;
      case 'usuarios':   return user?.role === 'superuser' ? <UsuariosPage /> : <DashboardPage />;
      case 'perfil':     return <PerfilSegurancaPage user={user} />;
      default:           return <DashboardPage />;
    }
  };

  // Banner de aviso de expiração (≤ 7 dias restantes, não permanente, não superuser)
  const ExpiracaoBanner = () => {
    if (!user || user.role === 'superuser') return null;
    if (user.diasRestantes === -1 || user.diasRestantes > 7) return null;
    const msg = user.diasRestantes === 0
      ? '⛔ A tua conta expirou. Contacta o administrador.'
      : `⚠️ A tua conta expira em ${user.diasRestantes} dia${user.diasRestantes !== 1 ? 's' : ''}. Contacta o administrador para renovar.`;
    return (
      <div style={{
        background: user.diasRestantes === 0 ? '#7f1d1d' : '#78350f',
        color: '#fff', padding: '10px 18px', fontSize: 13, fontWeight: 600,
        display: 'flex', alignItems: 'center', gap: 8,
      }}>
        <AlertTriangle size={16} /> {msg}
      </div>
    );
  };

  if (!user) return <LoginPage setUser={setUser} />;

  return (
    <div className="app-shell">
      {sidebarOpen && (
        <div
          onClick={() => setSidebarOpen(false)}
          style={{ position:'fixed', inset:0, background:'rgba(0,0,0,0.5)', zIndex:99, display:'none' }}
          className="sidebar-backdrop"
        />
      )}

      <Sidebar
        currentPage={currentPage}
        setCurrentPage={(p) => { setCurrentPage(p); setSidebarOpen(false); }}
        userRole={user.role}
        isOpen={sidebarOpen}
        onClose={() => setSidebarOpen(false)}
      />

      <div className="main-content">
        <ExpiracaoBanner />
        <Header
          currentPage={currentPage}
          isDark={isDark}
          toggleTheme={() => setIsDark(!isDark)}
          onLogout={handleLogout}
          userEmail={user.email}
          onMenuClick={() => setSidebarOpen(!sidebarOpen)}
          onProfileClick={() => { setCurrentPage('perfil'); setSidebarOpen(false); }}
        />
        <main className="page-content">
          {renderPage()}
        </main>
      </div>
    </div>
  );
};

export default App;
