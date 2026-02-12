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
