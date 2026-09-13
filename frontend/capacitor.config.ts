import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.barraca.bstore',
  appName: 'FlexStock',
  webDir: '../src/main/resources/public',
  server: {
    // Servidor Render (gratuito) — base de dados PostgreSQL incluída
    url: 'https://bstore-q1x6.onrender.com',
    cleartext: false,
  },
};

export default config;
