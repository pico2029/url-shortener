# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

This is a service for other users, as confirmed during initialization. Visitors can create short links without an account. Registered users manage their own links; anyone can follow a valid short link without logging in.

Specific audience segments and usage frequency remain open decisions.

## Product Purpose

Turn long HTTP/HTTPS URLs into short links that are easy to share. Accounts provide personal link management. Planned aliases, expiration, and click statistics give owners control over how links are shared and an understanding of their use.

The first complete workflow is entering a destination, receiving and copying a full short URL, and opening it to reach the original destination. The planned full product supports separate users managing only their own links.

## Operating Context

Users interact through a browser. The current interface uses Italian copy and the document language is Italian; the intended service languages remain undecided.

Development and verification use Docker Compose, with Nginx serving the React/TypeScript frontend and forwarding API and redirect requests to a Java/Spring Boot backend. PostgreSQL provides persistence and Redis is configured for future caching and rate limiting. Public deployment is outside the current implementation scope, despite the intended audience being other users.

## Capabilities and Constraints

### Current implementation

The repository has configured infrastructure, a React home page and registration form, and a Spring Boot registration API. US03 registration is implemented with a public API, PostgreSQL users migration, password hashing, an Italian `/register` form, and backend/frontend feature tests. Login and JWT session verification are implemented; registration includes password confirmation and automatically logs in. Both forms lead to a dashboard showing only its title. Logout, link operations and personal management remain unimplemented. Planned capabilities must not be presented as working features.

### Confirmed product decisions

- Anonymous visitors may create short links. Accounts are used for personal management.
- Opening short links does not require authentication.
- Ownership, recovery, and any later claiming of anonymously created links remain undecided. Do not assume anonymous links automatically belong to a later account.

### Documented implementation scope

The README and user stories define the following planned behavior:

- Accept valid HTTP/HTTPS destinations, persist links, and generate unique Base62 codes.
- Return a full short URL suitable for copying; use `/r/{shortCode}` for redirects and `/api/v1/*` for application APIs.
- Redirect valid links with HTTP `302`; return `404` for unknown codes and `410` for expired links.
- Provide registration, JWT login, logout, and handling of expired sessions.
- Restrict link listing, deletion, and statistics to the owner. Ask for confirmation before deletion.
- Provide optional custom aliases and future expiration dates for authenticated users.
- Report total and daily clicks, including redirects served from cache.
- Respect deletion and expiration in Redis; fall back to PostgreSQL when the cache is unavailable.
- Provide configurable request limits, returning `429` with `Retry-After` when exceeded.
- Handle applicable loading, empty, success, and error states with understandable feedback.

JWT sessions use per-tab sessionStorage and last one hour. Detailed alias rules, analytics timezone, rate-limit policies, anonymous-link management, and hosting remain open decisions. No pricing, commercial differentiation, or availability guarantees have been established.

## Brand Commitments

The current working name is URL Shortener. No further binding brand or voice commitments were established during initialization.

## Evidence on Hand

- `README.md`: implementation status, architecture, local setup, API plans, and roadmap.
- `USER_STORIES.md`: planned user workflows, acceptance criteria, implementation responsibilities, and open decisions. Its account requirement is resolved by this initialization: anonymous creation is allowed; subsequent anonymous-link management is still open.
- `frontend/src/app/main.tsx`, `frontend/src/app/styles.css`, and `frontend/index.html`: the current placeholder interface and Italian document language.
- `compose.yml`: configured development infrastructure.

There are no customer testimonials, production usage metrics, performance benchmarks, pricing, or deployment evidence in the reviewed project materials. Future interfaces must not fabricate these.

## Product Principles

- Make creating and sharing a short link possible without registration.
- Use accounts to provide clear, private ownership and personal management.
- Make link outcomes and errors understandable, including missing, expired, and deleted links.
- Keep redirect behavior consistent across persistence, cache, expiration, and analytics.
- Represent implemented capabilities honestly and distinguish them from plans.
