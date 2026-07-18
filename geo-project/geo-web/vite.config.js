import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
export const DIAGNOSTIC_PROXY_TIMEOUT_MS = 210_000;
export const DEFAULT_API_PROXY_TIMEOUT_MS = 120_000;
export default defineConfig({
    plugins: [vue()],
    resolve: {
        alias: {
            '@': '/src',
        },
    },
    server: {
        host: '0.0.0.0',
        port: 3000,
        proxy: {
            '/api/admin/model-diagnostics': {
                target: 'http://localhost:8080',
                changeOrigin: true,
                timeout: DIAGNOSTIC_PROXY_TIMEOUT_MS,
                proxyTimeout: DIAGNOSTIC_PROXY_TIMEOUT_MS,
            },
            '/api': {
                target: 'http://localhost:8080',
                changeOrigin: true,
                timeout: DEFAULT_API_PROXY_TIMEOUT_MS,
                proxyTimeout: DEFAULT_API_PROXY_TIMEOUT_MS,
            },
            '/oss': {
                target: 'http://192.168.112.175:9000',
                changeOrigin: true,
                rewrite: (path) => path.replace(/^\/oss/, ''),
            },
        },
    },
    css: {
        preprocessorOptions: {},
    },
});
