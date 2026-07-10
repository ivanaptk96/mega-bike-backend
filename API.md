# Mega Bike API

OpenAPI contract:

```text
openapi.yaml
```

Postman collection:

```text
MegaBike.postman_collection.json
```

Local database setup:

```text
compose.yaml
src/main/resources/application-dev.yaml
```

Local development PostgreSQL listens on host port `5433` to avoid conflicts with a local Mac PostgreSQL installation on `5432`.

## Current Status

The API is in the first identity/login slice.

Implemented:

```text
POST /api/auth/login
```

Planned but not implemented yet:

```text
POST /api/auth/refresh
POST /api/auth/logout
GET  /api/auth/me
```

## Authentication

Mega Bike uses bearer tokens.

After login, the backend returns an access token. Future authenticated requests should send it like this:

```http
Authorization: Bearer <accessToken>
```

Token validation for incoming requests is not implemented yet. The login endpoint can create tokens, but protected endpoints cannot use those tokens until `JwtAuthenticationFilter` is added.

## POST /api/auth/login

Authenticates a user with email and password.

Route:

```http
POST /api/auth/login
Content-Type: application/json
```

Request body:

```json
{
  "email": "admin@megabike.local",
  "password": "password"
}
```

Successful response:

```http
200 OK
Content-Type: application/json
```

Response body:

```json
{
  "accessToken": "<jwt>",
  "tokenType": "Bearer",
  "expiresInSeconds": 900,
  "expiresAt": "2026-07-10T10:00:00Z",
  "email": "admin@megabike.local",
  "authorities": [
    "PRODUCT_READ",
    "PRODUCT_WRITE",
    "ROLE_ADMIN",
    "USER_MANAGE"
  ]
}
```

The exact `authorities` list depends on the user's roles and permissions.

Validation errors:

```text
email must be present and valid
password must be present
```

Authentication failure:

```http
401 Unauthorized
Content-Type: application/json
```

```json
{
  "code": "INVALID_CREDENTIALS",
  "message": "Invalid email or password."
}
```

## Dev Users

When the Spring `dev` profile is active, Liquibase loads mock users.

```text
admin@megabike.local
manager@megabike.local
employee@megabike.local
```

Password for all three:

```text
password
```

## Current Route Security

Configured in:

```text
src/main/java/com/megabike/identity/infrastructure/SecurityConfig.java
```

Rules:

```text
/api/auth/**        public for now
/api/admin/**       ADMIN
/api/internal/**    ADMIN, MANAGER, EMPLOYEE
any other request   authenticated
```

`/api/auth/**` is intentionally broad during the auth bootstrap phase. It should become more precise after refresh, logout, and current-user endpoints are implemented.
