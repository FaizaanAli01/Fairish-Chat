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

  if (mode === 'production') {
    // The backend serves every REST endpoint under the /api context path
    // (`/api/auth/login`, `/api/conversations`, ...). Client code appends paths
    // such as `/auth/login` to this base, so a base URL that omits `/api`
    // silently targets unauthenticated paths and the backend answers 401.
    // Fail the build instead of shipping that regression again.
    let apiPath: string;
    try {
      apiPath = new URL(apiBaseUrl).pathname.replace(/\/+$/, '');
    } catch {
      throw new Error(
        '[config] VITE_API_BASE_URL must be an absolute URL, e.g. https://api.example.com/api.'
      );
    }

    if (!apiPath.endsWith('/api')) {
      throw new Error(
        '[config] VITE_API_BASE_URL must include the backend /api context path. ' +
          'Set it to an absolute URL ending in /api (for example ' +
          'https://fairish-chat-backend.onrender.com/api), otherwise every REST ' +
          'request is sent to a path the backend rejects with 401.'
      );
    }
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
