import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

const LOCAL_HOST_PATTERN = /(^|\/\/)(localhost|127\.0\.0\.1|\[::1\])(?=[:/]|$)/i;

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  // Only VITE_* variables are public client configuration; never read backend secrets here.
  const env = loadEnv(mode, '.', 'VITE_');
  const apiBaseUrl = env.VITE_API_BASE_URL?.trim();

  if (!apiBaseUrl) {
    throw new Error(
      '[config] VITE_API_BASE_URL is required. Set it to your backend API base URL before running Vite.'
    );
  }

  if (mode === 'production' && LOCAL_HOST_PATTERN.test(apiBaseUrl)) {
    throw new Error(
      '[config] VITE_API_BASE_URL must not point to localhost in a production build.'
    );
  }

  const wsUrl = env.VITE_WS_URL?.trim();

  if (wsUrl && mode === 'production' && LOCAL_HOST_PATTERN.test(wsUrl)) {
    throw new Error(
      '[config] VITE_WS_URL must not point to localhost in a production build.'
    );
  }

  return {
    plugins: [react()],
    server: {
      port: 5173,
      host: true,
    },
  };
});
