# Fairish Chat — Frontend

React single-page application for Fairish Chat: authentication, profile editing, contact
management, 1-to-1 conversations, and real-time messaging.

## Stack

- React 18 + TypeScript
- Vite 5
- Tailwind CSS
- React Router 6
- Axios
- `@stomp/stompjs` for STOMP over WebSocket
- Lucide icons

## Requirements

- Node.js 18+ and npm
- The backend running and reachable (default `http://localhost:8080`)

## Environment configuration

Copy `.env.example` to `.env`:

```bash
cp .env.example .env      # Windows: copy .env.example .env
```

| Variable | Required | Description |
|---|---|---|
| `VITE_API_BASE_URL` | Yes | Backend REST base URL without a trailing slash, e.g. `http://localhost:8080/api` |
| `VITE_WS_URL` | No | WebSocket base URL, e.g. `ws://localhost:8080/ws`. Derived from `VITE_API_BASE_URL` when omitted. |

`vite.config.ts` and `src/config/env.ts` both validate configuration. The dev server and
build fail fast when `VITE_API_BASE_URL` is missing; production builds additionally reject
`localhost`, `127.0.0.1`, and `[::1]` URLs. `VITE_*` values are public and are embedded in
the bundle — never store secrets in them.

The app authenticates the STOMP `CONNECT` frame with the JWT in STOMP headers, never in the
WebSocket URL, so tokens do not leak into access logs or proxies. If WebSocket delivery fails,
message sending falls back to the REST endpoint.

## Scripts

```bash
npm install       # install dependencies
npm run dev       # Vite dev server on http://localhost:5173
npx tsc --noEmit  # type-check only
npm run build     # type-check + production build into dist/
npm run preview   # serve the production build locally
```

## Application structure

```
src
├── api/
│   ├── axiosClient.ts       Bearer token injection and 401 handling
│   ├── authApi.ts           register / login / logout
│   ├── userApi.ts           profile, search, avatar blob fetch
│   ├── contactApi.ts        contact request lifecycle + contacts
│   ├── conversationApi.ts   conversation create/list/get
│   ├── messageApi.ts        paginated history + REST send fallback
│   └── websocketService.ts  STOMP connect, subscribe, publish
├── components/
│   ├── Navbar.tsx
│   ├── Sidebar.tsx          Chats / Contacts tabs, pending-request badge
│   ├── ContactsPanel.tsx    search, incoming/outgoing requests, contacts
│   ├── Avatar.tsx           authenticated blob rendering with initials fallback
│   └── ...
├── context/AuthContext.tsx  session state and token persistence
├── pages/                   Login, Register, Dashboard, Profile, 404
├── types/                   typed API contracts
└── config/env.ts            validated build-time configuration
```

## Behavior

### Authentication and routing

- `ProtectedRoute` guards `/app` and `/profile`; `PublicRoute` redirects authenticated users
  away from login/register.
- The axios client adds `Authorization: Bearer <token>` to every request and clears session
  state on `401`.
- Only the authenticated user's own profile type (`User`) contains an email. Other users are
  represented by `PublicUser`, which intentionally has no email, storage key, or legacy image
  URL.

### Contacts

The sidebar has two tabs: **Chats** and **Contacts**. The Contacts tab shows a badge with the
number of pending incoming requests.

`ContactsPanel` provides:

- debounced search by exact 24-character Fairish User ID or username substring;
- **Add** to send a request, with **Pending** / **Contact** indicators on search results;
- incoming requests with **Accept** / **Decline**;
- outgoing requests with **Cancel**;
- accepted contacts with **Chat** and **Remove** actions;
- loading, error, and empty states.

Every mutation reloads the lists from the server and triggers a conversation refresh. There is
no full-page reload. Removing a contact closes an open conversation when it disappears from the
list, so revoked access cannot continue in the UI.

The backend remains the enforcement point: the UI hides unavailable actions, but a direct API
or STOMP call is still rejected with `403`.

### Conversations and messages

- Conversations are created only on the server and only between accepted contacts.
- The sidebar shows the most recent message preview and timestamp.
- The message pane loads paginated history (newest first) and supports loading older pages.
- Sending uses STOMP when connected and falls back to the REST endpoint otherwise.
- Incoming messages arrive on `/topic/conversations/{id}`. The client subscribes to every
  conversation in the list (not only the open one) so unread state and delivery
  acknowledgements stay accurate. If an unread push arrives for a conversation the client
  does not know yet, it refreshes the list and subscribes without a page reload.

### Avatars

`Avatar` receives only public metadata (`hasAvatar`, `avatarUpdatedAt`). When an avatar exists,
it fetches bytes through the authenticated `GET /api/users/{id}/avatar` endpoint as a blob and
renders an object URL, falling back to initials on failure. No storage URL or presigned URL is
ever used by the client.

## Production build and hosting

```bash
# PowerShell
$env:VITE_API_BASE_URL='https://api.example.com/api'
$env:VITE_WS_URL='wss://api.example.com/ws'
npm run build
```

Upload `dist/` to a static host and rewrite unknown paths to `index.html` so client-side routes
survive a refresh. This repository includes:

- `public/_redirects` for Netlify / Cloudflare Pages
- `vercel.json` for Vercel

Any other host needs an equivalent SPA fallback rule. The backend must allow the frontend
origin in `CORS_ALLOWED_ORIGINS` for both REST and WebSocket.

## Testing status

There is currently **no frontend unit or end-to-end test suite**. The validation performed in
this repository is:

- `npx tsc --noEmit` (strict TypeScript check)
- `npm run build` with production, non-localhost URLs

Contact lifecycle, chat authorization, and WebSocket enforcement are tested on the backend;
see the root `README.md`.

## Limitations

- No end-to-end encryption, presence, typing indicators, or read receipts.
- No offline message queue; real-time delivery requires an active WebSocket connection.
- No client-side automated tests.
- Message attachments, group chats, and push notifications are not implemented.
