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

Inventory:

```text
GET  /api/internal/inventory
GET  /api/internal/inventory/products/{productId}
POST /api/internal/inventory/adjustments
```

## Product Listing Filters

`GET /api/internal/products` supports optional filters:

```text
query
categoryId
active
page
size
sort
direction
```

Example:

```http
GET /api/internal/products?query=helmet&active=true&page=0&size=20&sort=name&direction=asc
```

`query` currently searches:

```text
name
productCode
barcode
brandName
externalId
```

Product list responses are paged:

```text
content
page
size
totalElements
totalPages
first
last
```

Pagination defaults:

```text
page       0
size       20
sort       name
direction  asc
```

Allowed product sort fields:

```text
name
productCode
brandName
retailPrice
createdAt
active
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

- There is no delete endpoint yet.
- Product images are not implemented yet.
- Supplier-specific product codes are not modeled separately yet.
- Prices are sent as decimal numbers, not strings.
- `retailPriceIncludesVat` should usually be `true`.
- The only supported product unit is currently `PIECE`.

## Inventory Notes

Current inventory assumptions:

```text
one default stock location: STORE
negative stock is not allowed
manual adjustments are applied immediately
every adjustment creates a movement audit row
```

Dev stock location:

```text
60000000-0000-0000-0000-000000000001
STORE
Mega Bike Store
```

Inventory listing:

```http
GET /api/internal/inventory?query=helmet&page=0&size=20
```

Inventory list responses are paged:

```text
content
page
size
totalElements
totalPages
first
last
```

Manual adjustment:

```http
POST /api/internal/inventory/adjustments
```

```json
{
  "productId": "50000000-0000-0000-0000-000000000001",
  "stockLocationId": "60000000-0000-0000-0000-000000000001",
  "quantityDelta": 2.000,
  "reason": "Cycle count correction",
  "referenceText": "COUNT-1"
}
```

## Permissions

Read access:

```text
PRODUCT_READ
STOCK_RECEIVE
```

Create/update access:

```text
PRODUCT_WRITE
STOCK_ADJUST
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
