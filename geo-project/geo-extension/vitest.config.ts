import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import pkg from './package.json'
import { resolveExtensionProfile } from './extension.profiles'

export default defineConfig({
  plugins: [vue()],
  define: {
    __EXTENSION_VERSION__: JSON.stringify(pkg.version),
    __EXTENSION_PROFILE__: JSON.stringify(resolveExtensionProfile('test')),
  },
  test: {
    environment: 'jsdom',
    globals: true,
  },
  resolve: {
    alias: {
      '@': new URL('./src', import.meta.url).pathname,
    },
  },
})
