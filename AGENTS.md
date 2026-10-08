# Repository Guidelines

## Project Structure & Module Organization

US01 provides anonymous link creation and an English React form. Registration, JWT login and a placeholder dashboard are also implemented; redirects, logout and link management remain planned. Consult `README.md` for current behavior, `PRODUCT.md` for product decisions, and `USER_STORIES.md` for planned work.

- `backend/`: Java 21/Spring Boot application. Organize code under `src/main/java/com/example/shortener/` by feature (`link`, `auth`, `user`, `analytics`, `security`, `ratelimit`); reserve `shared` for common code. Place tests in `src/test/java/` and Flyway migrations in `src/main/resources/db/migration/`.
- `frontend/`: React/TypeScript application. `src/app/` contains the entry point and styles, `src/features/` holds feature code, `src/shared/` holds reusable code, and `src/assets/fonts/` holds self-hosted fonts and their licenses.
- `compose.yml` wires Nginx, the backend, PostgreSQL, and Redis; `scripts/verify.sh` checks infrastructure.

## Build, Test, and Development Commands

Run from the repository root with Docker running:

- `docker compose config --quiet`: validate Compose configuration.
- `docker compose build`: build both images; the backend runs Maven `verify`, and the frontend runs TypeScript checks and Vite.
- `docker compose up --build -d --wait --wait-timeout 180`: start healthy services at `http://localhost:8080`.
- `bash scripts/verify.sh`: verify health, proxy routing, and database persistence. Requires Bash and briefly interrupts services by recreating containers.
- `docker compose down`: stop services while preserving database data.

With host toolchains, run `mvn verify` in `backend/` or `npm ci` then `npm run build` in `frontend/`. Run `npm test` for frontend tests; no frontend development script exists.

## Coding Style & Naming Conventions

Match existing style: four-space Java indentation; two-space TypeScript, CSS, YAML, and JSON indentation. TypeScript uses single quotes, semicolons, and strict checking. Use PascalCase for Java classes and React components, camelCase for methods and variables, and lowercase Java packages. Keep controllers, services, repositories, and DTOs within their feature. No formatter or linter is configured. Name new Flyway migrations `V1__description.sql`, incrementing versions thereafter.

## Testing Guidelines

JUnit 5, Mockito, and Testcontainers cover link creation; no coverage threshold exists. Mirror backend packages in tests, naming unit/API files `*Test.java` and PostgreSQL integration files `*IT.java`. Run integration tests with `mvn -Pintegration verify` or `bash scripts/test-integration.sh`; Testcontainers requires Docker. Add behavioral tests for new features; infrastructure verification complements application tests.

## Commit & Pull Request Guidelines

Commits use imperative descriptions, such as `Fix Readme`; no prefix convention exists. Keep commits focused. PRs should describe changes, reference issues or user stories, report verification commands and results, and include screenshots for UI changes.

## Security & Configuration

Use `.env.example` as the configuration reference; keep credentials out of Git. Local defaults are development-only. Use Flyway for schema changes: Hibernate table generation is disabled. Avoid `docker compose down --volumes` when database data must be retained.

## Spring Boot Architecture and Code Quality

- Organize code by feature. Keep controllers focused on HTTP concerns, services on application behavior, and repositories on persistence.
- Use constructor injection and explicit dependencies. Avoid field injection and unnecessary interfaces or abstractions.
- Use dedicated request and response DTOs; do not expose JPA entities through APIs.
- Validate requests at the API boundary and enforce business invariants in the application layer. Use database constraints for guarantees under concurrency.
- Define transaction boundaries around application operations. Keep network calls outside database transactions.
- Handle API errors consistently with appropriate HTTP statuses. Never expose stack traces, credentials, or internal database details.
- Use typed configuration properties for application settings and validate required configuration at startup.
- Keep schema changes in Flyway migrations. Choose entity relationships and fetch strategies deliberately; avoid accidental eager loading and N+1 queries.
- Test observable behavior with focused unit tests, API tests, and PostgreSQL integration tests where persistence semantics matter.

## React Architecture and Code Quality

- Use function components and follow the Rules of Hooks. Keep rendering pure and never mutate props or state.
- Organize UI and behavior by feature. Extract reusable components and hooks when reuse or complexity justifies them.
- Keep state minimal and close to its consumers. Derive values during rendering instead of duplicating them in state.
- Use effects to synchronize with external systems, not for derived values or user actions that belong in event handlers.
- Keep HTTP requests and transport types separate from presentation components. Handle errors and stale or cancelled requests explicitly.
- Preserve strict TypeScript checking. Avoid `any`, unsafe assertions, and unnecessary non-null assertions.
- Use semantic HTML, associated form labels, keyboard-accessible controls, and accessible feedback.
- Handle loading, success, validation, and service-error states explicitly. Prevent duplicate submissions and preserve user input after failures.
- Add dependencies, global state, and memoization only when a concrete requirement justifies them.
- Test user-visible behavior rather than component implementation details.
