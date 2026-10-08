# URL Shortener

A Java 21/Spring Boot and React/TypeScript application using PostgreSQL, Redis, and Nginx through Docker Compose.

## Current behavior

US01 is implemented: visitors can create short links without an account. The form accepts complete HTTP/HTTPS destinations, persists each submission in PostgreSQL, and displays a full short URL. Every submission generates a new code, even for an existing destination.

**Redirects are not implemented yet (US02).** Generated `/r/{shortCode}` URLs can be selected and copied manually but do not yet open the destination. One-click copying, accounts, dashboard, aliases, expiration, caching, rate limits, and analytics remain planned.

## Architecture and structure

The backend is a monolith organized by feature under `backend/src/main/java/com/example/shortener/`. The `link` module owns creation, validation, DTOs, persistence, and generation; `security` owns endpoint access; `shared` contains common API error handling. Database changes use Flyway in `backend/src/main/resources/db/migration/`; Hibernate schema generation is disabled.

The frontend uses React function components and strict TypeScript. `src/app/` owns the entry point and styles, `src/features/links/` owns the form and typed API client, and `src/shared/` is reserved for reusable code.

Nginx serves React on `http://localhost:8080`, proxies `/api/*`, `/r/*`, Swagger, and OpenAPI to the backend, and blocks external Actuator access. The backend has no published port. PostgreSQL and Redis are on the internal data network; PostgreSQL uses a named persistent volume. Redis is configured but not used by link creation.

Versions: Spring Boot 3.5.7, PostgreSQL 17.6, Redis 7.4.6, React 19.2.0, TypeScript 5.9.3, Vite 7.3.7, Node 22.21.1, Maven 3.9.11, Nginx 1.28.0. JUnit 5, Mockito, and Testcontainers cover backend behavior. No CI workflow is configured.

## Run locally

Start Docker Desktop, then run from the repository root:

```bash
docker compose config --quiet
docker compose up --build -d --wait --wait-timeout 180
```

Open http://localhost:8080. Inspect services with `docker compose ps` and `docker compose logs -f`. Stop them with `docker compose down`, which preserves database data. Do not use `--volumes` when data must be retained.

## Configuration

Copy `.env.example` to `.env` to override development defaults. Never commit credentials.

| Variable | Default | Purpose |
|---|---|---|
| `PUBLIC_PORT` | `8080` | Nginx host port |
| `SHORTENER_PUBLIC_BASE_URL` | `http://localhost:${PUBLIC_PORT}` | Public HTTP/HTTPS origin used in returned links |
| `POSTGRES_DB` | `shortener` | Database name |
| `POSTGRES_USER` | `shortener` | Database user |
| `POSTGRES_PASSWORD` | `shortener-local` | Development password |

The public base URL must be an HTTP/HTTPS origin without credentials, query, fragment, or a path other than `/`. Invalid configuration fails startup. Outside Compose, the backend defaults to `http://localhost:8080`. Compose passes database and Redis connection settings to the backend.

Only `POST /api/v1/links` is public, with CSRF excluded specifically for this method/path. Internal Actuator health is also public. Other endpoints retain HTTP Basic authentication, including Swagger and OpenAPI: the development user is `user`, with a generated password in backend startup logs. This is not an application account system.

## Create a link

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
{"code":"INVALID_URL","message":"Inserisci un URL HTTP/HTTPS valido, completo di host, entro 8192 caratteri.","field":"originalUrl"}
```

Malformed JSON uses `INVALID_REQUEST`; persistence failures use `SERVICE_UNAVAILABLE`. Error messages do not expose database details.

## Verification

```bash
docker compose build
bash scripts/verify.sh
```

The build runs Maven unit/API tests and TypeScript/Vite checks. The verification script checks health, React content, anonymous creation through Nginx, invalid input without insertion, protected routes, Actuator blocking, and persisted link data after container recreation. It briefly interrupts services and leaves its test link in the database.

With Maven and Java 21 available on the host:

```bash
cd backend
mvn verify
mvn -Pintegration verify
```

The `integration` profile adds PostgreSQL Testcontainers tests. Docker 25+ must be running and accessible; these tests deliberately do not run during the image build. Test configuration uses Docker API 1.44 for compatibility with Docker 29; override with `-Dapi.version` if needed. A Docker-hosted runner is also available:

```bash
bash scripts/test-integration.sh
```

With Node available, run `npm ci` then `npm run build` in `frontend/`. There is no frontend test or development script; browser checks exercise the form through Compose.

## Planned APIs

Creation is implemented. Listing and deletion of owned links, statistics, registration/login, and public `302` redirects remain planned. Consult `PRODUCT.md` and `USER_STORIES.md` for decisions and acceptance criteria.
