# Company Service

## Security

Public (`permitAll`):
- `/swagger-ui/**`
- `/swagger-ui.html`
- `/v3/api-docs/**`
- `/actuator/health`
- `/actuator/info`

All other endpoints require authentication.

## Dev Header Context (`dev` profile only)

When the `dev` profile is active, request context can be supplied via headers:
- `X-Subject-Id`: used as actor/subject for audit fields (`createdBy`, `modifiedBy`, `trashedBy`, etc.)
- `X-Company-Id`: optional company context for `/api/v1/location/{locationId}` endpoints if not provided explicitly

Outside `dev`, these headers are ignored for security context derivation and the authenticated principal is used for subject resolution.

## IntelliJ Run Configuration

Example environment variables:

`COMPANY_DB_HOST=localhost;COMPANY_DB_PORT=3306;COMPANY_DB_NAME=company;COMPANY_DB_USER=root;COMPANY_DB_PASSWORD=;COMPANYPORT=8080`

`Include system environment variables` can stay enabled, because the variables are service-specific.

## Docker Run

A `Dockerfile` and `docker-compose.yml` are provided for local containerized execution.  Build the service image manually (skipping tests if you like) via:

```
docker build -t company-service .
```

The application exposes `server.port=8110` and `management.server.port=8181` (context path `/api/v1`).
The `Dockerfile` registers a health probe against `http://localhost:8181/actuator/health`.

To run the stack with MariaDB (listening on host port `3307`) just start Compose:

```
docker compose up --build
```

`docker compose down` will stop the services and retain the MariaDB data in the named volume `db_data`.
