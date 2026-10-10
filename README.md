# URL Shortener

A URL-shortener project built with a **Java/Spring Boot backend**, a **React/TypeScript frontend**, **PostgreSQL**, and **Redis**. Anonymous short-link creation, authenticated custom aliases, public redirects, account registration and login are available. Builds and services run through **Docker Compose**.

## Project status

The project implements US01–US11: anonymous and authenticated creation, registration, JWT login, client logout, a personal dashboard, one-click link copying and owner-only deletion, custom aliases, optional expiration and Redis redirect caching for a single backend. Owner-only statistics include saved GET clicks and 30 daily UTC counts. Rate limiting remains planned.

| Available | To be implemented |
|---|---|
| Link creation, public redirects, personal listing, deletion, account and statistics APIs | Rate limiting |
| English home, account forms, personal dashboard, copying, deletion and inline statistics | Custom analytics date ranges |
| PostgreSQL persistence and Redis redirect caching | Rate limiting |
| Flyway users/links/click-event migrations and persistence | Future domain migrations |
| Registration, JWT login, expiry and client logout | JWT revocation (outside current scope) |
| Authentication endpoint documentation, Swagger UI, and health checks | Documentation for remaining endpoints |
| Multi-stage Dockerfiles, Docker Compose and custom aliases | Durable analytics delivery |
| Infrastructure, registration and persistence checks; feature tests | Remaining feature tests |

**Registration and login are implemented:** `/register` includes password confirmation; `/login` checks existing accounts. Both successful flows navigate to `/dashboard`, with authenticated creation, optional custom aliases, paginated personal links and “Log out”. Registration automatically logs in through the login API. Statistics are available through the Statistics button on each personal link, including expired links. Generated short URLs can be copied with one click or manually and open their destinations without login.

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

Health checks ensure that PostgreSQL is healthy before the backend starts, and that the backend is healthy before the frontend starts. Redis is optional for backend startup and health: redirects fall back to PostgreSQL when it is unavailable. Redis retains its own container health check. Actuator exposes `/actuator/health` internally, checking the database without revealing sensitive details. Nginx blocks external access to this endpoint and exposes `/healthz` for its own health check. `docker compose up --wait` still checks Redis and may report failure even while the backend remains usable; to start without Redis, use `docker compose up -d postgres backend frontend`.

The Dockerfiles use multi-stage builds: Maven/JDK produces the JAR executed by Java, while Node/npm produces the static files served by Nginx. Maven and npm do not need to be installed on the host.

### Recovering the legacy swapped V3/V4 history

If startup reports V3 applied checksum `772876360` versus local `-1975026989`, and V4 reports the reverse, the database used the earlier expansion/ownership ordering. Keep the current migration files unchanged. Stop the backend and take a full PostgreSQL backup before reconciling history; retain that backup privately because it contains account data.

The manual script `scripts/reconcile-swapped-link-migrations.sql` accepts only that exact legacy history and verifies the 32-character code column, nullable UUID owner, foreign key and listing index. It atomically swaps the recorded versions and script names, preserving each migration's checksum, description, installation rank and timestamp. It does not rerun either migration or change application data. It aborts for any other history, including an already reconciled database. This is specific to the public schema and does not address databases with links originally recorded as V1.

After backing up, run from PowerShell:

```powershell
docker compose stop backend
Get-Content -Raw scripts/reconcile-swapped-link-migrations.sql | docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
if ($LASTEXITCODE -ne 0) { throw 'Flyway reconciliation failed' }
docker compose up -d --wait --wait-timeout 180
```

Normal Flyway validation remains enabled; startup applies any pending migrations. Do not use a blanket repair or delete the database volume to resolve this mismatch.

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

Implemented backend features use role subpackages without moving responsibilities outside their feature:

| Feature | Subpackages |
|---|---|
| `link` | `controller`, `dto`, `service`, `entity`, `repository`, `config`, `exception` |
| `auth` | `controller`, `dto`, `service`, `exception` |
| `user` | `entity`, `repository` |
| `security` | `config`, `service` |
| `shared` | `config`, `exception` |

Link services, validators, code generation, and redirect-cache helpers share `link/service`; this preserves package-private locking and cache coordination. Controllers and redirect HTML error handling live in `link/controller`, while JSON deserializers live alongside request DTOs in `link/dto`. Focused tests mirror the production subpackage, and broad integration tests remain at the feature root. `RedirectCacheIT` lives alongside the services because it verifies package-private cache coordination. The `analytics` backend feature uses JDBC for click events and transactional statistics, and the frontend analytics feature provides the dashboard panel. Only `ratelimit` remains a placeholder.

