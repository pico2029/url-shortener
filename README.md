# URL Shortener

A URL-shortener project built with a **Java/Spring Boot backend**, a **React/TypeScript frontend**, **PostgreSQL**, and **Redis**. Anonymous short-link creation, authenticated custom aliases, public redirects, account registration and login are available. Builds and services run through **Docker Compose**.

## Project status

The project implements US01–US08: anonymous and authenticated creation, registration, JWT login, client logout, a personal dashboard, one-click link copying and owner-only deletion and custom aliases. Statistics remain planned.

| Available | To be implemented |
|---|---|
| Link creation, public redirects, personal listing, deletion and account APIs | Custom aliases and expiration |
| English home, account forms, personal dashboard, copying and deletion | Statistics UI |
| PostgreSQL and Redis configuration | Application caching and rate limiting |
| Flyway users/links migrations and JPA persistence | Remaining domain migrations |
| Registration, JWT login, expiry and client logout | JWT revocation (outside current scope) |
| Authentication endpoint documentation, Swagger UI, and health checks | Documentation for remaining endpoints |
| Multi-stage Dockerfiles, Docker Compose and custom aliases | Analytics and expiration |
| Infrastructure, registration and persistence checks; feature tests | Remaining feature tests |

**Registration and login are implemented:** `/register` includes password confirmation; `/login` checks existing accounts. Both successful flows navigate to `/dashboard`, with authenticated creation, optional custom aliases, paginated personal links and “Log out”. Registration automatically logs in through the login API. Expiration and statistics remain unimplemented. Generated short URLs can be copied with one click or manually and open their destinations without login.

## Technology stack

| Area | Configured technologies |
|---|---|
| Backend | Java 21, Spring Boot 3.5.7, Spring Web |
| Persistence | Spring Data JPA, PostgreSQL 17.6, PostgreSQL JDBC |
| Migrations | Flyway with PostgreSQL support |
| Cache | Redis 7.4.6, Spring Data Redis |
| Security | Spring Security; JWT Bearer authentication |
| Frontend | React 19.2.0, TypeScript 5.9.3, HTML, and CSS |
| Frontend build | Vite 7.3.7, Node.js 22.21.1, and npm |
| Backend build | Maven 3.9.11 |
| Web server and reverse proxy | Nginx 1.28.0 |
| API documentation | OpenAPI and Swagger UI through springdoc-openapi 2.8.13 |
| Backend health | Spring Boot Actuator |
| Backend tests | JUnit 5, Mockito, PostgreSQL Testcontainers |
| Frontend tests | Vitest, React Testing Library, jsdom |
| Containerization | Docker and Docker Compose |

JUnit and Mockito cover registration, validation and error handling. PostgreSQL Testcontainers covers HTTP, migrations, uniqueness, concurrent registrations, login and JWT verification. Vitest and React Testing Library cover password confirmation, both forms, navigation, session expiry, dashboard access and link-copy feedback. The repository currently **does not contain GitHub Actions workflows**. Checks can be run manually using the included script.

## Architecture

The backend is a monolith organized by feature. The frontend and backend have independent builds and images and communicate through Nginx.

