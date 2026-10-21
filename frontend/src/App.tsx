import { lazy, Suspense, useState, useEffect, useCallback, useRef } from 'react';
import { AlertTriangle } from 'lucide-react';
import './index.css';
import Sidebar from './components/Sidebar';
import Header from './components/Header';
import BottomNav from './components/BottomNav';
import LoginPage from './pages/LoginPage';
import VendasPage from './pages/VendasPage';
const DashboardPage = lazy(() => import('./pages/DashboardPage'));
const CategoriasPage = lazy(() => import('./pages/CategoriasPage'));
const ProdutosPage = lazy(() => import('./pages/ProdutosPage'));
const DevedoresPage = lazy(() => import('./pages/DevedoresPage'));
const UsuariosPage = lazy(() => import('./pages/UsuariosPage'));
const AuditoriaPage = lazy(() => import('./pages/AuditoriaPage'));
const PerfilSegurancaPage = lazy(() => import('./pages/PerfilSegurancaPage'));
const FaltaReporPage = lazy(() => import('./pages/FaltaReporPage'));
const ClienteDisplayPage = lazy(() => import('./pages/ClienteDisplayPage'));
import { verificarStockBaixo } from './utils/notificacoes';
import { apiFetch, apiRequest } from './utils/api';
import { limparDadosLocais } from './utils/offlineQueue';
import { Loading, LoadError, ToastProvider, Notice } from './components/UI';
import PageErrorBoundary from './components/PageErrorBoundary';
import { CameraScannerHost } from './components/CameraScannerHost';
import { usePwaInstall } from './utils/usePwaInstall';
import { IosInstallBanner } from './components/IosInstallBanner';
import { useToast } from './utils/toast';
import {
  setupNativeAppPlatform,
  setNativeStatusBarTheme,
  setKeepScreenAwake,
  setupAndroidBackButton,
} from './utils/nativeApp';

export type PageId =
  | 'dashboard'
  | 'categorias'
  | 'produtos'
  | 'vendas'
  | 'devedores'
  | 'usuarios'
  | 'auditoria'
  | 'perfil'
  | 'reposicao';
export interface UserSession {
  userId: number;
  email: string;
  role: 'superuser' | 'operator';
  diasRestantes: number;
}

