import axios from 'axios';
import { API_BASE_URL } from '../config/env';

const TOKEN_KEY = 'fairish_jwt_token';
const USER_KEY = 'fairish_user';

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
    // Ignore storage failures; the in-memory auth state is still cleared by
    // the AuthContext listener.
  }
}

export const axiosClient = axios.create({
  baseURL: API_BASE_URL,
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

// Response interceptor: Handle 401 Unauthorized globally
// Skip auth endpoints (login/register) so that bad-credentials 401s
// don't accidentally wipe the user's existing session state.
axiosClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      const url = error.config?.url || '';
      const isAuthEndpoint = url.includes('/auth/login') || url.includes('/auth/register');

      if (!isAuthEndpoint && readToken()) {
        clearStoredSession();
        window.dispatchEvent(new Event('auth:unauthorized'));
      }
    }
    return Promise.reject(error);
  }
);
