# Workspan local development

Workspan runs a Spring Boot API, a React/TypeScript frontend, and MySQL.

## Run with Docker Compose

1. Copy `.env.example` to `.env` and set local database passwords.
2. From the repository root, run `docker compose up --build`.
3. Open the landing page at <http://localhost:5173>. Create an account to create an organization and its first administrator, or sign in to an existing account.
4. Open Swagger UI at <http://localhost:8080/swagger-ui.html>. The OpenAPI document is at <http://localhost:8080/v3/api-docs>.
5. Open phpMyAdmin at <http://localhost:8081>. MySQL is published on port `3308` by default; change `MYSQL_PORT` in `.env` if that port is already in use.

Employee credential emails require SMTP settings in `.env` (`SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, and `SMTP_FROM`). Copy the names and values from your SMTP provider. Without a configured SMTP host, the send actions report a delivery error.

MySQL creates the `ilot` database on first startup and stores it in the `workspan_mysql_data` volume. Hibernate updates entity tables in this development setup. Do not use sample passwords outside local development.

## Run services without Docker

- Start MySQL 8.4 and create the `ilot` database and an application user.
- Run `mvn spring-boot:run` from `workspan-backend`.
- Run `npm ci` and `npm run dev` from `workspan-frontend`. Vite proxies `/api` to `http://localhost:8080`.

## API workflow

Swagger UI documents the endpoints and accepts the session cookie established by login or registration. API routes for organization data, spaces, projects, and tasks require an authenticated session. Space, project, and task queries are scoped to the authenticated user’s organization.

Create resources in parent-first order:

1. `POST /api/spaces` with `{ "name": "Engineering" }`.
2. `POST /api/projects` with a `spaceId`, `name`, and optional dates and description.
3. `POST /api/tasks` with a `projectId`, `name`, status, and optional dates.

Each of Spaces, Projects, and Tasks supports `GET` collection, `GET /{id}`, `POST`, `PUT /{id}`, and `DELETE /{id}`. Deleting a resource still referenced by child records returns `409 Conflict`.
