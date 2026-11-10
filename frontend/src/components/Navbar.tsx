import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { MessageSquare, LogOut, X, AlertTriangle } from 'lucide-react';
import { useAuth } from '../context/AuthContext';

export const Navbar: React.FC = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [showLogoutModal, setShowLogoutModal] = useState(false);
  const [isLoggingOut, setIsLoggingOut] = useState(false);

  const handleConfirmLogout = async () => {
    setIsLoggingOut(true);
    try {
      await logout();
      navigate('/login');
    } finally {
      setIsLoggingOut(false);
      setShowLogoutModal(false);
    }
  };

  // Helper for user initials fallback
  const getInitials = (name?: string) => {
    if (!name) return 'U';
    return name.slice(0, 2).toUpperCase();
  };

  return (
    <>
      <header className="h-16 border-b border-dark-border bg-dark-sidebar/95 backdrop-blur-md px-3 sm:px-6 flex items-center justify-between sticky top-0 z-30 flex-shrink-0">
        {/* Brand Link */}
        <Link to="/app" className="flex items-center space-x-3 group min-w-0">
          <div className="w-9 h-9 sm:w-10 sm:h-10 rounded-xl bg-gradient-to-tr from-violet-hover to-violet-primary flex items-center justify-center shadow-violet-sm group-hover:shadow-violet-glow transition-all duration-200 flex-shrink-0">
            <MessageSquare className="w-5 h-5 text-white" />
          </div>
          <div className="min-w-0 flex items-center space-x-2">
            <span className="text-base sm:text-lg font-bold text-content-primary tracking-tight truncate block">
              Fairish<span className="text-violet-lavender ml-1">Chat</span>
            </span>
            <span className="hidden sm:inline-flex items-center px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wider bg-violet-primary/10 text-violet-lavender border border-violet-primary/20 rounded-full flex-shrink-0">
              Live
            </span>
          </div>
        </Link>

        {/* User Controls */}
        <div className="flex items-center space-x-2 sm:space-x-3 flex-shrink-0">
          <Link
            to="/app/profile"
            className="flex items-center space-x-2.5 px-2.5 sm:px-3 py-1.5 rounded-xl hover:bg-dark-hover border border-transparent hover:border-dark-border transition-all text-content-secondary hover:text-content-primary group"
          >
            <div className="w-8 h-8 rounded-full bg-dark-panel border border-dark-border flex items-center justify-center overflow-hidden flex-shrink-0 text-xs font-semibold text-violet-lavender">
              {user?.profileImage ? (
                <img src={user.profileImage} alt={user.username} className="w-full h-full object-cover" />
              ) : (
                <span>{getInitials(user?.username)}</span>
              )}
            </div>
            <div className="hidden md:block text-left min-w-0 max-w-[120px]">
              <p className="text-xs font-semibold leading-none text-content-primary truncate">{user?.username}</p>
              <p className="text-[11px] text-content-secondary mt-1 leading-none truncate">{user?.email}</p>
            </div>
          </Link>

          <button
            onClick={() => setShowLogoutModal(true)}
            title="Log out of Fairish Chat"
            aria-label="Log out"
            className="p-2 text-content-secondary hover:text-status-error hover:bg-red-500/10 rounded-xl transition-all border border-transparent hover:border-red-500/20"
          >
            <LogOut className="w-4 h-4 sm:w-5 sm:h-5" />
          </button>
        </div>
      </header>

      {/* Logout Confirmation Dialog */}
      {showLogoutModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fadeIn">
          <div
            className="w-full max-w-sm bg-dark-panel border border-dark-border rounded-2xl p-5 sm:p-6 shadow-2xl shadow-black/80 space-y-4"
            role="dialog"
            aria-modal="true"
          >
            <div className="flex items-start justify-between">
              <div className="w-10 h-10 rounded-xl bg-red-500/10 border border-red-500/20 flex items-center justify-center text-status-error flex-shrink-0">
                <AlertTriangle className="w-5 h-5" />
              </div>
              <button
                onClick={() => setShowLogoutModal(false)}
                className="p-1 text-content-secondary hover:text-content-primary rounded-lg hover:bg-dark-hover transition-colors"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="space-y-1.5">
              <h3 className="text-base font-semibold text-content-primary">Sign out of Fairish Chat?</h3>
              <p className="text-xs text-content-secondary leading-relaxed">
                You will need to sign in again to access your messages and conversations.
              </p>
            </div>

            <div className="flex items-center justify-end space-x-2 pt-2">
              <button
                type="button"
                onClick={() => setShowLogoutModal(false)}
                disabled={isLoggingOut}
                className="px-3.5 py-2 rounded-xl text-xs font-semibold text-content-secondary hover:text-content-primary hover:bg-dark-hover transition-colors"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleConfirmLogout}
                disabled={isLoggingOut}
                className="px-4 py-2 rounded-xl text-xs font-semibold bg-red-600 hover:bg-red-500 text-white shadow-sm transition-all disabled:opacity-50"
              >
                {isLoggingOut ? 'Signing out...' : 'Sign Out'}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
};
