import React from 'react';
import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { LoadingSpinner } from './LoadingSpinner';

export const PublicRoute: React.FC = () => {
  const { isAuthenticated, isLoading, serverStatusMessage } = useAuth();

  if (isLoading) {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center bg-dark-bg p-4 space-y-2">
        <LoadingSpinner size="lg" />
        {serverStatusMessage && (
          <p className="text-center text-xs text-violet-lavender animate-pulse">
            {serverStatusMessage}
          </p>
        )}
      </div>
    );
  }

  if (isAuthenticated) {
    return <Navigate to="/app" replace />;
  }

  return <Outlet />;
};
