# Fairish Chat — Backend

Spring Boot service providing authentication, profiles, contacts, contact-gated 1-to-1
conversations, persistent messages, STOMP real-time delivery, and private avatar storage.

## Stack

- Java 21 (build target), Spring Boot 3.2
- Spring Security 6 with stateless JWT (HS256) and BCrypt
- Spring Data MongoDB
- Spring WebSocket / STOMP (in-memory broker)
- AWS SDK v2 S3 client (Cloudflare R2 in production, MinIO locally)
- Jakarta Bean Validation, JUnit 5, Mockito, MockMvc, Testcontainers

## Package layout

```
src/main/java/com/fairish/chat
├── config/      Security-independent config: CORS, WebSocket, dotenv loader,
│                Spring MVC, avatar storage client/properties
├── controller/  AuthController, UserController, AvatarController,
│                ContactController, ConversationController, MessageController,
│                WebSocketChatController, HealthController
├── dto/         Request/response models. PublicUserResponse deliberately omits email.
├── exception/   Domain exceptions and GlobalExceptionHandler
├── model/       User, ContactRequest, ContactStatus, Conversation, Message
├── repository/  Spring Data MongoDB repositories
├── security/    JwtService, JwtAuthenticationFilter, SecurityConfig,
│                WebSocketAuthInterceptor, UserPrincipal
├── service/     Auth, User, Contact, Conversation, Message, Avatar services
└── storage/     AvatarStorage interface, S3AvatarStorage, DisabledAvatarStorage
```

## Configuration

Copy `.env.example` to `.env`. The file is gitignored and loaded automatically by
`DotenvEnvironmentPostProcessor`, which searches `backend/.env`, `.env`, and parent
directories. Precedence is: command-line arguments → system properties → OS environment →
`.env` → `application.yml` defaults.

At minimum set `JWT_SECRET` (32+ bytes). For a complete local setup, `docker-compose.yml` in
the repository root starts MongoDB and MinIO, and the `.env.example` avatar values already
match it.

| Variable | Description | Default |
|---|---|---|
| `PORT` | HTTP port | `8080` |
| `SPRING_PROFILES_ACTIVE` | `prod` enables strict production settings | unset |
| `MONGODB_URI` | MongoDB connection URI | `mongodb://localhost:27017/fairish_chat` |
| `MONGODB_DATABASE` | Database name | `fairish_chat` |
| `JWT_SECRET` | HMAC secret, minimum 32 bytes | **required** |
| `JWT_EXPIRATION` | Token lifetime in ms | `86400000` |
| `CORS_ALLOWED_ORIGINS` | Exact origins, comma-separated | localhost dev origins |
| `AVATAR_STORAGE_ENABLED` | Enable S3 storage | `false` (dev), `true` (`prod`) |
| `AVATAR_STORAGE_ENDPOINT` | S3 endpoint | empty |
| `AVATAR_STORAGE_REGION` | S3 region | `us-east-1` |
| `AVATAR_STORAGE_BUCKET` | Private bucket | `fairish-avatars` |
| `AVATAR_STORAGE_ACCESS_KEY` | S3 access key | required when enabled |
| `AVATAR_STORAGE_SECRET_KEY` | S3 secret key | required when enabled |
| `AVATAR_STORAGE_PATH_STYLE_ACCESS` | Path-style addressing | `true` |
| `AVATAR_STORAGE_CREATE_BUCKET` | Create bucket on first use | `true` |
| `AVATAR_MAX_UPLOAD_BYTES` | Upload size limit | `5242880` |
| `AVATAR_MAX_REQUEST_BYTES` | Multipart request limit | `6MB` |
| `AVATAR_OUTPUT_SIZE` | Normalized avatar size | `512` |

### Avatar storage providers

| Setting | Local MinIO | Production R2 |
|---|---|---|
| `AVATAR_STORAGE_ENDPOINT` | `http://localhost:9000` | `https://<account-id>.r2.cloudflarestorage.com` |
| `AVATAR_STORAGE_REGION` | `us-east-1` | `auto` |
| `AVATAR_STORAGE_PATH_STYLE_ACCESS` | `true` | `false` |
| `AVATAR_STORAGE_CREATE_BUCKET` | `true` | `false` (provision out of band) |

