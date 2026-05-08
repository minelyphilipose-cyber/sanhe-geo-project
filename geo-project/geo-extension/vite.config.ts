import { writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { defineConfig, type Plugin } from 'vite'
import vue from '@vitejs/plugin-vue'
import pkg from './package.json'

export default defineConfig({
  plugins: [vue(), extensionManifestPlugin()],
  define: {
    __EXTENSION_VERSION__: JSON.stringify(pkg.version),
  },
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    rollupOptions: {
      input: {
        popup: resolve(__dirname, 'index.html'),
        'service-worker': resolve(__dirname, 'src/service-worker/index.ts'),
        'content-script': resolve(__dirname, 'src/content-script/index.ts'),
        'admin-bridge-content-script': resolve(__dirname, 'src/admin-bridge/index.ts'),
      },
      output: {
        entryFileNames: 'assets/[name].js',
        chunkFileNames: 'assets/[name].js',
        assetFileNames: 'assets/[name].[ext]',
      },
    },
  },
})

function extensionManifestPlugin(): Plugin {
  return {
    name: 'geo-extension-manifest',
    closeBundle() {
      const configuredAdminOrigin = process.env.VITE_GEO_ADMIN_ORIGIN
      const adminMatches = configuredAdminOrigin
        ? [`${configuredAdminOrigin.replace(/\/$/, '')}/*`]
        : [
            'http://localhost:3000/*',
            'http://127.0.0.1:3000/*',
            'http://localhost:5173/*',
            'http://127.0.0.1:5173/*',
            'http://*/*',
          ]
      const manifest = {
        manifest_version: 3,
        name: '三合星链自媒体助手',
        version: pkg.version,
        description: 'Semi-auto publishing companion for GEO self-media tasks.',
        action: {
          default_popup: 'index.html',
          default_title: 'GEO',
        },
        background: {
          service_worker: 'assets/service-worker.js',
          type: 'module',
        },
        permissions: ['alarms', 'cookies', 'storage', 'tabs', 'scripting'],
        host_permissions: [
          'http://127.0.0.1:8080/*',
          'http://localhost:8080/*',
          'https://*.toutiao.com/*',
          'https://*.zhihu.com/*',
        ],
        content_scripts: [
          {
            matches: ['https://mp.toutiao.com/*', 'https://www.zhihu.com/*', 'https://zhuanlan.zhihu.com/*'],
            js: ['assets/content-script.js'],
            run_at: 'document_idle',
          },
          ...(adminMatches.length
            ? [{
                matches: adminMatches,
                js: ['assets/admin-bridge-content-script.js'],
                run_at: 'document_start',
              }]
            : []),
        ],
      }
      writeFileSync(resolve(__dirname, 'dist/manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`)
    },
  }
}
