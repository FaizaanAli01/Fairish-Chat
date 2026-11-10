import React from 'react';
import { AlertCircle } from 'lucide-react';

interface ErrorMessageProps {
  message?: string;
  validationErrors?: Record<string, string>;
}

export const ErrorMessage: React.FC<ErrorMessageProps> = ({ message, validationErrors }) => {
  if (!message && (!validationErrors || Object.keys(validationErrors).length === 0)) {
    return null;
  }

  return (
    <div className="bg-red-500/10 border border-red-500/25 rounded-xl p-3 sm:p-3.5 text-status-error text-xs sm:text-sm flex items-start space-x-2.5 shadow-sm">
      <AlertCircle className="w-4 h-4 sm:w-5 sm:h-5 flex-shrink-0 mt-0.5 text-status-error" />
      <div className="flex-1 min-w-0">
        {message && <p className="font-medium text-status-error leading-relaxed">{message}</p>}
        {validationErrors && Object.keys(validationErrors).length > 0 && (
          <ul className="mt-1.5 list-disc list-inside space-y-1 text-xs text-red-300/90">
            {Object.entries(validationErrors).map(([field, err]) => (
              <li key={field} className="truncate">
                <span className="font-semibold capitalize text-status-error">{field}:</span> {err}
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
};
