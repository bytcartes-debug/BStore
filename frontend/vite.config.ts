import { defineConfig, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'
import { visualizer } from 'rollup-plugin-visualizer'
import fs from 'node:fs'
import path from 'node:path'

function swVersionPlugin(): Plugin {
  return {
    name: 'sw-version-plugin',
    closeBundle() {
      const outDir = process.env.DOCKER_BUILD ? 'dist' : '../src/main/resources/public'
      const swPath = path.resolve(outDir, 'sw.js')
      if (fs.existsSync(swPath)) {
        const buildVersion = `b${Date.now().toString(36)}`
        let content = fs.readFileSync(swPath, 'utf-8')
        content = content.replace(/__SW_BUILD_HASH__/g, buildVersion)
        fs.writeFileSync(swPath, content, 'utf-8')
      }
    },
  }
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    swVersionPlugin(),
    visualizer({
      filename: 'stats.html',
      gzipSize: true,
      brotliSize: true,
      open: false,
    }),
  ],
  build: {
    // Em Docker o build vai para dist/, o Dockerfile copia para resources/public
    // Em desenvolvimento local vai direto para resources/public
    outDir: process.env.DOCKER_BUILD ? 'dist' : '../src/main/resources/public',
    emptyOutDir: true,
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: false,
      },
    },
  },
})
