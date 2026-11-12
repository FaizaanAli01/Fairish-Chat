/**
 * Build-time validated runtime configuration.
 *
 * Vite inlines `import.meta.env` values during the build. `vite.config.ts`
 * already rejects missing values and localhost URLs in production builds; the
 * checks here are a runtime safety net for any deployment that bypasses it.
 */

const LOCAL_HOST_PATTERN = /(^|\/\/)(localhost|127\.0\.0\.1|\[::1\])(?=[:/]|$)/i;

const rawApiBaseUrl = import.meta.env.VITE_API_BASE_URL;

if (!rawApiBaseUrl || !rawApiBaseUrl.trim()) {
  throw new Error(
    'VITE_API_BASE_URL is required. Set it to your backend API base URL (for example https://api.example.com/api) before building.'
  );
}

const apiBaseUrl = rawApiBaseUrl.trim().replace(/\/+$/, '');

if (import.meta.env.PROD && LOCAL_HOST_PATTERN.test(apiBaseUrl)) {
  throw new Error(
    'VITE_API_BASE_URL must not point to localhost in a production build. Configure the public HTTPS API URL.'
  );
}

const rawWsUrl = import.meta.env.VITE_WS_URL?.trim();

if (rawWsUrl && import.meta.env.PROD && LOCAL_HOST_PATTERN.test(rawWsUrl)) {
  throw new Error(
    'VITE_WS_URL must not point to localhost in a production build. Configure the public WSS URL.'
  );
}

function deriveWebSocketUrl(apiBase: string): string {
  const wsProtocol = apiBase.startsWith('https://') ? 'wss://' : 'ws://';
  const hostAndPath = apiBase.replace(/^https?:\/\//, '');
  const cleanHostAndPath = hostAndPath.replace(/\/api\/?$/, '');
  return `${wsProtocol}${cleanHostAndPath}/ws`;
}

export const API_BASE_URL = apiBaseUrl;
export const WS_URL = rawWsUrl || deriveWebSocketUrl(apiBaseUrl);
