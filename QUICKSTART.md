# ilot project workspace

This starter runs a Spring Boot API, a React/TypeScript dashboard, and MySQL.

## Run with Docker Compose

1. Copy `.env.example` to `.env` and choose local database passwords.
2. From the repository root, run `docker compose up --build`.
3. Open the dashboard at <http://localhost:5173>. The API is at <http://localhost:8080/api/organizations>.

The MySQL data is kept in the `mysql_data` volume. Hibernate uses `ddl-auto=update` for this early development setup; switch to versioned Flyway migrations before production. Do not use the sample passwords outside local development.

## Run services without Docker

- Start MySQL 8.4 and create a database named `ilot` with a user that has access to it.
- Run the backend from `ilot-backend` with `mvn spring-boot:run` (override `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD` if needed).
- Run `npm ci` and `npm run dev` from `ilot-frontend`. Vite proxies `/api` to `http://localhost:8080` for local development. In Docker Compose, Nginx serves the built frontend and proxies `/api` to the `backend` service.

## Initial API

- `GET /api/organizations` — list organizations with space, project, and task counts.
- `GET /api/organizations/{id}` — fetch one organization summary.
- `POST /api/organizations` — create an organization with `{ "name": "Acme" }`.

## Backend layout

The backend is organized under `com.ilot.ilotbackend`: `domain` for JPA entities, `repository` for persistence, `dto` for API contracts, `mapper` for entity/DTO conversion, `service` for business logic, and `controller` for HTTP endpoints. The initial model includes Organization → Space → Project → Task, users, Equipment/Human/Budget resource subtypes, scoped RBAC roles and permissions, role assignments, project resource allocations, task assignees, and task dependencies. Methodology modules and full workflow rules can be added as subsequent layers, following the design docs.
