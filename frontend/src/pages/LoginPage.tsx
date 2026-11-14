import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { MessageSquare, Lock, Mail, ArrowRight, ShieldCheck, Eye, EyeOff } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { ErrorMessage } from '../components/ErrorMessage';
import { LoadingSpinner } from '../components/LoadingSpinner';

export const LoginPage: React.FC = () => {
  const { login } = useAuth();
  const navigate = useNavigate();

  const [identifier, setIdentifier] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);
    setFieldErrors({});

    if (!identifier.trim()) {
      setFieldErrors((prev) => ({ ...prev, identifier: 'Please enter your username or email' }));
      return;
    }
    if (!password) {
      setFieldErrors((prev) => ({ ...prev, password: 'Please enter your password' }));
      return;
    }

    setIsSubmitting(true);
    try {
      await login({ identifier, password });
      navigate('/app');
    } catch (err: any) {
      if (err.response?.data?.message) {
        setErrorMsg(err.response.data.message);
      } else if (err.response?.data?.validationErrors) {
        setFieldErrors(err.response.data.validationErrors);
      } else {
        setErrorMsg('Unable to log in. Please check your credentials and try again.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen bg-dark-bg flex flex-col justify-center items-center px-4 py-12 relative overflow-hidden">
      {/* Subtle violet background ambient glow */}
      <div className="absolute top-1/4 -left-32 w-96 h-96 bg-violet-primary/10 rounded-full blur-3xl pointer-events-none" />
      <div className="absolute bottom-1/4 -right-32 w-96 h-96 bg-violet-muted/10 rounded-full blur-3xl pointer-events-none" />

      <div className="w-full max-w-md relative z-10 space-y-6">
        {/* Branding Header */}
        <div className="text-center space-y-3">
          <div className="w-14 h-14 rounded-2xl bg-gradient-to-tr from-violet-hover to-violet-primary flex items-center justify-center mx-auto shadow-violet-md">
            <MessageSquare className="w-7 h-7 text-white" />
          </div>
          <div className="space-y-1">
            <h1 className="text-2xl sm:text-3xl font-bold text-content-primary tracking-tight">
              Sign in to <span className="brand-wordmark">Fairish<span className="ml-1">Chat</span></span>
            </h1>
            <p className="text-xs sm:text-sm text-content-secondary">
              Enter your credentials to access your direct message feed
            </p>
          </div>
        </div>

        {/* Auth Card */}
        <div className="bg-dark-sidebar border border-dark-border rounded-2xl p-6 sm:p-8 shadow-card space-y-6">
          <ErrorMessage message={errorMsg || undefined} validationErrors={fieldErrors} />

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-content-secondary mb-2">
                Email or Username
              </label>
              <div className="relative">
                <Mail className="w-4 h-4 text-content-muted absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
                <input
                  type="text"
                  required
                  placeholder="name@example.com or username"
                  value={identifier}
                  onChange={(e) => setIdentifier(e.target.value)}
                  className="w-full pl-10 pr-4 py-2.5 bg-dark-panel border border-dark-border rounded-xl text-xs sm:text-sm text-content-primary placeholder-content-muted focus:outline-none focus:border-violet-primary focus:ring-1 focus:ring-violet-primary transition-all"
                />
              </div>
            </div>

            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-content-secondary mb-2">
                Password
              </label>
              <div className="relative">
                <Lock className="w-4 h-4 text-content-muted absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
                <input
                  type={showPassword ? 'text' : 'password'}
                  required
                  placeholder="••••••••"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="w-full pl-10 pr-10 py-2.5 bg-dark-panel border border-dark-border rounded-xl text-xs sm:text-sm text-content-primary placeholder-content-muted focus:outline-none focus:border-violet-primary focus:ring-1 focus:ring-violet-primary transition-all"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                  className="p-1.5 text-content-muted hover:text-content-secondary absolute right-3 top-1/2 -translate-y-1/2 rounded-lg transition-colors"
                >
                  {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
            </div>

            <button
              type="submit"
              disabled={isSubmitting}
              className="w-full py-3 px-4 bg-violet-primary hover:bg-violet-hover text-white text-xs sm:text-sm font-semibold rounded-xl shadow-violet-sm hover:shadow-violet-glow transition-all duration-150 flex items-center justify-center space-x-2 disabled:opacity-50 disabled:cursor-not-allowed mt-2"
            >
              {isSubmitting ? (
                <LoadingSpinner size="sm" text="Authenticating..." />
              ) : (
                <>
                  <span>Sign In</span>
                  <ArrowRight className="w-4 h-4" />
                </>
              )}
            </button>
          </form>

          <div className="pt-4 border-t border-dark-border text-center">
            <p className="text-xs sm:text-sm text-content-secondary">
              Don't have an account?{' '}
              <Link to="/register" className="font-semibold text-violet-lavender hover:text-violet-primary transition-colors">
                Create one now
              </Link>
            </p>
          </div>
        </div>

        {/* Security Info */}
        <div className="flex items-center justify-center space-x-2 text-[11px] text-content-muted text-center">
          <ShieldCheck className="w-4 h-4 text-status-success flex-shrink-0" />
          <span>Stateless JWT & BCrypt Encryption Protection</span>
        </div>
      </div>
    </div>
  );
};