Generated design-session files and review screenshots (`.impeccable/` and `artifacts/`) are excluded from Git. Installed repository skills and the product/design documentation are retained.

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
| `ANALYTICS_QUEUE_CAPACITY` | `1000` | Positive capacity of the bounded in-memory click recording queue |
| `REDIRECT_CACHE_ENABLED` | `true` | Enable optional Redis redirect caching |
| `REDIRECT_CACHE_TTL` | `5m` | Positive cache lifetime, at least 1ms; hits do not renew it |
| `REDIS_CONNECT_TIMEOUT` | `100ms` | Redis connection timeout |
| `REDIS_TIMEOUT` | `100ms` | Redis command timeout |

Default credentials are for local development only. Compose passes `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, and `JWT_SECRET` to the backend. The names `postgres` and `redis` are internal Docker addresses. Frontend API calls use relative `/api/v1/*` paths through Nginx.

Flyway applies `V1__create_users.sql` for users, `V2__create_links.sql` for links, `V3__add_link_owner.sql` for nullable ownership and a listing index, `V4__expand_link_short_code.sql` for 32-character short codes `V5__add_link_expiration.sql` for nullable expiration and `V6__create_click_events.sql` for click events with a cascading link foreign key and time index, with unique constraints on email and short code. The merged numbering preserves the local authentication database, which already applied users V1. A database from the separate US01 branch that already recorded links as V1 requires explicit Flyway-history reconciliation before using this merged version; do not delete volumes or blindly repair checksums. The numbering preserves the existing local V3 ownership migration without changing its contents or database history. A different database that already applied expansion as V3 and ownership as V4 needs explicit migration-history reconciliation before upgrading; do not repair it blindly. Hibernate does not generate tables. Coordinate migration versions before adding new ones.

Spring Security allows anonymous link creation (`POST /api/v1/links`), redirects (`GET` and `HEAD /r/{shortCode}`), registration, login and the internal health check. Other routes remain protected: unauthenticated GET requests receive JSON `401`; modifying requests without CSRF tokens receive `403`. Link creation, registration and login POST requests are exempt from CSRF checks. Form login, HTTP Basic, request caching and the generated demo user are disabled. Protected APIs accept an `Authorization: Bearer <token>` header; Swagger/OpenAPI also remain protected. An explicitly supplied invalid Bearer token still receives `401`, including on public routes.

## Verification

After starting the services:

```bash
bash scripts/verify.sh
```

The script uses Docker to verify:

- Backend health, including PostgreSQL connectivity; Redis is optional.
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
| `GET` | `/api/v1/links/{shortCode}/stats` | Bearer JWT | Owner-only lifetime saved GET clicks and 30 daily UTC counts |
| `DELETE` | `/api/v1/links/{shortCode}` | Bearer JWT | Permanently delete a personal link and invalidate its cached redirect |
| `POST` | `/api/v1/auth/register` | Public | Create an account; return `201` with its public fields |
| `POST` | `/api/v1/auth/login` | Public | Verify credentials; return `200` with a JWT and expiry |
| `GET` | `/api/v1/auth/me` | Bearer JWT | Return the authenticated user's public fields |

The registration endpoint itself does not issue a token; automatic login is performed by the frontend with a separate request. Detailed contracts appear below.

The `/r/` prefix separates short links from SPA pages. Nginx already reserves these paths: API and redirect requests do not receive the frontend HTML fallback.

## Roadmap

1. **Core links:** creation, persistence and public `302` redirects (US01–US02) are implemented.
2. **Backend foundations:** extend the existing account migration, validation, error handling, tests and API documentation to link features.
3. **Performance:** Redis caching with TTL is implemented for a single backend; rate limiting remains planned.
4. **Account management:** deletion, one-click copying, custom aliases and optional expiration are implemented in the personal dashboard.
5. **Analytics:** asynchronous best-effort GET click events and owner-only statistics are implemented.

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

Alias creation integrates with ownership and listing from US05: a JWT associates the new link with the current user and the dashboard refreshes the personal list. Existing anonymous links remain unowned. Owner-only deletion and optional expiration are implemented; Redis caching is integrated and respects expiration.

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

Resolution first checks Redis. A cache miss or cache failure reads PostgreSQL in a read-only transaction; a successful lookup populates Redis after the transaction ends. Codes are case-sensitive and are looked up without trimming, case conversion or a new length restriction. Destination paths, queries, fragments and existing percent escapes are preserved; Unicode characters are encoded as an ASCII URI without double encoding. Query parameters added to the short URL are not forwarded. The destination is never fetched or checked for availability.

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

# Unit tests and real PostgreSQL/Redis integration tests — required for account and link features
docker compose --profile test run --rm backend-tests mvn -B -ntp verify -Pintegration
```

The integration profile runs both tagged authentication tests through Surefire and link `*IT` tests through Failsafe. `bash scripts/test-integration.sh` is an alternative Docker runner.

The optional `backend-tests` service mounts the backend checkout, a Maven cache and the Docker socket. It uses `host.docker.internal` for Docker Desktop; `extra_hosts` provides the host gateway on Linux. It pins the docker-java API to 1.44 for compatibility with Docker 29; override `DOCKER_API_VERSION` if necessary. Testcontainers creates temporary PostgreSQL and Redis instances and removes them afterward; it does not use the application's data volume. Existing PostgreSQL-only tests disable redirect caching explicitly. The profile is not started with ordinary `docker compose up`.

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

US11 invalidates the actual cached redirect after the deletion commits and before returning 204. Local coordination prevents concurrent resolutions from repopulating deleted entries. If Redis invalidation fails, the affected namespace is abandoned so recovery cannot revive a deleted redirect. A redirect already in progress may complete; requests starting after 204 return 404.

Six focused US07 test scenarios were added: three backend scenarios (one parameterized) and three frontend scenarios. The relevant backend command is:

```bash
docker compose --profile test run --rm backend-tests mvn -B -ntp verify -Pintegration -Dtest=LinkServiceTest,LinkControllerTest,RedirectControllerTest,LinkResolutionServiceTest -Dit.test=LinkOwnershipIT,LinkPostgresIT
```

It passed 23 unit tests and 13 PostgreSQL integration checks. Frontend feature/session/navigation checks passed 31 tests and the frontend build passed. After aligning registration test fixtures with the current validation rules, the full frontend suite passes all 41 tests. Real-browser confirmation, keyboard and visual verification remain pending because browser automation was unavailable/not authorized in this session.

The standard Docker build now passes all 57 backend unit tests. `docker compose up --build -d --wait --wait-timeout 180` successfully started the updated backend and frontend; Delete link is included in the served dashboard bundle.


## US09 — Optional expiration

Authenticated creation accepts `expiresAt` alongside an optional alias:

```json
{"originalUrl":"https://example.com/page","alias":"Timed-Link","expiresAt":"2030-01-01T12:00:00+01:00"}
```

`expiresAt` must be an ISO-8601 string with `Z` or an offset, or omitted/null for no expiration. Non-string, malformed and offset-free values return `400 INVALID_REQUEST`; a past or current timestamp returns `400 INVALID_EXPIRATION` with `field: "expiresAt"`. Non-null expiration requires a valid JWT (`401` otherwise). The server truncates to PostgreSQL microsecond precision and checks the value against its UTC Clock immediately before each insert attempt. Creation and listing return `expiresAt` in UTC or null; older links remain unexpired after V5.

The dashboard creation form offers “Expiration (optional)” in the browser’s local timezone, named beside the field. It accepts minute precision, converts to UTC and rejects invalid/normalized local dates or times, including daylight-saving gaps. Ambiguous repeated hours use the browser's local Date conversion. URL, alias and expiration survive errors. The result and personal list display “Expires”, local date/time and timezone, or “No expiration”. Listing status is `SAVED` until expiration and `EXPIRED` at or after it. Status updates on list load/refresh without polling; redirects check current server time on every request.

At `expiresAt <= now`, GET and HEAD `/r/{shortCode}` return `410 Gone`, an English accessible expiration page for GET, no body for HEAD, no `Location` and `Cache-Control: no-store`. Missing or deleted links remain 404. Expiration does not delete a link or release its alias; owners can still copy or delete expired links.

The migration sequence preserves the local deployed V3 ownership migration, expands codes in V4 and adds nullable `TIMESTAMPTZ expires_at` in V5. No database history repair or data deletion is needed for that history. A database with a different already-applied V3/V4 ordering requires explicit reconciliation, not blind repair.

Real-browser checks on localhost verified creation with and without expiration, alias plus expiration, Europe/Rome display, accessible validation preserving the inputs and keyboard submission/cancellation. A live link opened its destination before expiration and showed “Link expired” afterwards; dashboard refresh showed “Expired”, and copying the expired link wrote the full URL to the real clipboard. GET/HEAD status and header assertions are covered by the integration suite; an additional terminal HTTP check was declined.

Historical US09 verification: six focused scenarios cover legacy migration and persistence, validation/authentication, the exact time boundary, HTTP/list ownership, local form conversion/errors and dashboard actions. Frontend tests pass 56 checks; backend unit tests pass 72 checks; the PostgreSQL suites passed 22 checks before the source migration ordering was restored to match the existing local database. A rerun of those integrations was declined; the standard Docker build and actual local migration/startup verified the restored ordering. The merged US11 path carries expiresAt, checks it before returning a destination, and caps TTL at remaining lifetime; TTL alone is insufficient.

## US11 — Fast redirects with Redis

Valid resolutions use Redis first and fall back to PostgreSQL on a miss, malformed payload or cache failure. Successful database lookups populate Redis outside the read-only transaction. Only successful resolutions are cached; missing codes and database errors are not. An existing cache hit needs no database query, and remains usable during a PostgreSQL outage. A database failure on a miss retains the HTML 503 response.

The default TTL is five minutes and is not renewed on hits. Set `REDIRECT_CACHE_TTL` to change it, or `REDIRECT_CACHE_ENABLED=false` to bypass Redis. Connection and command timeouts default to 100ms each. These bound the respective Redis operations, not total HTTP latency, which also includes database access and coordination. Keys use `shortener:redirect:v2:<process>:<group>:<generation>:<exact-code>` and JSON stores a version, persistent link ID, original URL and optional expiration. Version 1 payloads and missing/nonpositive IDs are rejected. URLs and authentication data are never logged by the cache; cache warnings are limited to one per 30 seconds.

The single backend shares 256 fair read/write lock groups between resolution and deletion. Deletion waits for earlier resolutions, commits to PostgreSQL, and invalidates the actual Redis key before returning 204. Failed cache operations abandon the group's namespace, preventing old entries from being read after recovery and preventing late writes from entering a new generation. Obsolete keys expire normally; no database-wide Redis flush is used. Backend startup generates a new process namespace. Failed deletion commits also abandon the namespace in case the database outcome is uncertain.

Requests beginning after a successful deletion return 404; redirects already in progress may finish with 302. Alias reuse resolves the new destination. This guarantee covers application operations in one backend process, including Redis outages and restart; multiple concurrent backends and external SQL mutations require a different consistency design.

US09 supplies persisted expiration, API/UI support and 410 responses. Database resolutions carry expiresAt into Redis; expired database results are rejected. The shared UTC Clock drives creation, listing, resolution and cache policy. TTL is capped at remaining lifetime, and expiration is checked immediately before returning a destination, including after cache population. Expired or submillisecond cache entries fall back to PostgreSQL, preserving authoritative 404/410 responses and 503 on database failure. The historical US09/US11 merge introduced no additional schema or frontend changes. US10 now records successful GET clicks asynchronously, including cache hits, without database reads on the redirect cache-hit path. The merged expiration/cache acceptance tests pass, including expiration while a Redis key is still present.

Run unit and integration tests using the commands above. Integration tests use temporary PostgreSQL and Redis containers, including a paused Redis and a restored pre-outage cache snapshot to verify the case where invalidation was lost, and verify startup with Redis unreachable. To verify the actual built Compose stack through Nginx on Windows:

```powershell
./scripts/verify-redirect-cache.ps1
# For a different public port/origin:
./scripts/verify-redirect-cache.ps1 -BaseUrl http://localhost:8081
```

This smoke test requires an already running stack with caching enabled. It briefly pauses/stops Redis and recreates the backend, which invalidates existing JWTs when `JWT_SECRET` is unset. It restores Redis afterward and retains database volumes and existing data. Each run leaves one synthetic account and two links. It checks TTL, deletion during an outage, alias reuse, namespaces across backend recreation, and healthy startup without Redis. Functional acceptance proves that cache hits avoid PostgreSQL; no hardware-dependent latency threshold is asserted.

Historical US11 branch verification passed 84 unit tests and 34 integration tests (9 authentication and 25 link/cache scenarios), Compose validation, both Docker image builds and the Nginx smoke test above. The real Redis behavioral suite uses 1s timeouts to allow Docker Desktop host-gateway scheduling; application defaults and the startup-without-Redis test retain 100ms. Existing registration integration fixtures were aligned with the current 6–18-character password contract. No database volumes were removed.

## Merge verification — US09 and US11

The combined implementation passes 87 backend unit tests and 38 integration tests (9 registration tests in Surefire and 29 PostgreSQL/Redis tests in Failsafe), with no failures or skips, using `docker compose run --rm --no-deps backend-tests mvn -B -ntp -Pintegration clean verify`. Cleaning removes stale build resources without changing Flyway history. All 56 frontend tests pass with `npm test`; `npm run build`, Compose validation and both Docker image builds pass. Two existing frontend tests were stabilized by creating the deferred response before rendering and waiting for asynchronous focus restoration.

The new cases verify persisted expiration in Redis, capped TTL, exact expiration boundaries on database/cache paths, expiration during cache population, GET/HEAD 410 while a Redis key remains alive, and a single shared Clock bean. Historical branch verification above is separate from this merged verification. The live-stack smoke test was not rerun; no running services or database volumes were recreated or removed.


## US10 — Personal click statistics

Only successful public GET resolutions enqueue one click event; HEAD, unknown links, expired links and failed resolutions do not count. Repeat GETs and bots count separately. Anonymous link GETs are also recorded, but their statistics cannot be queried or claimed by signing in. Tracking starts when US10 is deployed; earlier clicks cannot be reconstructed.

A dedicated worker writes events to PostgreSQL from a bounded in-memory queue (`ANALYTICS_QUEUE_CAPACITY`, default 1000, minimum 1). Each event stores only the persistent link ID and request-time UTC timestamp. No IP, user agent or referrer is stored. Writes happen outside redirect transactions and never delay cache-hit resolution for a database write. Full queues, persistence failures and process termination may lose events; there is no retry or durable delivery guarantee. Counts may appear shortly after a redirect. Shutdown waits up to five seconds for queued tasks. Warnings are throttled to one per 30 seconds and omit link destinations and authentication data.

Flyway V6 adds `click_events` with an index on `(link_id, occurred_at)` and `ON DELETE CASCADE`. Deleting a link removes its stored events. Pending events for a deleted ID are discarded by the foreign key, so alias reuse starts with zero clicks. Redis keys and payloads now use version 2 with the persistent link ID; old or invalid entries fall back to PostgreSQL.

`GET /api/v1/links/{shortCode}/stats` requires a valid Bearer JWT for the link's owner. It returns `200` with `Cache-Control: no-store` for owned links, including expired links; missing, anonymous and other owners' links return the same `404 LINK_NOT_FOUND`. Missing/invalid/expired/unknown-user identity returns `401`; database failures return `503 SERVICE_UNAVAILABLE`, using the existing `{code, message, field}` error shape. Ownership, lifetime total and daily aggregation share a read-only repeatable-read database snapshot. Statistics are not cached.

The response contains `shortCode`, `totalClicks` (all saved events for that link), `timezone` (`UTC`), inclusive date strings `from`/`to`, and `daily` entries `{date, clicks}`. `daily` always contains 30 ascending UTC dates, from today minus 29 days through today, with zeros for missing days. Lifetime total can exceed the sum of the displayed days. UTC aggregation does not depend on browser or database-session timezone.

Each dashboard row has a Statistics button. Only one panel opens at a time, with loading, zero, error/retry, Refresh and Close statistics states. It displays the lifetime total, a 30-day bar chart, an accessible Daily counts table and the best-effort recording caveat. Navigation, deletion and logout cancel pending requests and suppress stale results. The existing English interface, visual system and authentication client are reused; no chart dependency was added.

### US10 verification — 2026-10-10

`docker compose -p url-shortener-us10-check run --rm --no-deps backend-tests mvn -B -ntp -Pintegration verify` passed 138 backend tests: 93 unit tests, 9 registration integrations in Surefire, and 36 PostgreSQL/Redis integrations in Failsafe. There were no failures, errors or skips. New coverage includes cache and database GET counting, HEAD/error exclusion, anonymous tracking without access, UTC boundaries/zero days/lifetime totals, expired-link access, authorization, concurrent redirects, deletion/cascade/alias reuse, rejected/full queues, failed writes, and snapshot consistency when a concurrent event commits between the total and daily queries. Existing US11 expiration, invalidation and fallback tests also pass.

All 63 frontend tests pass with `npm test`; `npm run build`, Compose configuration validation and `git diff --check` pass. Frontend tests cover on-demand loading, refresh/zero/table values, expired links, retry/malformed responses, session expiry, keyboard controls, cancellation and stale responses on selection/navigation/deletion/logout.

Docker image build was requested but permission was declined. No US10 stack was started, no new Nginx smoke test or desktop/mobile browser inspection was performed, and no existing database volume or Flyway history was changed. These deployment and visual checks remain pending; the automated results do not certify them. Historical US09/US11 results above describe earlier code and remain separate.
