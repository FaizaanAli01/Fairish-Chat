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

| Variable | Required | Description |
|---|---|---|
| `VITE_API_BASE_URL` | Yes | Backend REST API base URL, without a trailing slash. |
| `VITE_WS_URL` | No | WebSocket base URL. Derived from `VITE_API_BASE_URL` as `wss://<api-host>/ws` when omitted. |

`VITE_API_BASE_URL` must be set before running `vite` or `vite build`. Production builds
also reject `localhost` / `127.0.0.1` values at build time. `VITE_*` values are public and
are embedded in the client bundle — never put secrets in them.

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
Set `VITE_API_BASE_URL` to the public HTTPS backend URL, then run:
```bash
npm run build
```
Production assets are generated in `dist/`. The build fails fast if `VITE_API_BASE_URL`
is missing or points to localhost.

### 5. SPA Routing on Static Hosts
`BrowserRouter` needs a rewrite of unknown paths to `index.html`. This repository includes:
- `public/_redirects` — Netlify / Cloudflare Pages
- `vercel.json` — Vercel

Equivalent rewrite rules must be configured on any other static host, otherwise deep links
such as `/app` and `/profile` return 404 on refresh.

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

