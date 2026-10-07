# Repository Guidelines

## Project Structure & Module Organization

This URL-shortener skeleton has no application features yet. Consult `README.md` for current behavior, `PRODUCT.md` for product decisions, and `USER_STORIES.md` for planned work.

- `backend/`: Java 21/Spring Boot application. Organize code under `src/main/java/com/example/shortener/` by feature (`link`, `auth`, `user`, `analytics`, `security`, `ratelimit`); reserve `shared` for common code. Place tests in `src/test/java/` and Flyway migrations in `src/main/resources/db/migration/`.
- `frontend/`: React/TypeScript application. `src/app/` contains the entry point and styles, `src/features/` holds feature code, and `src/shared/` holds reusable code. No asset directory exists yet.
- `compose.yml` wires Nginx, the backend, PostgreSQL, and Redis; `scripts/verify.sh` checks infrastructure.

## Build, Test, and Development Commands

Run from the repository root with Docker running:

- `docker compose config --quiet`: validate Compose configuration.
- `docker compose build`: build both images; the backend runs Maven `verify`, and the frontend runs TypeScript checks and Vite.
- `docker compose up --build -d --wait --wait-timeout 180`: start healthy services at `http://localhost:8080`.
- `bash scripts/verify.sh`: verify health, proxy routing, and database persistence. Requires Bash and briefly interrupts services by recreating containers.
- `docker compose down`: stop services while preserving database data.

With host toolchains, run `mvn verify` in `backend/` or `npm ci` then `npm run build` in `frontend/`. No frontend development/test script exists.

## Coding Style & Naming Conventions

Match existing style: four-space Java indentation; two-space TypeScript, CSS, YAML, and JSON indentation. TypeScript uses single quotes, semicolons, and strict checking. Use PascalCase for Java classes and React components, camelCase for methods and variables, and lowercase Java packages. Keep controllers, services, repositories, and DTOs within their feature. No formatter or linter is configured. Name new Flyway migrations `V1__description.sql`, incrementing versions thereafter.

## Testing Guidelines

JUnit 5, Mockito, and Testcontainers dependencies are available; no feature tests or coverage threshold exist. Mirror backend packages in tests, naming files `*Test.java` for Maven discovery. Testcontainers requires Docker. Add behavioral tests for new features; infrastructure verification does not validate application logic.

## Commit & Pull Request Guidelines

Commits use imperative descriptions, such as `Fix Readme`; no prefix convention exists. Keep commits focused. PRs should describe changes, reference issues or user stories, report verification commands and results, and include screenshots for UI changes.

## Security & Configuration

Use `.env.example` as the configuration reference; keep credentials out of Git. Local defaults are development-only. Use Flyway for schema changes: Hibernate table generation is disabled. Avoid `docker compose down --volumes` when database data must be retained.
