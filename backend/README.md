# Fairish Chat — Backend

Spring Boot 3.x backend service for Fairish Chat (Phase 1).

## Tech Stack
- Java 21
- Spring Boot 3.2.0
- Spring Data MongoDB
- Spring Security 6 (Stateless JWT Authentication, BCrypt)
- Jakarta Bean Validation
- Lombok
- JUnit 5 & Mockito

## Prerequisites
- Java 21+ JDK
- MongoDB running locally on port 27017 or a MongoDB Atlas URI
- Maven 3.9+ (or use the provided portable Maven / wrapper)

## Configuration
Copy `.env.example` to `.env` or configure system environment variables:

| Variable | Description | Default |
|---|---|---|
| `PORT` | HTTP server port | `8080` |
| `MONGODB_URI` | MongoDB connection URI | `mongodb://localhost:27017/fairish_chat` |
| `MONGODB_DATABASE` | MongoDB database name | `fairish_chat` |
| `JWT_SECRET` | Secret key for HS256 HMAC (>= 32 bytes / 256 bits) | **Required — no default** |
| `JWT_EXPIRATION` | JWT token validity in ms | `86400000` (24h) |
| `CORS_ALLOWED_ORIGINS` | Comma-separated exact frontend origins | Dev localhost defaults; **required in `prod`** |

The application fails fast at startup when `JWT_SECRET` is missing or shorter than 32 bytes.
Generate one per environment with `openssl rand -base64 48`.

## Production Profile
Activate the `prod` profile with `SPRING_PROFILES_ACTIVE=prod`. It enables INFO logging,
secure error responses (no exception details), reverse-proxy header handling, and requires
`CORS_ALLOWED_ORIGINS` to be set explicitly — no localhost defaults are applied.

## Build & Test
```bash
# Run tests
mvn test

# Package JAR
mvn clean package
```

## Running the Backend
```bash
mvn spring-boot:run
```
The server will start at `http://localhost:8080`.

## API Endpoints (Phase 1)
- `GET /api/health` — Public health check
- `POST /api/auth/register` — User registration
- `POST /api/auth/login` — User login (email or username)
- `POST /api/auth/logout` — Client-side logout acknowledgment
- `GET /api/users/me` — Get current user profile (requires `Authorization: Bearer <token>`)
- `PUT /api/users/me` — Update profile (requires `Authorization: Bearer <token>`)
- `GET /api/users/search?query=...` — Search users by username (requires `Authorization: Bearer <token>`)

