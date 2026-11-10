import React from 'react';
import { Loader2 } from 'lucide-react';

interface LoadingSpinnerProps {
  size?: 'sm' | 'md' | 'lg';
  text?: string;
  className?: string;
}

export const LoadingSpinner: React.FC<LoadingSpinnerProps> = ({ size = 'md', text, className = '' }) => {
  const sizeClasses = {
    sm: 'w-4 h-4',
    md: 'w-7 h-7',
    lg: 'w-10 h-10',
  };

  return (
    <div className={`flex flex-col items-center justify-center p-3 space-y-2.5 ${className}`}>
      <Loader2 className={`${sizeClasses[size]} animate-spin text-violet-primary`} />
      {text && <p className="text-xs font-medium text-content-secondary tracking-wide">{text}</p>}
    </div>
  );
};
