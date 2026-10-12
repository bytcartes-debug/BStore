import type { CapacitorConfig } from '@capacitor/cli';

/**
 * BStore — Configuração do Capacitor para Android
 *
 * A URL remota pode ser fornecida via variável de ambiente CAP_SERVER_URL no build.
 * Se CAP_SERVER_URL não estiver definida, a app carrega o frontend empacotado localmente
 * a partir de webDir com o esquema seguro https://localhost.
 */
const capServerUrl = process.env.CAP_SERVER_URL?.trim();

const config: CapacitorConfig = {
  appId: 'com.barraca.bstore',
  appName: 'BStore',
  webDir: '../src/main/resources/public',
  server: {
    url: capServerUrl || undefined,
    cleartext: false,
    androidScheme: 'https',
    allowNavigation: [
      '*.up.railway.app',
      '*.railway.app',
      'localhost',
      ...(capServerUrl ? [new URL(capServerUrl).hostname] : []),
    ],
  },
  plugins: {
    SplashScreen: {
      launchShowDuration: 1200,
      launchAutoHide: true,
      backgroundColor: '#17634f',
      androidSplashResourceName: 'splash',
      androidScaleType: 'CENTER_CROP',
      showSpinner: false,
    },
    StatusBar: {
      backgroundColor: '#17634f',
      style: 'DARK',
    },
    Keyboard: {
      resize: 'body',
      resizeOnFullScreen: true,
    },
  },
};

export default config;
