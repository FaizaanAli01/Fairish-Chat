import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { User, UpdateProfilePayload } from '../types/user';
import { LoginPayload, RegisterPayload } from '../types/auth';
import { authApi } from '../api/authApi';
import { userApi } from '../api/userApi';

interface AuthContextType {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (data: LoginPayload) => Promise<void>;
  register: (data: RegisterPayload) => Promise<void>;
  logout: () => Promise<void>;
  updateProfile: (data: UpdateProfilePayload) => Promise<User>;
  refreshUser: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(() => {
    const saved = localStorage.getItem('fairish_user');
    return saved ? JSON.parse(saved) : null;
  });
  const [token, setToken] = useState<string | null>(() => localStorage.getItem('fairish_jwt_token'));
  const [isLoading, setIsLoading] = useState<boolean>(true);

  const refreshUser = async () => {
    try {
      const currentUser = await userApi.getCurrentUser();
      setUser(currentUser);
      localStorage.setItem('fairish_user', JSON.stringify(currentUser));
    } catch (error: unknown) {
      // Only clear authentication state on explicit 401/403 (token expired or invalid)
      // Preserve the token for other errors (e.g., network issues, server downtime)
      const axiosError = error as { response?: { status?: number } };
      if (axiosError?.response?.status === 401 || axiosError?.response?.status === 403) {
        setToken(null);
        setUser(null);
        localStorage.removeItem('fairish_jwt_token');
        localStorage.removeItem('fairish_user');
      }
    }
  };

  useEffect(() => {
    const initAuth = async () => {
      const storedToken = localStorage.getItem('fairish_jwt_token');
      if (storedToken) {
        await refreshUser();
      }
      setIsLoading(false);
    };

    initAuth();

    const handleUnauthorized = () => {
      setToken(null);
      setUser(null);
    };

    window.addEventListener('auth:unauthorized', handleUnauthorized);
    return () => window.removeEventListener('auth:unauthorized', handleUnauthorized);
  }, []);

  const login = async (data: LoginPayload) => {
    const response = await authApi.login(data);
    localStorage.setItem('fairish_jwt_token', response.token);
    localStorage.setItem('fairish_user', JSON.stringify(response.user));
    setToken(response.token);
    setUser(response.user);
  };

  const register = async (data: RegisterPayload) => {
    const response = await authApi.register(data);
    localStorage.setItem('fairish_jwt_token', response.token);
    localStorage.setItem('fairish_user', JSON.stringify(response.user));
    setToken(response.token);
    setUser(response.user);
  };

  const logout = async () => {
    try {
      await authApi.logout();
    } catch {
      // Ignore network errors on logout
    } finally {
      localStorage.removeItem('fairish_jwt_token');
      localStorage.removeItem('fairish_user');
      setToken(null);
      setUser(null);
    }
  };

  const updateProfile = async (data: UpdateProfilePayload): Promise<User> => {
    const updated = await userApi.updateProfile(data);
    setUser(updated);
    localStorage.setItem('fairish_user', JSON.stringify(updated));
    return updated;
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        isAuthenticated: !!token && !!user,
        isLoading,
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
