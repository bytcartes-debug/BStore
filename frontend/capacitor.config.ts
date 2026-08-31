import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.barraca.bstore',
  appName: 'FlexStock',
  webDir: '../src/main/resources/public',
  server: {
    // ⚠️ Coloque aqui o IP do seu computador (veja com ipconfig no CMD)
    // Exemplo: url: 'http://192.168.1.105:8080',
    url: 'http://SEU_IP_AQUI:8080',
    cleartext: true,
  },
};

export default config;
