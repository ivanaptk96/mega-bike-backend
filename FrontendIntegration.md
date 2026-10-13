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
GET  /api/internal/inventory/movements
GET  /api/internal/inventory/products/{productId}/movements
POST /api/internal/inventory/adjustments
```

Suppliers:

```text
POST /api/internal/suppliers
GET  /api/internal/suppliers
GET  /api/internal/suppliers/{id}
PUT  /api/internal/suppliers/{id}
POST /api/internal/supplier-products
GET  /api/internal/supplier-products
GET  /api/internal/supplier-products/{id}
PUT  /api/internal/supplier-products/{id}
POST /api/internal/suppliers/{supplierId}/imports
GET  /api/internal/suppliers/{supplierId}/imports
GET  /api/internal/supplier-imports/{importRunId}
GET  /api/internal/supplier-imports/{importRunId}/issues
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
- Supplier-specific product codes are modeled through supplier products.
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

Stock movement history:

```http
GET /api/internal/inventory/movements?productId=50000000-0000-0000-0000-000000000001&movementType=ADJUSTMENT&page=0&size=20
```

Product movement history:

```http
GET /api/internal/inventory/products/50000000-0000-0000-0000-000000000001/movements?page=0&size=20
```

Movement list responses are paged and ordered by newest movement first.

## SEF PDF availability

SEF can return HTTP success with a JSON message indicating that PDF generation
has started. This response is not archived as a PDF. Synchronization retains the
invoice/XML and continues when a PDF is pending; repeat synchronization to fetch
the generated file. Document downloads repair previously archived non-PDF
responses in place, preserving the document ID and updating its hash and time.
Valid archived PDFs do not trigger another SEF request.

The document endpoint returns `409 / SEF_PDF_NOT_READY` while generation is
pending and `502 / SEF_INVALID_PDF` for an unexpected non-PDF response. The UI
shows these messages, supports opening a PDF in a new tab and retains download.
Service invoices use the same document endpoints as merchandise invoices.
Invoice pagination includes the unique ID as a final ordering key.

## Supplier Notes

Current first supplier target:

```text
Planet Bike
```

Dev supplier:

```text
80000000-0000-0000-0000-000000000001
PLANET_BIKE
RSD
```

Planet Bike Lager Lista import:

```http
POST /api/internal/suppliers/80000000-0000-0000-0000-000000000001/imports
Content-Type: multipart/form-data
```

Multipart field:

```text
file
```

Import rules:

```text
CSV can use comma or semicolon delimiter.
Supplier stock is supplier availability, not Mega Bike inventory.
Import does not create inventory movements.
Import does not auto-create internal products.
Imported supplier products can remain unlinked with productId = null.
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
SUPPLIER_MANAGE
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
