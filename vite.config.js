import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  base: process.env.GITHUB_PAGES === 'true' ? '/song/' : '/',
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        configure(proxy) {
          proxy.on('proxyReq', (proxyRequest) => {
            proxyRequest.removeHeader('origin');
          });
        },
      },
    },
  },
});
