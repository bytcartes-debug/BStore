import { useState, useEffect, useCallback } from 'react';

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed'; platform: string }>;
}

export function usePwaInstall() {
  const [deferredPrompt, setDeferredPrompt] = useState<BeforeInstallPromptEvent | null>(null);
  const [isStandalone, setIsStandalone] = useState(false);
  const [isIos, setIsIos] = useState(false);
  const [showIosGuide, setShowIosGuide] = useState(false);

  useEffect(() => {
    if (typeof window === 'undefined') return;

    // Detect standalone mode
    const checkStandalone = () => {
      const matchMediaStandalone = window.matchMedia('(display-mode: standalone)').matches;
      const navStandalone = (navigator as unknown as { standalone?: boolean }).standalone === true;
      return matchMediaStandalone || navStandalone;
    };

    const standalone = checkStandalone();
    setIsStandalone(standalone);

    // Detect iOS
    const nav = typeof navigator !== 'undefined' ? navigator : null;
    const ua = nav?.userAgent || '';
    const isIosDevice =
      (/iPad|iPhone|iPod/.test(ua) ||
        (nav?.platform === 'MacIntel' && (nav.maxTouchPoints || 0) > 1)) &&
      !(window as unknown as { MSStream?: unknown }).MSStream;
    setIsIos(isIosDevice);

    // If iOS and not installed in standalone mode, check if dismissed
    if (isIosDevice && !standalone) {
      const dismissed = localStorage.getItem('bstore:hide-ios-install-guide') === 'true';
      if (!dismissed) {
        setShowIosGuide(true);
      }
    }

    // Listen for beforeinstallprompt on Android / PC
    const handleBeforeInstallPrompt = (e: Event) => {
      e.preventDefault();
      setDeferredPrompt(e as BeforeInstallPromptEvent);
    };

    const handleAppInstalled = () => {
      setDeferredPrompt(null);
      setIsStandalone(true);
      setShowIosGuide(false);
    };

    window.addEventListener('beforeinstallprompt', handleBeforeInstallPrompt);
    window.addEventListener('appinstalled', handleAppInstalled);

    return () => {
      window.removeEventListener('beforeinstallprompt', handleBeforeInstallPrompt);
      window.removeEventListener('appinstalled', handleAppInstalled);
    };
  }, []);

  const promptInstall = useCallback(async () => {
    if (!deferredPrompt) return;
    try {
      await deferredPrompt.prompt();
      const choice = await deferredPrompt.userChoice;
      if (choice.outcome === 'accepted') {
        setIsStandalone(true);
      }
    } catch {
      // Ignorar erros no fluxo de instalacao
    } finally {
      setDeferredPrompt(null);
    }
  }, [deferredPrompt]);

  const dismissIosGuide = useCallback((neverAgain = true) => {
    setShowIosGuide(false);
    if (neverAgain) {
      try {
        localStorage.setItem('bstore:hide-ios-install-guide', 'true');
      } catch {}
    }
  }, []);

  return {
    canInstall: Boolean(deferredPrompt) && !isStandalone,
    isStandalone,
    isIos,
    showIosGuide,
    promptInstall,
    dismissIosGuide,
  };
}
