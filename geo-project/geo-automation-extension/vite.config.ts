import { writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { defineConfig, type Plugin } from 'vite'
import vue from '@vitejs/plugin-vue'
import pkg from './package.json'
import { resolveAutomationExtensionProfile, type AutomationExtensionProfile } from './extension.profiles'

export default defineConfig(({ mode }) => {
  const profile = resolveAutomationExtensionProfile(mode === 'production' ? 'production' : 'development')

  return {
    plugins: [vue(), manifestPlugin(profile)],
    define: {
      __AUTOMATION_EXTENSION_VERSION__: JSON.stringify(pkg.version),
      __AUTOMATION_EXTENSION_PROFILE__: JSON.stringify(profile),
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

function manifestPlugin(profile: AutomationExtensionProfile): Plugin {
  return {
    name: 'geo-automation-extension-manifest',
    closeBundle() {
      const manifest = {
        manifest_version: 3,
        name: 'GEO Automation Executor',
        version: pkg.version,
        description: 'Stateless executor for GEO self-media automation v2.',
        action: {
          default_popup: 'index.html',
          default_title: 'GEO Automation',
        },
        background: {
          service_worker: 'assets/service-worker.js',
          type: 'module',
        },
        permissions: ['storage', 'tabs', 'scripting'],
        host_permissions: profile.hostPermissions,
        content_scripts: [
          {
            matches: [
              'https://mp.toutiao.com/*',
              'https://zhuanlan.zhihu.com/*',
              'https://www.zhihu.com/*',
              'https://creator.xiaohongshu.com/*',
              'https://baijiahao.baidu.com/*',
              'https://creator.douyin.com/*',
            ],
            js: ['assets/content-script.js'],
            run_at: 'document_idle',
            all_frames: true,
          },
        ],
      }
      writeFileSync(resolve(__dirname, 'dist/manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`)
    },
  }
}
