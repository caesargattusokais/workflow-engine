import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// E2E_API_PORT lets the test suite point the dev-server proxy at an isolated
// backend when 8080 is occupied by another deployment.
const apiTarget = `http://localhost:${process.env.E2E_API_PORT || 8080}`;

export default defineConfig({
  plugins: [react()],
  server: {
    host: true,
    port: Number(process.env.E2E_UI_PORT || 3000),
    proxy: {
      '/api': apiTarget,
      '/ws': { target: apiTarget, ws: true }
    }
  }
});
