import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  build: {
    // Em Docker o build vai para dist/, o Dockerfile copia para resources/public
    // Em desenvolvimento local vai direto para resources/public
    outDir: process.env.DOCKER_BUILD ? 'dist' : '../src/main/resources/public',
    emptyOutDir: true,
  },
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    }
  }
})
