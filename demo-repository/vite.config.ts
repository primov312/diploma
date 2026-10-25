import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

// In development the React dev server proxies API calls to the Java backend
// so cookies stay same-origin. In the built image Java serves these files itself.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', 'ROCKET_');
  return {
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: env.ROCKET_API_PROXY ?? 'http://localhost:8080', changeOrigin: false },
      '/actuator': { target: env.ROCKET_API_PROXY ?? 'http://localhost:8080', changeOrigin: false },
    },
  },
  };
});
