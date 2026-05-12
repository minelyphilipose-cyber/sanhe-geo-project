import { writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { defineConfig, type Plugin } from 'vite'
import vue from '@vitejs/plugin-vue'
import pkg from './package.json'
import { resolveExtensionProfile, resolveExtensionProfileName, type ExtensionProfile } from './extension.profiles'

export default defineConfig(({ mode }) => {
  const profileName = resolveExtensionProfileName(
    process.env.VITE_GEO_EXTENSION_PROFILE ?? (mode === 'production' ? 'production' : 'test'),
  )
  const profile = resolveExtensionProfile(profileName)
  process.env.VITE_GEO_EXTENSION_PROFILE = profileName

  return {
    plugins: [vue(), extensionManifestPlugin(profile)],
    define: {
      __EXTENSION_VERSION__: JSON.stringify(pkg.version),
      __EXTENSION_PROFILE__: JSON.stringify(profile),
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
  }
})

function extensionManifestPlugin(profile: ExtensionProfile): Plugin {
  return {
    name: 'geo-extension-manifest',
    closeBundle() {
      const adminMatches = profile.adminOrigins.map(origin => `${origin.replace(/\/$/, '')}/*`)
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
        host_permissions: profile.hostPermissions,
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
