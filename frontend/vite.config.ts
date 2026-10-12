import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

// En desarrollo el navegador habla con un único origen (el de Vite) y este proxy reenvía las
// llamadas de la API al backend Spring, por lo que no hace falta configurar CORS.
const BACKEND = process.env.BACKEND_URL ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/teams': BACKEND,
      '/rounds': BACKEND,
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
  },
});
