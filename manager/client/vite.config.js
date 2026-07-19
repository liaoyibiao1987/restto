import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

// Vite 配置：dev server 代理 /api 到后端 8080
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    host: true,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
});
