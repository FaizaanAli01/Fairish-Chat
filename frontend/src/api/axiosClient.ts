import axios from 'axios';
import { API_BASE_URL } from '../config/env';

export const axiosClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor: Attach JWT Bearer token if present
axiosClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('fairish_jwt_token');
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

      if (!isAuthEndpoint && localStorage.getItem('fairish_jwt_token')) {
        localStorage.removeItem('fairish_jwt_token');
        localStorage.removeItem('fairish_user');
        window.dispatchEvent(new Event('auth:unauthorized'));
      }
    }
    return Promise.reject(error);
  }
);
