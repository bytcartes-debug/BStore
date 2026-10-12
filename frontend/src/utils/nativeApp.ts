import { Capacitor } from '@capacitor/core';
import { App as CapApp } from '@capacitor/app';
import { StatusBar, Style } from '@capacitor/status-bar';
import { SplashScreen } from '@capacitor/splash-screen';
import { Keyboard, KeyboardResize } from '@capacitor/keyboard';
import { KeepAwake } from '@capacitor-community/keep-awake';

export const isNativeAndroid = (): boolean =>
  Capacitor.isNativePlatform() && Capacitor.getPlatform() === 'android';

/** Atualiza a Status Bar do Android conforme o tema da aplicação */
export async function setNativeStatusBarTheme(isDark: boolean): Promise<void> {
  if (!Capacitor.isNativePlatform()) return;
  try {
    await StatusBar.setStyle({ style: isDark ? Style.Dark : Style.Light });
    await StatusBar.setBackgroundColor({ color: isDark ? '#152520' : '#17634f' });
  } catch {
    // Ignorar se o dispositivo não suportar ajuste dinâmico da status bar
  }
}

/** Configurações de arranque nativo do Capacitor (Splash, Keyboard, Status Bar) */
export async function setupNativeAppPlatform(isDark: boolean): Promise<void> {
  if (!Capacitor.isNativePlatform()) return;

  try {
    // Fechar splash screen logo que a app React estiver montada
    await SplashScreen.hide();
  } catch {}

  try {
    // Ajustar cor e contraste da barra de estado
    await setNativeStatusBarTheme(isDark);
  } catch {}

  try {
    // Configurar teclado nativo para redimensionar o corpo sem tapar botões
    await Keyboard.setResizeMode({ mode: KeyboardResize.Body });
    await Keyboard.setAccessoryBarVisible({ isVisible: false });
  } catch {}
}

/** Mantém o ecrã ligado em páginas ativas de balcão (Vendas / Cliente) */
export async function setKeepScreenAwake(awake: boolean): Promise<void> {
  if (!Capacitor.isNativePlatform()) return;
  try {
    if (awake) {
      await KeepAwake.keepAwake();
    } else {
      await KeepAwake.allowSleep();
    }
  } catch {
    // Silencioso se o dispositivo não suportar WAKE_LOCK
  }
}

let lastBackPressTime = 0;

/** Regista o listener do botão físico / gestual Voltar do Android */
export function setupAndroidBackButton(handlers: {
  hasOpenModal: () => boolean;
  closeActiveModal: () => boolean;
  isPaymentInProgress: () => boolean;
  isRootPage: () => boolean;
  navigateToRoot: () => void;
  showToast: (msg: string) => void;
}): () => void {
  if (!Capacitor.isNativePlatform()) return () => {};

  const listenerPromise = CapApp.addListener('backButton', () => {
    // 1. Se estiver no meio de um pagamento, pedir confirmação antes de sair
    if (handlers.isPaymentInProgress()) {
      const confirmExit = window.confirm(
        'Tem a certeza que deseja cancelar o pagamento e voltar? O carrinho será mantido.',
      );
      if (!confirmExit) return;
      handlers.closeActiveModal();
      return;
    }

    // 2. Se houver modal ou bottom-sheet aberto, fecha o modal primeiro
    if (handlers.hasOpenModal()) {
      const closed = handlers.closeActiveModal();
      if (closed) return;
    }

    // 3. Se não estiver na página principal (dashboard), volta à página principal
    if (!handlers.isRootPage()) {
      handlers.navigateToRoot();
      return;
    }

    // 4. Na raiz: duplo toque no botão voltar (num intervalo de 2 segundos) para sair
    const now = Date.now();
    if (now - lastBackPressTime < 2000) {
      void CapApp.exitApp();
    } else {
      lastBackPressTime = now;
      handlers.showToast('Toque novamente para sair do BStore.');
    }
  });

  return () => {
    void listenerPromise.then((handle) => handle.remove());
  };
}