```text
Browser
  |
  v
http://localhost:8080
  |
  v
frontend / Nginx :80
  |-- /                         -> compiled React application
  |-- /api/*                    -> backend :8080
  |-- /r/*                      -> backend :8080
  |-- /swagger-ui, /swagger-ui/* -> backend :8080
  |-- /swagger-ui.html           -> backend :8080
  `-- /v3/api-docs, /v3/api-docs/* -> backend :8080
                                      |
                                      |-- PostgreSQL :5432
                                      `-- Redis :6379
```

### Docker services

| Service | Responsibility | Host access |
|---|---|---|
| `frontend` | Nginx serves the SPA and forwards requests to the backend | Port `8080`, configurable |
| `backend` | Runs Spring Boot registration, login and authenticated APIs | No published ports |
| `postgres` | Stores data in a named volume | No published ports |
| `redis` | Provides a rebuildable cache without persistence | No published ports |
| `backend-tests` | Optional Maven/Testcontainers runner, enabled by the `test` profile | No application port |

The `ingress` network connects the frontend and backend. The internal `data` network connects the backend, PostgreSQL, and Redis. The frontend does not access the data services directly.

Health checks ensure that PostgreSQL and Redis are healthy before the backend starts, and that the backend is healthy before the frontend starts. Actuator exposes `/actuator/health` internally, checking both the database and Redis without revealing sensitive details. Nginx blocks external access to this endpoint and exposes `/healthz` for its own health check.

The Dockerfiles use multi-stage builds: Maven/JDK produces the JAR executed by Java, while Node/npm produces the static files served by Nginx. Maven and npm do not need to be installed on the host.

## Repository structure

```text
.
├── backend/
│   ├── src/main/java/com/example/shortener/
│   │   ├── ShortenerApplication.java
│   │   ├── link/
│   │   ├── auth/
│   │   ├── user/
│   │   ├── analytics/
│   │   ├── security/
│   │   ├── ratelimit/
│   │   └── shared/
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/migration/
│   ├── src/test/java/
│   ├── pom.xml
│   ├── Dockerfile
│   └── .dockerignore
├── frontend/
│   ├── src/
│   │   ├── app/
│   │   ├── features/
│   │   │   ├── links/
│   │   │   ├── auth/
│   │   │   └── analytics/
│   │   └── shared/
│   ├── index.html
│   ├── package.json
│   ├── package-lock.json
│   ├── tsconfig.json
│   ├── nginx.conf
│   ├── Dockerfile
│   └── .dockerignore
├── scripts/verify.sh
├── compose.yml
├── .env.example
├── .gitignore
└── README.md
```

### Feature boundaries

| Backend module | Responsibility |
|---|---|
| `link` | Link creation, management, aliases, expiration, caching, and redirects |
| `auth` | Registration and login |
| `user` | User data and persistence |
| `analytics` | Click events and statistics |
| `security` | JWT verification and authorization |
| `ratelimit` | Request limits using Redis |
| `shared` | Common configuration and error handling |

The `link` module contains URL validation, creation and persistence; the `auth`, `user`, `security` and `shared` modules contain the implemented account flow, persistence, JWT configuration and error handling. Other feature modules remain placeholders. The frontend follows a similar separation in `features`: `features/auth` contains the shared login/registration form and session storage; `features/links` contains the reusable creation form and personal dashboard; `app` contains routing; `shared` contains the API client and navigation helper.

## Getting started

Docker Engine with Docker Compose, or Docker Desktop, must be **running**. The first build requires an internet connection to download images and dependencies.

### First start or changes to both applications

From the repository root, build and start in the background, waiting for healthy services:

```bash
docker compose up --build -d --wait --wait-timeout 180
```

The first build can take several minutes while Docker downloads images and dependencies. The backend build runs Maven verification, including unit tests; the frontend build installs dependencies, checks TypeScript and compiles with Vite. Docker reuses cached layers when their inputs are unchanged. The wait timeout applies to service readiness and does not limit image build time.

| Page | Behavior |
|---|---|
| [Home](http://localhost:8080/) | Anonymous link creation and links to login and registration |
| [Log in](http://localhost:8080/login) | Email/password login for existing accounts |
| [Register](http://localhost:8080/register) | Email, password and matching password confirmation |
| [Dashboard](http://localhost:8080/dashboard) | English creation form, personal paginated list and logout; redirects to login without a valid session |

Both successful forms lead to the dashboard. Registration creates an account and then automatically calls login. The dashboard lists only the current account’s links and provides “Log out”.

### Subsequent starts without code changes

Reuse the existing images for a faster start:

```bash
docker compose up -d
```

This does not rebuild images, so source changes will not appear until you rebuild the affected application. There is no hot reload.

### Rebuild only the changed application

When the other services are already running, rebuild and recreate only the affected service:

```bash
# Frontend changes only
docker compose build frontend
docker compose up -d --no-deps frontend

# Backend changes only
docker compose build backend
docker compose up -d --no-deps backend
```

`--no-deps` leaves dependencies alone and does not start stopped PostgreSQL, Redis or backend services. If the stack is stopped, run `docker compose up -d` afterward to start all services using the rebuilt images.

To see build or startup errors directly in the terminal, use foreground mode:

```bash
docker compose up --build
```

### Service management

```bash
# Validate the configuration
docker compose config --quiet

# Build the images
docker compose build

# Show service status
docker compose ps

# Follow service logs
docker compose logs -f

# Show recent application errors
docker compose logs --tail=100 backend frontend

# Stop and remove containers while preserving the database
docker compose down
```

The `postgres-data` volume preserves the database after `down` and after container recreation. The `--volumes` option also deletes the data; do not use it if the data must be retained.

## Configuration

The application starts with local defaults without requiring a `.env` file. To customize them, copy `.env.example` to `.env` and edit the values. `.env` is excluded from Git.

| Compose variable | Default | Purpose |
|---|---|---|
| `PUBLIC_PORT` | `8080` | Public Nginx port |
| `POSTGRES_DB` | `shortener` | Database name |
| `POSTGRES_USER` | `shortener` | PostgreSQL user |
| `POSTGRES_PASSWORD` | `shortener-local` | Local PostgreSQL password |
| `SHORTENER_PUBLIC_BASE_URL` | `http://localhost:${PUBLIC_PORT}` | Public HTTP/HTTPS origin used in returned short links |
| `JWT_SECRET` | Empty; a random signing key is generated at startup | Optional Base64-encoded signing secret of at least 32 bytes; retains token validity across backend restarts |

Default credentials are for local development only. Compose passes `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, and `JWT_SECRET` to the backend. The names `postgres` and `redis` are internal Docker addresses. Frontend API calls use relative `/api/v1/*` paths through Nginx.

Flyway applies `V1__create_users.sql` for users, `V2__create_links.sql` for links, `V3__expand_link_short_code.sql` for 32-character short codes and `V4__add_link_owner.sql` for nullable ownership and a listing index, with unique constraints on email and short code. The merged numbering preserves the local authentication database, which already applied users V1. A database from the separate US01 branch that already recorded links as V1 requires explicit Flyway-history reconciliation before using this merged version; do not delete volumes or blindly repair checksums. A database that already applied ownership as V3 from the separate US05 branch also requires explicit Flyway-history reconciliation; this merge preserves the alias V3 already applied locally. Hibernate does not generate tables. Coordinate migration versions before adding new ones.

Spring Security allows anonymous link creation (`POST /api/v1/links`), redirects (`GET` and `HEAD /r/{shortCode}`), registration, login and the internal health check. Other routes remain protected: unauthenticated GET requests receive JSON `401`; modifying requests without CSRF tokens receive `403`. Link creation, registration and login POST requests are exempt from CSRF checks. Form login, HTTP Basic, request caching and the generated demo user are disabled. Protected APIs accept an `Authorization: Bearer <token>` header; Swagger/OpenAPI also remain protected. An explicitly supplied invalid Bearer token still receives `401`, including on public routes.

## Verification

After starting the services:

```bash
bash scripts/verify.sh
```

The script uses Docker to verify:

- Backend health, including PostgreSQL and Redis connectivity.
- The home, registration, login and dashboard HTML entry points and the presence of compiled React content. Browser interaction is covered separately by frontend tests and manual checks.
- Forwarding of protected API, Swagger, and OpenAPI GET requests to the backend, with `401` expected without authentication.
- Public redirects through Nginx: `302` with the original `Location` and `Cache-Control: no-store`, plus an English HTML `404` for a missing code.
- Anonymous link creation and invalid URL rejection without insertion.
- Registration through Nginx (`201`) and duplicate rejection (`409`), using a unique synthetic email per run.
- Blocking of external access to Actuator.
- Isolation of two authenticated accounts, excluding anonymous links, before and after container recreation; fresh login after restart.
- PostgreSQL cluster, link and registered-account persistence after container recreation, including a working redirect for the persisted link.

Verification recreates the application containers, causing a brief interruption. Each run leaves an anonymous verification link, two personal links and two synthetic `verify-<uuid>-a/b@example.com` accounts in the development database; it does not delete existing data or volumes.

If `JWT_SECRET` is empty, backend recreation invalidates existing sessions; sign in again afterward. Registered accounts remain in PostgreSQL.

The backend build runs Maven `verify`; the frontend build runs TypeScript and Vite. Ordinary Maven verification excludes tests tagged `integration`; the explicit integration profile runs them with Docker access (see below).

To regenerate the lockfile without installing npm on the host:

```bash
docker run --rm --mount "type=bind,source=$PWD/frontend,target=/workspace" -w /workspace node:22.21.1-alpine npm install --package-lock-only --ignore-scripts
```

## API endpoints

### Implemented

| Method | Path | Authentication | Behavior |
|---|---|---|---|
| `POST` | `/api/v1/links` | Optional Bearer JWT; required for a nonblank alias | Create an anonymous or personal link, with an optional custom alias |
| `GET`, `HEAD` | `/r/{shortCode}` | Public | `302` to the destination; HTML `404` if absent and `503` if persistence is unavailable |
| `GET` | `/api/v1/links?page=0&size=20` | Bearer JWT | List only the current user’s links |
| `POST` | `/api/v1/auth/register` | Public | Create an account; return `201` with its public fields |
| `POST` | `/api/v1/auth/login` | Public | Verify credentials; return `200` with a JWT and expiry |
| `GET` | `/api/v1/auth/me` | Bearer JWT | Return the authenticated user's public fields |

The registration endpoint itself does not issue a token; automatic login is performed by the frontend with a separate request. Detailed contracts appear below.

### Planned

These endpoints **have not been implemented yet**:

| Method | Path | Future behavior |
|---|---|---|
| `DELETE` | `/api/v1/links/{shortCode}` | Delete a link |
| `GET` | `/api/v1/links/{shortCode}/stats` | Retrieve link statistics |

The `/r/` prefix separates short links from SPA pages. Nginx already reserves these paths: API and redirect requests do not receive the frontend HTML fallback.

## Roadmap

1. **Core links:** creation, persistence and public `302` redirects (US01–US02) are implemented.
2. **Backend foundations:** extend the existing account migration, validation, error handling, tests and API documentation to link features.
3. **Performance:** Redis caching with TTL and rate limiting.
4. **Account management:** add deletion and automatic copying to the implemented personal dashboard with custom aliases.
5. **Analytics:** click events, expiration, and statistics.

Hot reload, CI workflows, public deployment, Kafka, Kubernetes, and advanced monitoring are outside the current implementation scope.

## US01 — Create a link


```http
POST /api/v1/links
Content-Type: application/json

{"originalUrl":"https://example.com/a-long-path?query=value#section"}
```

A successful `201 Created` response is returned only after commit:

```json
{
  "originalUrl": "https://example.com/a-long-path?query=value#section",
  "shortCode": "aB3dE7fG",
  "shortUrl": "http://localhost:8080/r/aB3dE7fG"
}
```

Validation strips surrounding whitespace, requires an absolute HTTP/HTTPS URL with a host, and permits at most 8192 characters after stripping. Localhost, IPv4, IPv6, and private IP destinations are allowed. Internal whitespace, malformed escapes, invalid ports, relative URLs, and other schemes are rejected. Destinations are not fetched or checked for availability; path, query, and fragment are preserved. Missing or null destinations and malformed JSON return `400` without insertion.

Codes use eight random Base62 characters. PostgreSQL enforces uniqueness. Code collisions are retried in separate transactions up to five attempts; other persistence failures and exhausted retries return `503` without reporting success.

Errors have `code`, `message`, and nullable `field`:

```json
{"code":"INVALID_URL","message":"Enter a valid HTTP/HTTPS URL with a host, up to 8,192 characters.","field":"originalUrl"}
```

Malformed JSON uses `INVALID_REQUEST`; persistence failures use `SERVICE_UNAVAILABLE`. Error messages do not expose database details.

## US08 — Choose a custom alias

The dashboard reuses the creation form with an optional **Custom alias** field. It sends a Bearer token even when the alias is blank. The home form sends only `originalUrl`: without a session it is anonymous; with an active session it sends the JWT and creates a personal link.

```http
POST /api/v1/links
Authorization: Bearer <token>
Content-Type: application/json

{"originalUrl":"https://example.com/promotion","alias":"Promo-2026"}
```

After commit, the API returns `201` with the existing response shape: `originalUrl`, `shortCode` (here `Promo-2026`), and the complete `shortUrl` (for example `http://localhost:8080/r/Promo-2026`). Opening this URL remains public.

Alias rules:

- Strip surrounding whitespace using Java `String.strip()`; internal whitespace is invalid. Non-breaking spaces are not stripped and are invalid.
- An omitted, `null`, empty or whitespace-only alias generates an eight-character Base62 code, without requiring login.
- A nonblank alias requires a valid JWT and must match `^[A-Za-z0-9_-]{3,32}$`: 3–32 ASCII letters, numbers, hyphens or underscores.
- Preserve case: `Promo` and `promo` are distinct. No reserved words apply because redirects use `/r/{shortCode}`.
- Aliases and generated codes share one global namespace across all users. An existing generated code also blocks a matching alias.
- Aliases must be JSON strings or `null`; numbers, booleans, arrays and objects are rejected without coercion.

| Status | Code | Behavior |
|---|---|---|
| 400 | `INVALID_ALIAS` | Invalid alias; `field` is `alias` |
| 400 | `INVALID_REQUEST` | Malformed JSON or a non-string alias |
| 401 | `UNAUTHORIZED` | Nonblank alias without JWT; message asks the user to log in |
| 409 | `ALIAS_ALREADY_TAKEN` | “This alias is already taken. Choose another alias.”; `field` is `alias` |
| 503 | `SERVICE_UNAVAILABLE` | Persistence or commit failure |

Link API errors retain `{code, message, field}`, including `401` for missing, invalid, expired or unknown-user identities. Invalid or expired supplied Bearer tokens are rejected by Spring Security even on requests without an alias. Other API routes retain Problem Detail authentication errors. Authentication for a nonblank alias is checked before alias/URL validation or persistence.

PostgreSQL's existing `uk_links_short_code` constraint guarantees uniqueness under concurrency. An explicit alias is inserted once: a collision returns `409`, without generating another code. Automatic codes retain up to five attempts in separate transactions. Success is returned only after commit. The dashboard focuses alias errors and preserves input on validation, conflict or service failure; `401` clears the session and returns to login.

Alias creation integrates with ownership and listing from US05: a JWT associates the new link with the current user and the dashboard refreshes the personal list. Existing anonymous links remain unowned. Deletion, expiration and Redis caching remain planned.

## US02 — Open a short link

Open the `shortUrl` returned by link creation, including links created before US02:

```http
GET /r/aB3dE7fG
```

For an existing code, the response has an empty body:

```http
HTTP/1.1 302 Found
Location: https://example.com/a-long-path?query=value#section
Cache-Control: no-store
```

Resolution reads PostgreSQL in a read-only transaction. Codes are case-sensitive and are looked up without trimming, case conversion or a new length restriction. Destination paths, queries, fragments and existing percent escapes are preserved; Unicode characters are encoded as an ASCII URI without double encoding. Query parameters added to the short URL are not forwarded. The destination is never fetched or checked for availability.

A missing code returns `404` with `Content-Type: text/html; charset=UTF-8`, the English title “Link not found”, an explanation and a “Back to home” link. Database or transaction failures return an English HTML `503 Service unavailable` page without internal details. Error responses have no `Location`. All these responses use `Cache-Control: no-store`. `HEAD` is public and returns the corresponding status and headers without a body.

The HTML error pages are served directly by the backend and work without JavaScript. No schema migration, Redis caching, click analytics or expiration is introduced by US02.

## US03 — Registration

Open [http://localhost:8080/register](http://localhost:8080/register), also linked from the home page. The form handles loading, password confirmation, validation, duplicates and network errors. Its Log in/Register navigation connects `/login` and `/register`. After account creation, the frontend calls login automatically, saves the session and opens `/dashboard`. If automatic login fails, it explains that the account was created and asks the user to switch to Log in, without attempting another registration. No email verification or password recovery is implemented.

```http
POST /api/v1/auth/register
Content-Type: application/json

{"email":"  Utente@Example.com  ","password":"una password sufficientemente lunga"}
```

Email is required, stripped of surrounding Unicode whitespace with Java `strip()`, lowercased with `Locale.ROOT`, then validated with Jakarta Bean Validation `@Email` and a maximum length of 30. No provider-specific transformations are applied. Uniqueness applies to the normalized address, including simultaneous requests.

Password is required and must have 6–18 UTF-16 code units (`String.length()` in Java, `.length` in JavaScript). Spaces and Unicode are accepted without trimming or normalization; supplementary Unicode characters count as two units. No mandatory character combinations are imposed. Only a salted PBKDF2 hash is stored, using Spring Security `defaultsForSpringSecurity_v5_8()` (PBKDF2-HMAC-SHA256, 310,000 iterations, 16-byte salt, 256-bit hash) with the `{pbkdf2@SpringSecurity_v5_8}` identifier. Login verifies the original password through the same reusable `PasswordEncoder`. Password confirmation is checked only in the registration form and is never sent to the API or stored.

Successful registration returns `201` with exactly `id` (UUID), normalized `email`, and `createdAt` (UTC ISO-8601). It does not return credentials, a JWT or a session cookie.

Errors use `application/problem+json`, standard ProblemDetail fields and an additional stable `code`:

| Status | Code | Meaning |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Invalid fields; `fieldErrors` maps field names to English messages |
| 400 | `INVALID_JSON` | Missing or malformed JSON |
| 409 | `EMAIL_ALREADY_REGISTERED` | Normalized email already exists |
| 500 | `INTERNAL_ERROR` | Generic internal failure without implementation details |

Example validation response (the `instance` contains the request path):

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Check the highlighted fields.",
  "instance": "/api/v1/auth/register",
  "code": "VALIDATION_ERROR",
  "fieldErrors": {"password": "Your password must contain 6 to 18 characters."}
}
```

## Feature tests

With Java 21 and Maven installed, run `mvn verify` in `backend/` for unit tests or `mvn verify -Pintegration` for the full suite with Docker available. On Docker 29, add `-Dapi.version=1.44` to the latter command if needed.

Without host Maven, use the dedicated runner from the repository root:

```bash
# Unit tests only, also run during the backend image build
docker compose --profile test run --rm backend-tests mvn -B -ntp verify

# Unit tests and real PostgreSQL integration tests — required for account and link features
docker compose --profile test run --rm backend-tests mvn -B -ntp verify -Pintegration
```

The integration profile runs both tagged authentication tests through Surefire and link `*IT` tests through Failsafe. `bash scripts/test-integration.sh` is an alternative Docker runner.

The optional `backend-tests` service mounts the backend checkout, a Maven cache and the Docker socket. It uses `host.docker.internal` for Docker Desktop; `extra_hosts` provides the host gateway on Linux. It pins the docker-java API to 1.44 for compatibility with Docker 29; override `DOCKER_API_VERSION` if necessary. Testcontainers creates a separate temporary PostgreSQL instance and removes it afterward; it does not use the application's data volume. The profile is not started with ordinary `docker compose up`.

Frontend tests and build:

```bash
cd frontend
npm ci
npm test
npm run build
```

For full verification, run the integration suite above, frontend tests, `docker compose build`, `docker compose up -d --wait --wait-timeout 180`, and `bash scripts/verify.sh`. In the browser, create and open a short link without login, check the missing-code page and its home link, navigation between Log in and Register, mismatched password confirmation, successful registration/login, dashboard refresh, keyboard navigation, and desktop/mobile layouts. A passing application build alone does not verify the browser flow or container persistence. Public deployment remains outside scope.

## Login and dashboard

`POST /api/v1/auth/login` accepts `email` and `password`; email is normalized like registration and password is preserved exactly. Existing accounts are compatible without a migration. Wrong passwords and unknown emails both return `401 INVALID_CREDENTIALS` with the same message. Malformed input returns `400`.

A successful response (`200`) contains `accessToken`, `tokenType: "Bearer"`, and `expiresAt` in UTC. The token is signed with HS256, identifies the user by UUID, has issuer `url-shortener`, and expires after one hour. No cookie is created. The frontend stores the token in per-tab `sessionStorage`, preserves it across refreshes, and sends it as a Bearer header to `GET /api/v1/auth/me`. That endpoint returns only `id`, `email`, and `createdAt`. A missing, invalid or expired session redirects the dashboard to `/login`; transient verification failures allow a retry without deleting the session.

`/dashboard` shows an English creation form, the current user’s links and “Log out”. Logout clears per-tab storage, aborts requests and replaces the current history entry with `/login`. Refresh and back navigation recheck access. Expired or rejected sessions return to login with an explanation; network errors offer retry while retaining the session. Late responses cannot invalidate a newer login. Logout is client-side: no revocation endpoint or JWT blacklist is provided.

`JWT_SECRET` is optional for local development: set it to the Base64 encoding of at least 32 random bytes to retain token validity across backend restarts. For example, generate a value with `openssl rand -base64 32` and keep it in the ignored `.env` file. If empty, a random key is generated at startup, so restarting the backend invalidates existing tokens; accounts remain persisted. Never commit an actual signing secret. The `.env.example` and Compose configuration include this variable.

## US05 — Personal links

`POST /api/v1/links` preserves the US01 response and adds the optional US08 alias field. Without a Bearer token and nonblank alias it creates an anonymous link; with a valid token it uses only the token subject as owner and checks that the user exists. Invalid, expired and unknown-user tokens return `401` without creating an anonymous fallback. Client-supplied owner fields do not determine ownership. Existing links retain `owner_id = NULL` and cannot be claimed by signing in.

```http
GET /api/v1/links?page=0&size=20
Authorization: Bearer <accessToken>
```

```json
{
  "items": [{
    "originalUrl": "https://example.com/page",
    "shortCode": "aB3dE7fG",
    "shortUrl": "http://localhost:8080/r/aB3dE7fG",
    "createdAt": "2026-10-08T10:00:00Z",
    "status": "SAVED"
  }],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
}
```

Pages start at zero; size defaults to 20 and accepts 1–100. Negative pages, non-integer parameters, invalid sizes and unsupported parameters (including `userId`) return `400 INVALID_REQUEST`. Results are ordered by `created_at DESC, id DESC`. An empty or out-of-range page returns `200` with an empty `items` array. Authentication failures return `401 UNAUTHORIZED`; unavailable persistence returns `503 SERVICE_UNAVAILABLE`. Link errors use `{code, message, field}` with no SQL details. OpenAPI documents both link operations and Bearer authentication.

The dashboard translates `SAVED` as “Saved”, localizes dates in English, and displays selectable short URLs. Public redirects are available through `/r/{shortCode}`. Creation returns to the first page and refreshes the list. A failed refresh explicitly confirms that creation succeeded and offers retry. Home copy remains English; when a session is present its creation request also sends the JWT, without retrying as anonymous.

Integration tests cover legacy-data migration, owner preservation across code collisions, account isolation, pagination and ordering. Frontend tests cover expiry, refresh, logout/history protection, stale responses, cancellation, loading, retry and refresh after creation.


## US06 — Copy a short link

“Copy link” is available after anonymous or authenticated creation and beside each personal link in the dashboard. It copies the complete `shortUrl` returned by the API without another HTTP request. Each button has independent feedback, is disabled while copying and displays “Link copied.” only after the clipboard write succeeds. Changing the URL resets feedback and ignores late results from an earlier copy.

Copying uses `navigator.clipboard.writeText` in a secure context: `http://localhost:8080` works for local development; public hosting requires HTTPS. If the API is unavailable or the browser rejects the write, an accessible error explains how to select the address and copy it manually. The read-only URL remains selectable. No legacy copying fallback or permission preflight is used.

Vitest tests cover the full copied value, pending state, failure/retry, unavailable clipboard, URL changes, late completion, Italian feedback, keyboard activation, anonymous/authenticated creation and independent dashboard rows without additional requests. Frontend build and the 17 link-feature tests passed during US06 implementation. Registration tests have been aligned with the current email/password limits. The full frontend suite passes 41 tests, and the standard Docker build passes 57 backend unit tests. Both updated services have been started through Docker Compose. Real clipboard paste and desktop/mobile visual checks remain pending because browser automation was unavailable in this session; verify these manually on localhost before marking end-to-end verification complete.


## US07 — Delete a personal link

`DELETE /api/v1/links/{shortCode}` requires a Bearer JWT. Ownership comes exclusively from the JWT subject. One transactional SQL deletion matches both code and owner; no client-supplied owner or separate ownership check is used. Successful deletion returns `204 No Content` after commit. Missing, anonymous, other users' and already deleted links return the same `404 LINK_NOT_FOUND`; missing/invalid/expired sessions return `401 UNAUTHORIZED`, and persistence failures return `503 SERVICE_UNAVAILABLE`. Errors use the link API's `{code, message, field}` JSON format. No migration is required.

The dashboard offers “Delete link”, followed by an inline permanent-deletion confirmation with “Delete permanently” and “Cancel”. Focus moves to Cancel on opening, returns to Delete link on cancellation, and moves to the list heading after a successful refresh. Only one deletion runs at a time; creation and pagination are blocked during deletion and refresh. The client accepts an empty 204 without parsing JSON and retains existing authenticated-request cancellation and session protection.

After deletion, the current page is reloaded. If that page is empty, the last valid page is loaded. A failed DELETE preserves the link and allows retry; a failed refresh after successful deletion explicitly reports that deletion succeeded and offers a list reload. A 404 DELETE reports that the link is unavailable and reloads the list. Public GET/HEAD redirects subsequently return 404.

Redis does not yet cache redirects. US11 must invalidate the actual cache entries when deleting and prevent a concurrent resolution from repopulating a deleted link; this change introduces no speculative Redis keys or cache.

Six focused US07 test scenarios were added: three backend scenarios (one parameterized) and three frontend scenarios. The relevant backend command is:

```bash
docker compose --profile test run --rm backend-tests mvn -B -ntp verify -Pintegration -Dtest=LinkServiceTest,LinkControllerTest,RedirectControllerTest,LinkResolutionServiceTest -Dit.test=LinkOwnershipIT,LinkPostgresIT
```

It passed 23 unit tests and 13 PostgreSQL integration checks. Frontend feature/session/navigation checks passed 31 tests and the frontend build passed. After aligning registration test fixtures with the current validation rules, the full frontend suite passes all 41 tests. Real-browser confirmation, keyboard and visual verification remain pending because browser automation was unavailable/not authorized in this session.

The standard Docker build now passes all 57 backend unit tests. `docker compose up --build -d --wait --wait-timeout 180` successfully started the updated backend and frontend; Delete link is included in the served dashboard bundle.
