/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        // Core dark palette design tokens
        dark: {
          bg: '#0B0D14',       // Main background
          sidebar: '#111522',  // Sidebar background
          panel: '#171C2B',    // Elevated panels / cards
          border: '#262D40',   // Border strokes
          hover: '#1C2234',    // Hover state background
          active: '#21293E',   // Active state background
        },
        // Electric Violet primary accent tokens
        violet: {
          primary: '#8B5CF6',  // Primary electric violet
          hover: '#7C3AED',    // Violet hover state
          lavender: '#A78BFA', // Lavender accent
          muted: '#6D28D9',    // Deeper violet
          glow: 'rgba(139, 92, 246, 0.25)',
        },
        // Content text tokens
        content: {
          primary: '#F8FAFC',   // Primary text
          secondary: '#94A3B8', // Secondary text
          muted: '#64748B',     // Muted / disabled text
        },
        // Functional feedback tokens
        status: {
          success: '#10B981',  // Success green
          error: '#F87171',    // Error red
          warning: '#FBBF24',  // Warning amber
        },
        // Backwards compatibility with existing brand utility classes, mapped to Electric Violet
        brand: {
          50: '#F5F3FF',
          100: '#EDE9FE',
          200: '#DDD6FE',
          300: '#C4B5FD',
          400: '#A78BFA', // Lavender
          500: '#8B5CF6', // Primary violet
          600: '#7C3AED', // Violet hover
          700: '#6D28D9',
          800: '#5B21B6',
          900: '#4C1D95',
          950: '#2E1065',
        },
      },
      boxShadow: {
        'violet-sm': '0 2px 8px -1px rgba(139, 92, 246, 0.2)',
        'violet-md': '0 4px 16px -2px rgba(139, 92, 246, 0.3)',
        'violet-glow': '0 0 20px -3px rgba(139, 92, 246, 0.35)',
        'card': '0 10px 30px -10px rgba(0, 0, 0, 0.5)',
      },
    },
  },
  plugins: [],
}
