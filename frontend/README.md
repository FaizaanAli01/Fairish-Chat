# Fairish Chat — Frontend

Modern React application for Fairish Chat (Phase 1).

## Tech Stack
- React 18
- Vite 5
- TypeScript
- Tailwind CSS
- React Router DOM 6
- Axios
- Lucide React Icons

## Getting Started

### 1. Configure Environment Variables
Copy `.env.example` to `.env`:
```bash
cp .env.example .env
```
Ensure `VITE_API_BASE_URL` points to your running backend (default: `http://localhost:8080/api`).

### 2. Install Dependencies
```bash
npm install
```

### 3. Start Development Server
```bash
npm run dev
```
The application will be accessible at `http://localhost:5173`.

### 4. Build for Production
```bash
npm run build
```
Production assets are generated in `dist/`.

## Architecture & Features (Phase 1)
- **Centralized API Client (`src/api/axiosClient.ts`)**: Automatically attaches JWT Bearer tokens to outgoing requests and handles 401 Unauthorized responses with state reset.
- **Authentication State (`src/context/AuthContext.tsx`)**: Reactive user session management across normal navigation.
- **Route Guards**:
  - `ProtectedRoute`: Safeguards dashboard and profile from unauthenticated users.
  - `PublicRoute`: Redirects logged-in users to `/app`.
- **Pages**:
  - `LoginPage`: Email/username & password authentication.
  - `RegisterPage`: Validation, unique constraints, and instant login.
  - `DashboardPage`: Protected shell with real-time user directory search and empty state placeholder.
  - `ProfilePage`: View and edit username, bio, and avatar.

