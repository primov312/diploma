import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In development the React dev server proxies API calls to the Java backend
// so cookies stay same-origin. In the built image Java serves these files itself.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: false },
      '/actuator': { target: 'http://localhost:8080', changeOrigin: false },
    },
  },
});