When storage is disabled, `DisabledAvatarStorage` is used and avatar endpoints return `503`.
When enabled, `AvatarStorageConfig` fails at startup if any required credential is missing.

### Production profile

`application-prod.yml`:

- requires an explicit `CORS_ALLOWED_ORIGINS` (no localhost defaults);
- defaults avatar storage to enabled and fails fast without credentials;
- enables reverse-proxy header handling;
- suppresses exception messages/stack traces in error responses.

## Build and run

```bash
# Windows
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run

# macOS / Linux
./mvnw test
./mvnw spring-boot:run
```

Package a JAR with `./mvnw clean package`. The service starts on
`http://localhost:8080`; health is at `GET /api/health`.

## Authorization primitives

- `SecurityConfig` protects every route except `/api/auth/**`, `/api/health`, `/ws/**`, and
  `/error`. The JWT filter derives the authenticated user; controllers use `UserPrincipal`
  to obtain the user ID rather than trusting client-supplied IDs.
- `ContactService.areAcceptedContacts(a, b)` is the authoritative contact check. It requires
  an `ACCEPTED` contact request for the unordered pair.
- `ConversationService.getConversationAndValidateParticipant(conversationId, userId)` is the
  shared chat gate: it checks existence, participation, and accepted-contact status. REST
  history/send, STOMP `SUBSCRIBE`, and STOMP `SEND` all call it.
- `ConversationService.getOrCreateConversation` rejects creation without an accepted contact;
  `getUserConversations` filters out conversations whose counterpart is not accepted.
- `WebSocketAuthInterceptor` authenticates `CONNECT` frames and enforces the same
  conversation gate for `SUBSCRIBE` and `SEND`.
- `AvatarService` allows reads only for the owner or an accepted contact.

## API summary

Public: `GET /api/health`, `POST /api/auth/register`, `POST /api/auth/login`,
`POST /api/auth/logout`.

Authenticated:

- `GET|PUT /api/users/me`, `GET /api/users/search?query=...`
- `POST|DELETE /api/users/me/avatar`, `GET /api/users/{id}/avatar`
- `POST /api/contacts/requests`
- `GET /api/contacts/requests/incoming`, `GET /api/contacts/requests/outgoing`
- `POST /api/contacts/requests/{id}/accept`, `POST /api/contacts/requests/{id}/decline`
- `DELETE /api/contacts/requests/{id}`
- `GET /api/contacts`, `DELETE /api/contacts/{contactUserId}`
- `POST|GET /api/conversations`, `GET /api/conversations/{id}`
- `GET|POST /api/conversations/{id}/messages`

STOMP endpoint `/ws`; subscribe to `/topic/conversations/{id}`; send to
`/app/conversations/{id}/sendMessage` or `/app/chat.sendMessage`.

See the root `README.md` for request bodies, status codes, and the full authorization model.

## Tests

Current suite: **204 tests, 0 failures, 0 errors, 1 skipped**.

The suite covers contact lifecycle and policy tests, conversation/message authorization,
MockMvc controller behavior, JWT/security, avatar processing and storage, public-projection
privacy, and STOMP interceptor authorization.

Important caveats:

- `S3AvatarStorageMinioTest` uses Testcontainers and is skipped without Docker.
- `AtlasConnectionTest` is non-hermetic: it loads `.env` and pings a live Atlas cluster,
  swallowing failures. It is not a deterministic test.
- No end-to-end test currently boots the application against a real MongoDB and drives both
  HTTP and STOMP flows; policy coverage is split across unit, MockMvc, and interceptor tests.

## Limitations

- No end-to-end encryption; messages are stored in plaintext.
- No presence, typing indicators, or read receipts.
- In-memory STOMP broker: single instance only.
- No rate limiting.
- Removing a contact revokes access but does not delete conversations or messages.
