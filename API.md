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
GET  /api/internal/inventory
GET  /api/internal/inventory/products/{productId}
GET  /api/internal/inventory/movements
GET  /api/internal/inventory/products/{productId}/movements
POST /api/internal/inventory/adjustments
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

Lists products with pagination. Default sort is `name,asc`.

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
page       zero-based page number, default 0
size       page size from 1 to 100, default 20
sort       one of name, productCode, brandName, retailPrice, createdAt, active
direction  asc or desc, default asc
```

Example:

```http
GET /api/internal/products?query=helmet&active=true&page=0&size=20&sort=name&direction=asc
Authorization: Bearer <accessToken>
```

Successful response:

```json
{
  "content": [
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
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

Invalid pagination or sorting:

```text
400 INVALID_PAGE_REQUEST
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

## Inventory

Inventory endpoints are protected internal endpoints.

Required authorities:

```text
STOCK_RECEIVE  list/read inventory
STOCK_ADJUST   create manual stock adjustments
```

Current assumptions:

```text
one default stock location: STORE
negative stock is not allowed
manual adjustments are applied immediately
every adjustment creates a stock movement audit row
```

### GET /api/internal/inventory

Lists inventory items with pagination.

Route:

```http
GET /api/internal/inventory
Authorization: Bearer <accessToken>
```

Optional query parameters:

```text
query           searches product name, product code, barcode
stockLocationId filters by stock location UUID
page            zero-based page number, default 0
size            page size from 1 to 100, default 20
```

Example:

```http
GET /api/internal/inventory?query=helmet&page=0&size=20
Authorization: Bearer <accessToken>
```

Successful response:

```json
{
  "content": [
    {
      "id": "70000000-0000-0000-0000-000000000001",
      "productId": "50000000-0000-0000-0000-000000000001",
      "productCode": "MB-P-0001",
      "productName": "Trail Helmet M",
      "barcode": "8600000000011",
      "stockLocationId": "60000000-0000-0000-0000-000000000001",
      "stockLocationCode": "STORE",
      "stockLocationName": "Mega Bike Store",
      "quantityOnHand": 5.000,
      "createdAt": "2026-01-01T00:00:00Z",
      "updatedAt": null
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

### GET /api/internal/inventory/products/{productId}

Returns inventory for one product at one stock location.

Route:

```http
GET /api/internal/inventory/products/{productId}?stockLocationId={stockLocationId}
Authorization: Bearer <accessToken>
```

Missing inventory item:

```text
404 INVENTORY_ITEM_NOT_FOUND
```

### POST /api/internal/inventory/adjustments

Creates a manual stock adjustment and records a stock movement.

Route:

```http
POST /api/internal/inventory/adjustments
Authorization: Bearer <accessToken>
Content-Type: application/json
```

Request body:

```json
{
  "productId": "50000000-0000-0000-0000-000000000001",
  "stockLocationId": "60000000-0000-0000-0000-000000000001",
  "quantityDelta": 2.000,
  "reason": "Cycle count correction",
  "referenceText": "COUNT-1"
}
```

Successful response:

```json
{
  "id": "7cfdb8db-83d4-49aa-bbb6-59dc2ef7001c",
  "productId": "50000000-0000-0000-0000-000000000001",
  "productCode": "MB-P-0001",
  "productName": "Trail Helmet M",
  "stockLocationId": "60000000-0000-0000-0000-000000000001",
  "stockLocationCode": "STORE",
  "stockLocationName": "Mega Bike Store",
  "movementType": "ADJUSTMENT",
  "quantityDelta": 2.000,
  "quantityAfter": 7.000,
  "reason": "Cycle count correction",
  "referenceText": "COUNT-1",
  "createdBy": "admin@megabike.local",
  "createdAt": "2026-08-05T08:00:00Z"
}
```

Adjustment errors:

```text
400 INVENTORY_ADJUSTMENT_INVALID
400 INVENTORY_NEGATIVE_STOCK
400 INVENTORY_PRODUCT_NOT_FOUND
400 STOCK_LOCATION_NOT_FOUND
```

### GET /api/internal/inventory/movements

Lists stock movement audit history with pagination.

Route:

```http
GET /api/internal/inventory/movements
Authorization: Bearer <accessToken>
```

Optional query parameters:

```text
productId       filters by product UUID
stockLocationId filters by stock location UUID
movementType    filters by movement type, currently ADJUSTMENT
page            zero-based page number, default 0
size            page size from 1 to 100, default 20
```

Example:

```http
GET /api/internal/inventory/movements?productId=50000000-0000-0000-0000-000000000001&movementType=ADJUSTMENT
Authorization: Bearer <accessToken>
```

Successful response:

```json
{
  "content": [
    {
      "id": "7cfdb8db-83d4-49aa-bbb6-59dc2ef7001c",
      "productId": "50000000-0000-0000-0000-000000000001",
      "productCode": "MB-P-0001",
      "productName": "Trail Helmet M",
      "stockLocationId": "60000000-0000-0000-0000-000000000001",
      "stockLocationCode": "STORE",
      "stockLocationName": "Mega Bike Store",
      "movementType": "ADJUSTMENT",
      "quantityDelta": 2.000,
      "quantityAfter": 7.000,
      "reason": "Cycle count correction",
      "referenceText": "COUNT-1",
      "createdBy": "admin@megabike.local",
      "createdAt": "2026-08-05T08:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

### GET /api/internal/inventory/products/{productId}/movements

Lists stock movement audit history for one product.

Route:

```http
GET /api/internal/inventory/products/{productId}/movements
Authorization: Bearer <accessToken>
```

Optional query parameters:

```text
stockLocationId
movementType
page
size
```

## Dev Users

## Suppliers

Supplier endpoints are protected internal endpoints.

Required authority:

```text
SUPPLIER_MANAGE
```

Current dev seed:

```text
80000000-0000-0000-0000-000000000001
PLANET_BIKE
Planet Bike
RSD
```

### Supplier CRUD

```text
POST /api/internal/suppliers
GET  /api/internal/suppliers
GET  /api/internal/suppliers/{id}
PUT  /api/internal/suppliers/{id}
```

Supplier request:

```json
{
  "code": "PLANET_BIKE",
  "name": "Planet Bike",
  "publicUrl": "https://planetbike.rs/",
  "b2bUrl": "https://planetbike-b2b.rs/dashboard/documents",
  "defaultCurrency": "RSD",
  "active": true
}
```

### Supplier Product Mapping

```text
POST /api/internal/supplier-products
GET  /api/internal/supplier-products?supplierId={supplierId}&query={query}&active=true&page=0&size=20
GET  /api/internal/supplier-products/{id}
PUT  /api/internal/supplier-products/{id}
```

Supplier product request:

```json
{
  "supplierId": "80000000-0000-0000-0000-000000000001",
  "productId": null,
  "supplierCode": "PB-123",
  "supplierBarcode": "8600000000999",
  "supplierProductName": "Planet Bike Product",
  "brandName": "Planet Bike",
  "categoryText": "Bikes",
  "description": "Supplier description.",
  "imageUrl": "https://planetbike.rs/product.jpg",
  "active": true
}
```

`productId` is nullable. This allows imported supplier products to exist before manual review links them to an internal Mega Bike product.

### Planet Bike Lager Lista Import

```http
POST /api/internal/suppliers/{supplierId}/imports
Authorization: Bearer <accessToken>
Content-Type: multipart/form-data
```

Multipart field:

```text
file
```

Current importer:

```text
supports comma or semicolon CSV
uses RSD from the supplier default currency
creates/updates supplier_product rows
stores supplier_price_snapshot rows when price is present
stores supplier_import_issue rows for missing required fields or missing price
does not update Mega Bike inventory
does not auto-create internal products
```

Recognized CSV header aliases:

```text
supplier code: supplier_code, suppliercode, sifra, šifra, code, sku, artikal_sifra
name:          name, naziv, product_name, supplier_product_name, artikal, opis
barcode:       barcode, bar_code, ean, barkod
brand:         brand, brend, proizvodjac, proizvođač
category:      category, kategorija, grupa
price:         purchase_price, price, cena, cijena, nabavna_cena, veleprodajna_cena
availability:  available_quantity, stock, stanje, lager, kolicina, količina
description:   description, opis, detaljan_opis
image URL:     image_url, image, slika, slika_url
```

Import response includes:

```text
status
totalRows
createdSupplierProducts
updatedSupplierProducts
priceSnapshots
issuesCount
sha256Hash
uploadedBy
```

Import review endpoints:

```text
GET /api/internal/suppliers/{supplierId}/imports
GET /api/internal/supplier-imports/{importRunId}
GET /api/internal/supplier-imports/{importRunId}/issues
```

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
