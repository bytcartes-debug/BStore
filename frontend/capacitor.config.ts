import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.barraca.bstore',
  appName: 'FlexStock',
  webDir: '../src/main/resources/public',
  server: {
    // Servidor na nuvem (livemy.app) — todos os utilizadores usam este endereço
    url: 'https://vh-prod-bstore-submain-c0c3b4-3d9f0aef.livemy.site',
    cleartext: false,
  },
};

export default config;
