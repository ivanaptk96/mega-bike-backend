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

The API currently includes the identity/login slice and the first catalog category slice.

Implemented:

```text
POST /api/auth/login
POST /api/auth/refresh
POST /api/auth/logout
GET  /api/auth/me
POST /api/internal/categories
GET  /api/internal/categories
GET  /api/internal/categories/{id}
PUT  /api/internal/categories/{id}
POST /api/internal/products
GET  /api/internal/products
GET  /api/internal/products/{id}
PUT  /api/internal/products/{id}
```

## Authentication

Mega Bike uses bearer tokens.

After login, the backend returns an access token. Future authenticated requests should send it like this:

```http
Authorization: Bearer <accessToken>
```

Token validation for incoming requests is implemented through `JwtAuthenticationFilter`.

The backend also returns an opaque refresh token. Store it separately from the access token. It is used to request a new access token when the short-lived access token expires.

## Error Response

API errors use a shared response shape:

```json
{
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed.",
  "details": {
    "email": "must be a well-formed email address"
  },
  "timestamp": "2026-07-13T08:45:00Z"
}
```

Common codes:

```text
VALIDATION_FAILED
MALFORMED_JSON
INVALID_CREDENTIALS
AUTHENTICATION_REQUIRED
ACCESS_DENIED
```

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
  "refreshToken": "<opaque-refresh-token>",
  "tokenType": "Bearer",
  "expiresInSeconds": 900,
  "expiresAt": "2026-07-10T10:00:00Z",
  "refreshTokenExpiresAt": "2026-07-17T10:00:00Z",
  "email": "admin@megabike.local",
  "authorities": [
    "PRODUCT_READ",
    "PRODUCT_WRITE",
    "ROLE_ADMIN",
    "USER_MANAGE"
  ]
}
```

## POST /api/auth/refresh

Issues a new access token from a valid refresh token.

Route:

```http
POST /api/auth/refresh
Content-Type: application/json
```

Request body:

```json
{
  "refreshToken": "<opaque-refresh-token>"
}
```

Successful response:

```json
{
  "accessToken": "<jwt>",
  "tokenType": "Bearer",
  "expiresInSeconds": 900,
  "expiresAt": "2026-07-10T10:15:00Z"
}
```

Invalid, revoked, or expired refresh token:

```http
401 Unauthorized
```

## POST /api/auth/logout

Revokes a refresh token.

Route:

```http
POST /api/auth/logout
Authorization: Bearer <accessToken>
Content-Type: application/json
```

Request body:

```json
{
  "refreshToken": "<opaque-refresh-token>"
}
```

Successful response:

```json
{
  "revoked": true
}
```

If the token was already missing or unknown:

```json
{
  "revoked": false
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
  "message": "Invalid credentials.",
  "details": {},
  "timestamp": "2026-07-13T08:45:00Z"
}
```

## GET /api/auth/me

Returns the currently authenticated user.

Route:

```http
GET /api/auth/me
Authorization: Bearer <accessToken>
```

Successful response:

```http
200 OK
Content-Type: application/json
```

Response body:

```json
{
  "email": "admin@megabike.local",
  "authorities": [
    "PRODUCT_READ",
    "PRODUCT_WRITE",
    "ROLE_ADMIN",
    "USER_MANAGE"
  ]
}
```

Missing or invalid token:

```http
401 Unauthorized
Content-Type: application/json
```

```json
{
  "code": "AUTHENTICATION_REQUIRED",
  "message": "Authentication is required.",
  "details": {},
  "timestamp": "2026-07-13T08:45:00Z"
}
```

## Categories

Category endpoints are protected internal endpoints.

Required authorities:

```text
PRODUCT_READ   list/read categories
PRODUCT_WRITE  create/update categories
```

### POST /api/internal/categories

Creates a category.

Route:

```http
POST /api/internal/categories
Authorization: Bearer <accessToken>
Content-Type: application/json
```

Request body:

```json
{
  "name": "Helmets",
  "slug": "helmets",
  "parentId": null,
  "active": true
}
```

`slug` is optional. If omitted, it is generated from `name`.

Successful response:

```json
{
  "id": "2ef0b6d6-1d5b-4f7c-9ef7-6ad60cbf50f2",
  "name": "Helmets",
  "slug": "helmets",
  "parentId": null,
  "parentName": null,
  "active": true,
  "createdAt": "2026-07-20T17:00:00Z",
  "updatedAt": null
}
```

### GET /api/internal/categories

Lists categories ordered by name.

Route:

```http
GET /api/internal/categories
Authorization: Bearer <accessToken>
```

### GET /api/internal/categories/{id}

Returns one category.

Route:

```http
GET /api/internal/categories/{id}
Authorization: Bearer <accessToken>
```

Missing category:

```text
404 CATEGORY_NOT_FOUND
```

### PUT /api/internal/categories/{id}

Updates a category.

Route:

```http
PUT /api/internal/categories/{id}
Authorization: Bearer <accessToken>
Content-Type: application/json
```

Request body:

```json
{
  "name": "Bike Helmets",
  "slug": "bike-helmets",
  "parentId": null,
  "active": true
}
```

Duplicate slug:

```text
409 CATEGORY_SLUG_EXISTS
```

## Products

Product endpoints are protected internal endpoints.

Required authorities:

```text
PRODUCT_READ   list/read products
PRODUCT_WRITE  create/update products
```

### POST /api/internal/products

Creates a product.

Route:

```http
POST /api/internal/products
Authorization: Bearer <accessToken>
Content-Type: application/json
```

Request body:

```json
{
  "productCode": "MB-P-1001",
  "externalId": "ALG-P-1001",
  "barcode": "8600000001001",
  "name": "Trail Helmet M",
  "description": "Mountain bike helmet, medium.",
  "brandName": "Mega Bike",
  "categoryId": "40000000-0000-0000-0000-000000000001",
  "unit": "PIECE",
  "purchasePrice": 38.00,
  "retailPrice": 59.99,
  "vatRate": 20.00,
  "retailPriceIncludesVat": true,
  "active": true
}
```

Successful response:

```json
{
  "id": "5fc85194-7bd5-47ab-9251-4ef31fbcb1ad",
  "productCode": "MB-P-1001",
  "externalId": "ALG-P-1001",
  "barcode": "8600000001001",
  "name": "Trail Helmet M",
  "description": "Mountain bike helmet, medium.",
  "brandName": "Mega Bike",
  "categoryId": "40000000-0000-0000-0000-000000000001",
  "categoryName": "Bikes",
  "unit": "PIECE",
  "purchasePrice": 38.00,
  "retailPrice": 59.99,
  "vatRate": 20.00,
  "retailPriceIncludesVat": true,
  "active": true,
  "createdAt": "2026-07-21T18:00:00Z",
  "updatedAt": null
}
```

Duplicate code or barcode:

```text
409 PRODUCT_CODE_EXISTS
409 PRODUCT_BARCODE_EXISTS
```

Missing category:

```text
400 PRODUCT_CATEGORY_NOT_FOUND
```

### GET /api/internal/products

Lists products ordered by name.

Route:

```http
GET /api/internal/products
Authorization: Bearer <accessToken>
```

Optional query parameters:

```text
query       searches name, productCode, barcode, brandName, externalId
categoryId filters by category UUID
active     filters active/inactive products
```

Example:

```http
GET /api/internal/products?query=helmet&active=true
Authorization: Bearer <accessToken>
```

### GET /api/internal/products/{id}

Returns one product.

Route:

```http
GET /api/internal/products/{id}
Authorization: Bearer <accessToken>
```

Missing product:

```text
404 PRODUCT_NOT_FOUND
```

### PUT /api/internal/products/{id}

Updates a product.

Route:

```http
PUT /api/internal/products/{id}
Authorization: Bearer <accessToken>
Content-Type: application/json
```

Request body is the same shape as `POST /api/internal/products`.

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
/api/auth/login     public
/api/auth/refresh   public
/api/auth/me        authenticated
/api/auth/logout    authenticated
/api/admin/**       ADMIN
/api/internal/**    ADMIN, MANAGER, EMPLOYEE
any other request   authenticated
```
