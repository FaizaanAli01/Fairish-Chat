import React, { createContext, useContext, useState, useEffect, useRef, ReactNode } from 'react';
import { User, UpdateProfilePayload } from '../types/user';
import { LoginPayload, RegisterPayload } from '../types/auth';
import { authApi } from '../api/authApi';
import { userApi } from '../api/userApi';

const TOKEN_KEY = 'fairish_jwt_token';
const USER_KEY = 'fairish_user';

interface AuthContextType {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  serverStatusMessage: string | null;
  login: (data: LoginPayload) => Promise<void>;
  register: (data: RegisterPayload) => Promise<void>;
  logout: () => Promise<void>;
  updateProfile: (data: UpdateProfilePayload) => Promise<User>;
  refreshUser: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

/**
 * Reads the persisted token defensively. A malformed or empty value is treated
 * as no session rather than being forwarded to the API.
 */
function readStoredToken(): string | null {
  try {
    const stored = localStorage.getItem(TOKEN_KEY);
    return stored && stored.trim().length > 0 ? stored : null;
  } catch {
    return null;
  }
}

/**
 * Parses the cached user without ever throwing. Corrupted or tampered storage
 * must not crash the application on boot.
 */
function readStoredUser(): User | null {
  let raw: string | null = null;
  try {
    raw = localStorage.getItem(USER_KEY);
  } catch {
    return null;
  }
  if (!raw) {
    return null;
  }

  try {
    const parsed: unknown = JSON.parse(raw);
    if (
      parsed !== null &&
      typeof parsed === 'object' &&
      typeof (parsed as { id?: unknown }).id === 'string' &&
      typeof (parsed as { username?: unknown }).username === 'string'
    ) {
      return parsed as User;
    }
  } catch {
    // Fall through to cleanup.
  }

  try {
    localStorage.removeItem(USER_KEY);
  } catch {
    // Storage may be unavailable (private mode, quota). Ignore.
  }
  return null;
}

export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [token, setToken] = useState<string | null>(readStoredToken);
  const [user, setUser] = useState<User | null>(() => (readStoredToken() ? readStoredUser() : null));
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [serverStatusMessage, setServerStatusMessage] = useState<string | null>(null);
  const sessionCheckTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const clearSession = () => {
    try {
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(USER_KEY);
    } catch {
      // Ignore storage failures; in-memory state is still cleared.
    }
    setToken(null);
    setUser(null);
  };

  const refreshUser = async () => {
    if (sessionCheckTimerRef.current) {
      clearTimeout(sessionCheckTimerRef.current);
    }
    sessionCheckTimerRef.current = setTimeout(() => {
      setServerStatusMessage('Waking up server (takes ~30s on first load)...');
    }, 4000);

    try {
      const currentUser = await userApi.getCurrentUser();
      setUser(currentUser);
      localStorage.setItem(USER_KEY, JSON.stringify(currentUser));
    } catch (error: unknown) {
      // Only clear authentication state on explicit 401/403 (token expired or invalid).
      // Preserve the token for other errors (e.g. network issues, server downtime).
      const axiosError = error as { response?: { status?: number } };
      if (axiosError?.response?.status === 401 || axiosError?.response?.status === 403) {
        clearSession();
      }
    } finally {
      if (sessionCheckTimerRef.current) {
        clearTimeout(sessionCheckTimerRef.current);
        sessionCheckTimerRef.current = null;
      }
      setServerStatusMessage(null);
      setIsLoading(false);
    }
  };

  useEffect(() => {
    const storedToken = readStoredToken();
    const hasCachedProfile = storedToken !== null && readStoredUser() !== null;

    if (storedToken) {
      // Revalidate in the background. Waiting for /users/me before rendering used
      // to show a full-screen spinner for the entire backend cold-start window and
      // delayed every dashboard request. The cached profile is sufficient to boot.
      void refreshUser();
    }

    // Only block rendering when there is a token but no cached identity to use.
    // With no token (login page) or with a cached profile, render immediately.
    setIsLoading(storedToken !== null && !hasCachedProfile);

    const handleUnauthorized = () => {
      clearSession();
    };

    // Keep every tab in sync when another tab logs in, logs out, or the session
    // is invalidated. The storage event fires only in other tabs, so this cannot
    // loop with the writes performed above.
    const handleStorage = (event: StorageEvent) => {
      if (event.key === TOKEN_KEY) {
        const nextToken = readStoredToken();
        setToken(nextToken);
        setUser(nextToken ? readStoredUser() : null);
      } else if (event.key === USER_KEY) {
        setUser(readStoredUser());
      } else if (event.key === null) {
        // localStorage.clear() was called in another tab.
        setToken(null);
        setUser(null);
      }
    };

    window.addEventListener('auth:unauthorized', handleUnauthorized);
    window.addEventListener('storage', handleStorage);
    return () => {
      if (sessionCheckTimerRef.current) {
        clearTimeout(sessionCheckTimerRef.current);
        sessionCheckTimerRef.current = null;
      }
      window.removeEventListener('auth:unauthorized', handleUnauthorized);
      window.removeEventListener('storage', handleStorage);
    };
  }, []);

  const persistSession = (nextToken: string, nextUser: User) => {
    try {
      localStorage.setItem(TOKEN_KEY, nextToken);
      localStorage.setItem(USER_KEY, JSON.stringify(nextUser));
    } catch {
      // Storage can be unavailable (private mode, quota). The in-memory
      // session still works for this tab.
    }
    setToken(nextToken);
    setUser(nextUser);
  };

  const login = async (data: LoginPayload) => {
    const response = await authApi.login(data);
    persistSession(response.token, response.user);
  };

  const register = async (data: RegisterPayload) => {
    const response = await authApi.register(data);
    persistSession(response.token, response.user);
  };

  const logout = async () => {
    try {
      await authApi.logout();
    } catch {
      // Ignore network errors on logout; local state is always cleared.
    } finally {
      clearSession();
    }
  };

  const updateProfile = async (data: UpdateProfilePayload): Promise<User> => {
    const updated = await userApi.updateProfile(data);
    setUser(updated);
    try {
      localStorage.setItem(USER_KEY, JSON.stringify(updated));
    } catch {
      // Ignore storage failures; the updated profile is already in memory.
    }
    return updated;
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        isAuthenticated: !!token && !!user,
        isLoading,
        serverStatusMessage,
        login,
        register,
        logout,
        updateProfile,
        refreshUser,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