function AppContent() {
  const [user, setUser] = useState<UserSession | null>(null);
  const [loadingSession, setLoadingSession] = useState(true);
  const [sessionError, setSessionError] = useState<string | null>(null);
  const [logoutError, setLogoutError] = useState<string | null>(null);
  const [loggingOut, setLoggingOut] = useState(false);
  const [currentPage, setCurrentPage] = useState<PageId>('dashboard');
  const [isDark, setIsDark] = useState(() => localStorage.getItem('bstore:theme') === 'dark');
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const mainRef = useRef<HTMLElement>(null);
  const toast = useToast();
  const { canInstall, showIosGuide, promptInstall, dismissIosGuide } = usePwaInstall();

  const checkSession = useCallback(async (signal?: AbortSignal) => {
    setLoadingSession(true);
    setSessionError(null);
    try {
      const response = await apiFetch('/api/auth/me', { signal });
      if (response.status === 401 || response.status === 403) {
        setUser(null);
        return;
      }
      if (!response.ok) throw new Error();
      const current = await response.json();
      setUser({
        userId: current.id,
        email: current.email,
        role: current.role,
        diasRestantes: current.diasRestantes ?? -1,
      });
    } catch {
      if (!signal?.aborted)
        setSessionError(
          'Não foi possível verificar a sessão. Verifique a ligação e tente novamente.',
        );
    } finally {
      if (!signal?.aborted) setLoadingSession(false);
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    const clearSession = () => {
      setUser(null);
      setCurrentPage('dashboard');
      setSidebarOpen(false);
      localStorage.removeItem('currentUser');
      void limparDadosLocais();
    };
    window.addEventListener('bstore:unauthenticated', clearSession);
    void checkSession(controller.signal);
    return () => {
      controller.abort();
      window.removeEventListener('bstore:unauthenticated', clearSession);
    };
  }, [checkSession]);

  useEffect(() => {
    void setupNativeAppPlatform(isDark);
  }, [isDark]);

  useEffect(() => {
    document.body.classList.toggle('dark-theme', isDark);
    localStorage.setItem('bstore:theme', isDark ? 'dark' : 'light');
    void setNativeStatusBarTheme(isDark);
  }, [isDark]);

  // Manter ecrã ativo exclusivamente nas páginas de Vendas
  useEffect(() => {
    const awake = currentPage === 'vendas';
    void setKeepScreenAwake(awake);
    return () => {
      void setKeepScreenAwake(false);
    };
  }, [currentPage]);

  // Registar controlo nativo do botão Voltar do Android
  useEffect(() => {
    const unregister = setupAndroidBackButton({
      hasOpenModal: () =>
        Boolean(document.querySelector('dialog[open], .modal-backdrop, .scanner-modal-content')),
      closeActiveModal: () => {
        const dialog = document.querySelector<HTMLDialogElement>('dialog[open]');
        if (dialog) {
          const cancelBtn = dialog.querySelector<HTMLButtonElement>(
            'button.modal-close-btn, button[aria-label="Fechar"]',
          );
          if (cancelBtn) {
            cancelBtn.click();
            return true;
          }
          dialog.close();
          return true;
        }
        const modalBackdrop = document.querySelector<HTMLElement>('.modal-backdrop');
        if (modalBackdrop) {
          const closeBtn = modalBackdrop.querySelector<HTMLButtonElement>(
            'button.icon-btn, button[aria-label="Fechar"]',
          );
          if (closeBtn) {
            closeBtn.click();
            return true;
          }
        }
        return false;
      },
      isPaymentInProgress: () => {
        return (
          currentPage === 'vendas' &&
          Boolean(document.querySelector('dialog[open], .modal-backdrop')) &&
          Boolean(window.history.state?.bstorePagamento)
        );
      },
      isRootPage: () => currentPage === 'dashboard',
      navigateToRoot: () => setCurrentPage('dashboard'),
      showToast: (msg) => toast(msg),
    });

    return () => unregister();
  }, [currentPage, toast]);

  useEffect(() => {
    if (user && currentPage === 'dashboard') void verificarStockBaixo();
  }, [user, currentPage]);
  useEffect(() => {
    if (user) mainRef.current?.focus();
  }, [currentPage, user]);

  const navigate = (page: PageId) => {
    setCurrentPage(page);
    setSidebarOpen(false);
  };
  const handleLogout = async () => {
    if (loggingOut) return;
    setLoggingOut(true);
    setLogoutError(null);
    try {
      await apiRequest('/api/auth/logout', { method: 'POST' });
      localStorage.removeItem('currentUser');
      void limparDadosLocais();
      setUser(null);
      setCurrentPage('dashboard');
    } catch {
      setLogoutError('Não foi possível terminar a sessão. Tente sair novamente.');
    } finally {
      setLoggingOut(false);
    }
  };

  const renderPage = () => {
    switch (currentPage) {
      case 'categorias':
        return <CategoriasPage />;
      case 'produtos':
        return <ProdutosPage />;
      case 'vendas':
        return <VendasPage currentUser={user} />;
      case 'devedores':
        return <DevedoresPage />;
      case 'usuarios':
        return user?.role === 'superuser' ? (
          <UsuariosPage user={user} />
        ) : (
          <DashboardPage navigate={navigate} />
        );
      case 'auditoria':
        return user?.role === 'superuser' ? (
          <AuditoriaPage user={user} />
        ) : (
          <DashboardPage navigate={navigate} />
        );
      case 'perfil':
        return <PerfilSegurancaPage user={user} />;
      case 'reposicao':
        return <FaltaReporPage navigate={navigate} />;
      default:
        return <DashboardPage navigate={navigate} />;
    }
  };

  if (loadingSession) return <Loading fullScreen label="A carregar o BStore…" />;
  if (sessionError)
    return (
      <div className="loading-screen">
        <h1>BStore</h1>
        <LoadError message={sessionError} retry={() => void checkSession()} />
      </div>
    );
  if (!user) return <LoginPage setUser={setUser} />;

  return (
    <div className="app-shell">
      <a href="#main-content" className="skip-link">
        Saltar para o conteúdo
      </a>
      <Sidebar
        currentPage={currentPage}
        setCurrentPage={navigate}
        userRole={user.role}
        isOpen={sidebarOpen}
        onClose={() => setSidebarOpen(false)}
      />
      <div className="main-content">
        {user.role !== 'superuser' && user.diasRestantes !== -1 && user.diasRestantes <= 7 && (
          <div className="expiration-banner" role="status">
            <AlertTriangle size={18} aria-hidden="true" style={{ flexShrink: 0 }} />
            <p>
              {user.diasRestantes <= 0
                ? 'A sua conta expirou. Contacte o administrador.'
                : `A sua conta expira em ${user.diasRestantes} dia${user.diasRestantes !== 1 ? 's' : ''}. Contacte o administrador para renovar.`}
            </p>
          </div>
        )}
        <Header
          currentPage={currentPage}
          isDark={isDark}
          toggleTheme={() => setIsDark((value) => !value)}
          onLogout={handleLogout}
          loggingOut={loggingOut}
          userEmail={user.email}
          onMenuClick={() => setSidebarOpen(true)}
          menuOpen={sidebarOpen}
          onProfileClick={() => navigate('perfil')}
          canInstall={canInstall}
          onInstall={promptInstall}
        />
        {showIosGuide && <IosInstallBanner onDismiss={dismissIosGuide} />}
        <main id="main-content" ref={mainRef} tabIndex={-1} className="page-content">
          {logoutError && <Notice>{logoutError}</Notice>}
          <PageErrorBoundary key={currentPage}>
            <div key={currentPage} className="page-view-animated">
              <Suspense fallback={<Loading />}>{renderPage()}</Suspense>
            </div>
          </PageErrorBoundary>
        </main>
      </div>
      <BottomNav
        currentPage={currentPage}
        setCurrentPage={navigate}
        onOpenMore={() => setSidebarOpen(true)}
        menuOpen={sidebarOpen}
      />
    </div>
  );
}

export default function App() {
  if (typeof window !== 'undefined' && window.location.pathname === '/cliente') {
    return (
      <Suspense fallback={<Loading fullScreen label="A carregar ecrã do cliente..." />}>
        <ClienteDisplayPage />
      </Suspense>
    );
  }
  return (
    <ToastProvider>
      <AppContent />
      <CameraScannerHost />
    </ToastProvider>
  );
}
