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
├── encryption/  AES-256-GCM message encryption, key ring, legacy migration
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
| `MESSAGE_ENCRYPTION_ENABLED` | Encrypt messages at rest | `true` |
| `MESSAGE_ENCRYPTION_KEY_ID` | Identifier of the active key | `v1` |
| `MESSAGE_ENCRYPTION_KEY` | Base64-encoded 32-byte AES key | **required when enabled** |
| `MESSAGE_ENCRYPTION_PREVIOUS_KEYS` | Old `keyId:base64Key` entries for decryption | empty |
| `MESSAGE_ENCRYPTION_MIGRATION_ENABLED` | Run the legacy migration at startup | `false` |
| `MESSAGE_ENCRYPTION_MIGRATION_MODE` | `dry-run` or `apply` | `dry-run` |
| `MESSAGE_ENCRYPTION_MIGRATION_BATCH_SIZE` | Migration batch size | `100` |
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

### Message encryption at rest

Message content is encrypted with **AES-256-GCM** before it reaches MongoDB. The backend
decrypts it on every read path (REST history, conversation previews) and before broadcasting
over STOMP. The React app continues to receive the same plaintext JSON contract; no key or
ciphertext metadata is exposed to the browser.

- Algorithm: `AES/GCM/NoPadding`, 256-bit key, 96-bit random nonce per encryption
  operation, 128-bit authentication tag.
- Every new message and every conversation preview is encrypted as a separate operation,
  so they never share a nonce.
- Plaintext is never written to `messages.content` or `conversations.lastMessageText` for
  new messages. Legacy fields remain readable.
- Decryption validates the record structure, key ID, nonce length, tag length and
  authentication tag. A failure is rejected with a generic error; ciphertext is never
  returned as a message body.

Generate a key (never commit it):

```bash
openssl rand -base64 32
```

Set `MESSAGE_ENCRYPTION_KEY` to that value and keep `MESSAGE_ENCRYPTION_KEY_ID` stable for
the lifetime of the key. Startup fails when encryption is enabled and the key is missing,
not Base64, or not exactly 32 bytes. The key is never logged.

**Key rotation.** Add the new key as `MESSAGE_ENCRYPTION_KEY` with a new
`MESSAGE_ENCRYPTION_KEY_ID`, then list the old key in
`MESSAGE_ENCRYPTION_PREVIOUS_KEYS` (for example `v1:<old-base64-key>`). New writes use the
active key; old records decrypt with the key ID stored beside their ciphertext.

**Legacy plaintext migration.** Existing records are never rewritten or deleted
automatically. The opt-in utility encrypts `content` and `lastMessageText` in place and
removes the plaintext field only after writing the encrypted fields:

```bash
cd backend
# 1. Count candidates without modifying anything
MESSAGE_ENCRYPTION_MIGRATION_ENABLED=true \
MESSAGE_ENCRYPTION_MIGRATION_MODE=dry-run \
./mvnw spring-boot:run

# 2. After reviewing the counts and taking a backup, apply once
MESSAGE_ENCRYPTION_MIGRATION_ENABLED=true \
MESSAGE_ENCRYPTION_MIGRATION_MODE=apply \
./mvnw spring-boot:run --spring.main.web-application-type=none
```

The runner executes once at startup. Use `--spring.main.web-application-type=none` for a
dedicated migration job so the process exits when the run finishes. Do not run it
concurrently with normal traffic. Reading is backward compatible even before migration:
records without `encryptionVersion` are treated as legacy plaintext.

### Production profile

`application-prod.yml`:

- requires an explicit `CORS_ALLOWED_ORIGINS` (no localhost defaults);
- defaults avatar storage to enabled and fails fast without credentials;
- keeps message encryption enabled and fails fast without a valid key;
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

Current suite: **238 tests, 0 failures, 0 errors, 1 skipped**.

The suite covers contact lifecycle and policy tests, conversation/message authorization,
AES-256-GCM encryption and decryption (unique nonces, tampered ciphertext, wrong key,
invalid nonce, legacy plaintext), REST/STOMP plaintext delivery, encrypted previews,
MockMvc controller behavior, JWT/security, avatar processing and storage, public-projection
privacy, and STOMP interceptor authorization.

Important caveats:

- `S3AvatarStorageMinioTest` uses Testcontainers and is skipped without Docker.
- `AtlasConnectionTest` is non-hermetic: it loads `.env` and pings a live Atlas cluster,
  swallowing failures. It is not a deterministic test.
- No end-to-end test currently boots the application against a real MongoDB and drives both
  HTTP and STOMP flows; policy coverage is split across unit, MockMvc, and interceptor tests.

## Limitations

- Messages are encrypted at rest server-side (AES-256-GCM), but this is not end-to-end
  encryption: the backend holds the key and can decrypt content to serve it.
- No key management service integration; keys come from environment variables/secret
  manager supplied configuration.
- No presence, typing indicators, or read receipts.
- In-memory STOMP broker: single instance only.
- No rate limiting.
- Removing a contact revokes access but does not delete conversations or messages.
