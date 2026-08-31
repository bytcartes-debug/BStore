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

export type PageId = 'dashboard' | 'categorias' | 'produtos' | 'vendas' | 'devedores' | 'usuarios' | 'perfil';

export interface UserSession {
  email: string;
  role: 'superuser' | 'operator';
}

const App: React.FC = () => {
  const [user, setUser] = useState<UserSession | null>(null);
  const [currentPage, setCurrentPage] = useState<PageId>('dashboard');
  const [isDark, setIsDark] = useState(true);
  const [sidebarOpen, setSidebarOpen] = useState(false);

  useEffect(() => {
    const saved = localStorage.getItem('currentUser');
    if (saved) setUser(JSON.parse(saved));
  }, []);

  useEffect(() => {
    document.body.className = isDark ? '' : 'light-theme';
  }, [isDark]);

  const handleLogout = () => {
    localStorage.removeItem('currentUser');
    setUser(null);
  };

  const renderPage = () => {
    switch (currentPage) {
      case 'dashboard':   return <DashboardPage />;
      case 'categorias':  return <CategoriasPage />;
      case 'produtos':    return <ProdutosPage />;
      case 'vendas':      return <VendasPage />;
      case 'devedores':   return <DevedoresPage />;
      case 'usuarios':    return user?.role === 'superuser' ? <UsuariosPage /> : <DashboardPage />;
      case 'perfil':      return <PerfilSegurancaPage user={user} />;
      default:            return <DashboardPage />;
    }
  };

  if (!user) {
    return <LoginPage setUser={setUser} />;
  }

  return (
    <div className="app-shell">
      {/* Mobile overlay */}
      {sidebarOpen && (
        <div
          onClick={() => setSidebarOpen(false)}
          style={{
            position: 'fixed', inset: 0,
            background: 'rgba(0,0,0,0.5)',
            zIndex: 99,
            display: 'none',
          }}
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
