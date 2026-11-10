import React from 'react';
import { Link } from 'react-router-dom';
import { MessageSquare, ArrowLeft } from 'lucide-react';

export const NotFoundPage: React.FC = () => {
  return (
    <div className="min-h-screen bg-dark-bg flex flex-col items-center justify-center p-4 text-center relative overflow-hidden">
      {/* Background glow */}
      <div className="absolute top-1/3 w-80 h-80 bg-violet-primary/10 rounded-full blur-3xl pointer-events-none" />

      <div className="relative z-10 space-y-5 max-w-sm">
        <div className="w-16 h-16 rounded-2xl bg-dark-panel border border-dark-border flex items-center justify-center mx-auto shadow-violet-sm text-violet-primary">
          <MessageSquare className="w-8 h-8" />
        </div>
        <div className="space-y-1.5">
          <h1 className="text-4xl font-extrabold text-content-primary tracking-tight">404</h1>
          <h2 className="text-base font-semibold text-content-primary">Page Not Found</h2>
          <p className="text-xs sm:text-sm text-content-secondary leading-relaxed">
            The page or conversation you are looking for does not exist or has been moved.
          </p>
        </div>
        <div>
          <Link
            to="/app"
            className="inline-flex items-center space-x-2 px-5 py-2.5 bg-violet-primary hover:bg-violet-hover text-white rounded-xl text-xs sm:text-sm font-semibold shadow-violet-sm hover:shadow-violet-glow transition-all"
          >
            <ArrowLeft className="w-4 h-4" />
            <span>Return to Messages</span>
          </Link>
        </div>
      </div>
    </div>
  );
};
