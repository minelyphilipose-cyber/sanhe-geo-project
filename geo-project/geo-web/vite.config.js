import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
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
            '/api': {
                target: 'http://localhost:8080',
                changeOrigin: true,
                timeout: 120000,
                proxyTimeout: 120000,
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
