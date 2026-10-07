# URL Shortener

An application skeleton for shortening URLs, built with a **Java/Spring Boot backend**, a **React/TypeScript frontend**, **PostgreSQL**, and **Redis**. All builds and services run through **Docker Compose**.

## Project status

The project provides the initial structure and configuration for implementing features incrementally.

| Available | To be implemented |
|---|---|
| Spring Boot entry point | Link creation and redirects |
| Static React page | Dashboard and link management |
| PostgreSQL and Redis configuration | Application caching and rate limiting |
| Flyway and Spring Data JPA configuration | Entities, repositories, and domain migrations |
| Spring Security with default behavior | Registration, login, and JWT authentication |
| OpenAPI, Swagger UI, and health checks | Application endpoint documentation |
| Multi-stage Dockerfiles and Docker Compose | Analytics, custom aliases, and expiration |
| Infrastructure verification script | Feature unit and integration tests |

**No application endpoints have been implemented yet.** The only Java class is `ShortenerApplication`. Feature directories contain `.gitkeep` files rather than empty placeholder classes.

## Technology stack

| Area | Configured technologies |
|---|---|
| Backend | Java 21, Spring Boot 3.5.7, Spring Web |
| Persistence | Spring Data JPA, PostgreSQL 17.6, PostgreSQL JDBC |
| Migrations | Flyway with PostgreSQL support |
| Cache | Redis 7.4.6, Spring Data Redis |
| Security | Spring Security; JWT authentication is planned |
| Frontend | React 19.2.0, TypeScript 5.9.3, HTML, and CSS |
| Frontend build | Vite 7.3.7, Node.js 22.21.1, and npm |
| Backend build | Maven 3.9.11 |
| Web server and reverse proxy | Nginx 1.28.0 |
| API documentation | OpenAPI and Swagger UI through springdoc-openapi 2.8.13 |
| Backend health | Spring Boot Actuator |
| Test dependencies | JUnit 5, Mockito, Testcontainers |
| Containerization | Docker and Docker Compose |

JUnit, Mockito, and Testcontainers are included as dependencies, but there are no Java tests yet. The repository currently **does not contain GitHub Actions workflows**. Checks can be run manually using the included script.

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
| `backend` | Runs Spring Boot and future APIs | No published ports |
| `postgres` | Stores data in a named volume | No published ports |
| `redis` | Provides a rebuildable cache without persistence | No published ports |

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

| Backend module | Future responsibility |
|---|---|
| `link` | Link creation, management, aliases, expiration, caching, and redirects |
| `auth` | Registration and login |
| `user` | User data and persistence |
| `analytics` | Click events and statistics |
| `security` | JWT verification and authorization |
| `ratelimit` | Request limits using Redis |
| `shared` | Common configuration and error handling |

Controllers, services, repositories, entities, and DTOs will be added inside each module when their features are implemented. The frontend follows a similar separation in `features`. The `app` directory contains the entry point, while `shared` is reserved for reusable elements.

## Getting started

Docker Engine with Docker Compose, or Docker Desktop, must be **running**. The first build requires an internet connection to download images and dependencies.

From the repository root:

```bash
docker compose up --build
```

Open [http://localhost:8080](http://localhost:8080). To start in the background and wait for health checks:

```bash
docker compose up --build -d --wait --wait-timeout 180
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

Default credentials are for local development only. Compose passes `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, `REDIS_HOST`, and `REDIS_PORT` to the backend. The names `postgres` and `redis` are internal Docker addresses. Future frontend API calls will use relative `/api/v1/*` paths.

Flyway is enabled, but there are no application migrations yet. Hibernate does not generate tables. Flyway may create its own schema history table.

Spring Security retains its default configuration. API requests with `Accept: application/json` receive `401`, and Swagger requires authentication. The default user is `user`, with a temporary password generated in the backend logs on each startup. This behavior is not an application account system.

## Verification

After starting the services:

```bash
bash scripts/verify.sh
```

The script uses Docker to verify:

- Backend health, including PostgreSQL and Redis connectivity.
- The HTML page and the presence of compiled React content.
- Forwarding of API, redirect, Swagger, and OpenAPI requests to the backend, with `401` expected in the skeleton.
- Blocking of external access to Actuator.
- PostgreSQL cluster persistence after container recreation.

Verification recreates all containers, causing a brief interruption. It does not add application tables or data.

The backend build runs Maven `verify`; the frontend build runs TypeScript and Vite. When Testcontainers tests are introduced, the test container will need access to the Docker engine.

To regenerate the lockfile without installing npm on the host:

```bash
docker run --rm --mount "type=bind,source=$PWD/frontend,target=/workspace" -w /workspace node:22.21.1-alpine npm install --package-lock-only --ignore-scripts
```

## Planned API contracts

These endpoints **have not been implemented yet**:

| Method | Path | Future behavior |
|---|---|---|
| `POST` | `/api/v1/links` | Create a short link |
| `GET` | `/api/v1/links` | List the user's links |
| `DELETE` | `/api/v1/links/{shortCode}` | Delete a link |
| `GET` | `/api/v1/links/{shortCode}/stats` | Retrieve link statistics |
| `POST` | `/api/v1/auth/register` | Register an account |
| `POST` | `/api/v1/auth/login` | Log in |
| `GET` | `/r/{shortCode}` | Redirect to the original URL |

The `/r/` prefix separates short links from SPA pages. Nginx already reserves these paths: API and redirect requests do not receive the frontend HTML fallback.

## Roadmap

1. **Core links:** creation, unique Base62 codes, persistence, and `302` redirects.
2. **Backend foundations:** domain migrations, validation, error handling, tests, and API documentation.
3. **Performance:** Redis caching with TTL and rate limiting.
4. **Accounts:** registration, JWT login, link ownership, and custom aliases.
5. **Analytics:** click events, expiration, and statistics.

Hot reload, CI workflows, public deployment, Kafka, Kubernetes, and advanced monitoring are outside the current skeleton.
