# Frontend Integration Notes

`openapi.yaml` is the formal API contract and should be the main source for endpoint paths, request bodies, response bodies, authentication, and status codes.

This file is a shorter onboarding summary for frontend work.

## Auth Flow

Login:

```http
POST /api/auth/login
```

The successful login response includes:

```text
accessToken
refreshToken
tokenType
expiresInSeconds
expiresAt
refreshTokenExpiresAt
email
authorities
```

Protected API calls must send the access token:

```http
Authorization: Bearer <accessToken>
```

Refresh an access token:

```http
POST /api/auth/refresh
```

Logout:

```http
POST /api/auth/logout
```

Logout requires a valid bearer access token and the refresh token in the request body.

## Catalog Endpoints

Categories:

```text
POST /api/internal/categories
GET  /api/internal/categories
GET  /api/internal/categories/{id}
PUT  /api/internal/categories/{id}
```

Products:

```text
POST /api/internal/products
GET  /api/internal/products
GET  /api/internal/products/{id}
PUT  /api/internal/products/{id}
```

## Product Listing Filters

`GET /api/internal/products` supports optional filters:

```text
query
categoryId
active
```

Example:

```http
GET /api/internal/products?query=helmet&active=true
```

`query` currently searches:

```text
name
productCode
barcode
brandName
externalId
```

## Product Notes

Current product fields include:

```text
id
productCode
externalId
barcode
name
description
brandName
categoryId
categoryName
unit
purchasePrice
retailPrice
vatRate
retailPriceIncludesVat
active
createdAt
updatedAt
```

Important assumptions:

- There is no pagination yet.
- There is no delete endpoint yet.
- Product images are not implemented yet.
- Inventory/stock is not implemented yet.
- Supplier-specific product codes are not modeled separately yet.
- Prices are sent as decimal numbers, not strings.
- `retailPriceIncludesVat` should usually be `true`.
- The only supported product unit is currently `PIECE`.

## Permissions

Read access:

```text
PRODUCT_READ
```

Create/update access:

```text
PRODUCT_WRITE
```

Current dev behavior:

- Admin can read and write.
- Manager can read and write.
- Employee can read but cannot write.

## Dev Users

When the Spring `dev` profile is active, Liquibase loads these users:

```text
admin@megabike.local / password
manager@megabike.local / password
employee@megabike.local / password
```
