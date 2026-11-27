import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import { API_BASE_URL } from '../config/env';

const TOKEN_KEY = 'fairish_jwt_token';
const USER_KEY = 'fairish_user';

// A cold backend (e.g. a scaled-to-zero host waking up) can take tens of seconds.
// Requests must still finish or fail visibly instead of spinning forever.
const REQUEST_TIMEOUT_MS = 65_000;
const RETRY_DELAY_MS = 500;
const RETRYABLE_STATUSES = new Set([502, 503, 504]);

type RetryableConfig = InternalAxiosRequestConfig & { __retryCount?: number };

function readToken(): string | null {
  try {
    const token = localStorage.getItem(TOKEN_KEY);
    return token && token.trim().length > 0 ? token : null;
  } catch {
    return null;
  }
}

function clearStoredSession(): void {
  try {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  } catch {
    // Ignore storage failures; in-memory auth state is still cleared by
    // the AuthContext listener.
  }
}

/**
 * Turns transport failures into actionable UI text. Server-provided messages
 * always win so validation/authorization responses stay unchanged.
 */
export function getApiErrorMessage(error: unknown, fallback: string): string {
  const axiosError = error as AxiosError<{ message?: string }> | undefined;
  const serverMessage = axiosError?.response?.data?.message;
  if (typeof serverMessage === 'string' && serverMessage.length > 0) return serverMessage;
  if (axiosError?.code === 'ECONNABORTED' || axiosError?.code === 'ETIMEDOUT') {
    return 'The server took too long to respond. Please try again.';
  }
  if (!axiosError?.response) {
    return 'Cannot reach the server. Check your connection and try again.';
  }
  return fallback;
}

export const axiosClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: REQUEST_TIMEOUT_MS,
  // Do not force a global Content-Type. Axios sets application/json for object
  // bodies automatically, while FormData must keep its browser-generated
  // multipart boundary (avatar uploads).
});

// Request interceptor: Attach JWT Bearer token if present
axiosClient.interceptors.request.use(
  (config) => {
    const token = readToken();
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor: Handle 401 Unauthorized globally and retry transient
// failures for idempotent reads only. Writes (login, message send, receipts,
// contact mutations) are never replayed.
axiosClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    if (error.response && error.response.status === 401) {
      const url = error.config?.url || '';
      const isAuthEndpoint = url.includes('/auth/login') || url.includes('/auth/register');

      if (!isAuthEndpoint && readToken()) {
        clearStoredSession();
        window.dispatchEvent(new Event('auth:unauthorized'));
      }
      return Promise.reject(error);
    }

    const config = error.config as RetryableConfig | undefined;
    const status = error.response?.status;
    const isTimeout = error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT';
    const isTransient = (!error.response && !isTimeout) ||
      (status !== undefined && RETRYABLE_STATUSES.has(status));

    if (
      config &&
      config.method?.toLowerCase() === 'get' &&
      !config.__retryCount &&
      isTransient
    ) {
      config.__retryCount = 1;
      await new Promise<void>((resolve) => setTimeout(resolve, RETRY_DELAY_MS));
      return axiosClient(config);
    }

    return Promise.reject(error);
  }
);
