# Supplier Integration Notes

This file captures supplier integration planning for Mega Bike.

Do not store supplier usernames, passwords, cookies, session tokens, or downloaded private price lists in Git. Keep credentials in a password manager or a local ignored file such as `SupplierSecrets.local.md`.

## Current Suppliers

### Capriolo

Public site:

```text
https://www.capriolo.com/
```

B2B portal:

```text
https://b2b.capriolo.croonus.com/prijava
```

Observed public-site signals:

- Has a visible B2B entry point.
- Public categories include bikes, parts, equipment, fitness, hunting, and fishing.
- Public bike categories include city, e-bike, MTB, road, and kids.
- Public site footer says prices are shown in dinars with tax included and delivery is only within Serbia.
- Site appears powered by Croonus Technologies, so the B2B portal may be a Croonus platform.

Integration hypothesis:

```text
Priority: high
Likely product source for complete bicycles and some parts/equipment.
Likely Serbian dinar pricing.
Investigate whether B2B offers downloadable Excel/CSV price lists before browser automation.
```

### Planet Bike

Public site:

```text
https://planetbike.rs/
```

B2B portal:

```text
https://planetbike-b2b.rs/login
```

B2B document area after login:

```text
https://planetbike-b2b.rs/dashboard/documents
```

Observed public-site signals:

- Public site has broad retail categories: cycling, outdoor, rollers, lifestyle, winter sports, fitness, and other.
- Cycling categories include bikes, equipment, parts, clothing/footwear, tools, and maintenance.
- Product listing pages expose product names and public RSD prices.
- Site appears to require JavaScript for the best public-shop experience.

Integration hypothesis:

```text
Priority: first supplier integration target
Important source for bikes, accessories, parts, outdoor, and winter categories.
Likely broad catalog with many categories/brands.
The B2B portal exposes daily CSV documents called Lager Lista.
Start with manual or semi-automated CSV import from this document area.
No supplier API is currently exposed to Mega Bike.
```

### Venera Bike

Public/B2B site:

```text
https://www.venerabike.rs/
```

Observed public-site signals:

- Public site includes product codes labeled as `Sifra`.
- Product listings show product name, brand-like labels such as Visitor/Venssini, size attributes, and public DIN prices.
- Public categories include bicycles and motorcycle/roller/scooter-related categories.

Integration hypothesis:

```text
Priority: medium/high
Useful supplier for bicycles and related products.
Product code extraction may be reliable because public listings expose `Sifra`.
Confirm whether logged-in B2B prices and stock differ from public pages.
```

### Digor Sport / Eurotrade Bike B2B

Public/login sites:

```text
https://www.digorsport.com/user/login
https://b2b.eurotrade.bike/login.html
```

Observed public-site signals:

- Supplier is currently understood as parts-focused.
- The Eurotrade B2B login page is minimal from public HTML.

Integration hypothesis:

```text
Priority: medium
Likely important for parts.
Requires B2B access discovery before implementation.
Do not plan API details until credentials/access and export options are confirmed.
```

## Integration Strategy

Start with supplier data modeling and CSV import before building any scraper or portal client.

Current supplier-wide assumptions:

```text
prices are in RSD
supplier APIs are not currently exposed to Mega Bike
CSV/XLSX imports should be supported before browser automation
images and descriptions may be imported where the supplier allows access
```

First concrete target:

```text
Planet Bike daily Lager Lista CSV import
```

Recommended first backend tables:

```text
supplier
supplier_product
supplier_price_snapshot
supplier_import_run
supplier_import_issue
```

Minimum `supplier` fields:

```text
id
code
name
public_url
b2b_url
default_currency
active
created_at
updated_at
version
```

Minimum `supplier_product` fields:

```text
id
supplier_id
product_id nullable
supplier_code
supplier_barcode nullable
supplier_product_name
brand_name nullable
category_text nullable
active
created_at
updated_at
version
```

Minimum price snapshot fields:

```text
id
supplier_product_id
purchase_price
currency
vat_included
available_quantity nullable
captured_at
source
```

Minimum import-file fields:

```text
id
supplier_id
original_filename
content_type
sha256_hash
uploaded_by
uploaded_at
storage_key nullable
```

Minimum import-run fields:

```text
id
supplier_id
source_type
status
started_at
finished_at
total_rows
created_products
updated_supplier_products
issues_count
```

## Recommended Implementation Order

1. Supplier CRUD.
2. Supplier-product mapping CRUD.
3. Planet Bike Lager Lista CSV upload/import.
4. Import-run and issue tracking.
5. Supplier price snapshot history from CSV imports.
6. Product matching/review workflow.
7. Per-supplier B2B discovery.
8. Supplier-specific automated import adapters.

Reason:

```text
Manual import gives Mega Bike value before fragile B2B automation.
It also reveals the real supplier columns, codes, currencies, VAT rules, and stock data before we hard-code integrations.
```

For Planet Bike, the import should begin with a downloaded daily Lager Lista CSV from:

```text
https://planetbike-b2b.rs/dashboard/documents
```

Do not automate login/download first. Start by uploading the downloaded CSV into the backend so the import model is stable before any portal automation exists.

## Supplier Adapter Plan

Keep supplier-specific code outside the core supplier domain:

```text
supplier/application
supplier/domain
supplier/api
supplier/infrastructure/capriolo
supplier/infrastructure/planetbike
supplier/infrastructure/venera
supplier/infrastructure/digor
```

Each adapter should output the same internal import row shape:

```text
supplier_code
barcode
name
brand
category
purchase_price
currency, default RSD
vat_included
available_quantity
source_url_or_file
description nullable
image_url nullable
```

## Planet Bike Lager Lista Import Plan

V1 flow:

```text
Admin/manager downloads daily Lager Lista CSV from Planet Bike B2B.
Admin/manager uploads CSV into Mega Bike backend.
Backend parses CSV into supplier import rows.
Backend creates/updates supplier_product records.
Backend stores price/availability snapshots.
Backend records import issues for rows that cannot be matched or parsed.
```

Recommended endpoints:

```text
POST /api/internal/suppliers/{supplierId}/imports
GET  /api/internal/suppliers/{supplierId}/imports
GET  /api/internal/supplier-imports/{importRunId}
GET  /api/internal/supplier-imports/{importRunId}/issues
```

Recommended V1 behavior:

```text
Do not auto-create internal products until matching rules are clear.
Create supplier_product rows even when product_id is null.
Use supplier code as the primary stable external key.
Store all prices as RSD.
Store imported stock/availability as supplier availability, not Mega Bike inventory.
Do not update Mega Bike inventory from supplier Lager Lista.
```

Product matching strategy:

```text
1. exact supplier_product.supplier_code match
2. exact barcode match against product.barcode
3. exact normalized product name match only as a review suggestion
4. otherwise leave supplier_product.product_id null for manual review
```

Important boundary:

```text
Supplier Lager Lista stock means supplier availability.
It is not Mega Bike stock and must not write inventory_item or stock_movement rows.
Mega Bike inventory changes only through adjustments, goods receipt, or future POS/integration events.
```

## Credential Handling

Never commit credentials.

Recommended local secret storage:

```text
SupplierSecrets.local.md
```

Recommended production secret storage later:

```text
environment variables
deployment secret manager
encrypted CI/CD variables
```

Potential environment variable names:

```text
CAPRIOLO_B2B_USERNAME
CAPRIOLO_B2B_PASSWORD
PLANET_BIKE_B2B_USERNAME
PLANET_BIKE_B2B_PASSWORD
VENERA_B2B_USERNAME
VENERA_B2B_PASSWORD
DIGOR_B2B_USERNAME
DIGOR_B2B_PASSWORD
EUROTRADE_B2B_USERNAME
EUROTRADE_B2B_PASSWORD
```

## Open Questions

For each supplier:

1. What exact CSV columns are present?
2. Does the CSV include barcode?
3. Does the CSV include supplier stock/availability?
4. Are prices VAT-inclusive or VAT-exclusive?
5. Is available stock visible?
6. Are product barcodes available?
7. Is supplier product code stable over time?
8. Are there minimum order quantities?
9. Are discounts customer-specific?
10. Are images/descriptions present in the CSV or only on product pages?

## Backend Next Step

Implemented supplier foundation and Planet Bike CSV import before portal automation:

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

Implemented behavior:

```text
Planet Bike is seeded in dev with RSD as default currency.
CSV upload supports comma and semicolon delimiters.
CSV rows create or update supplier_product by supplier code.
CSV rows with price create supplier_price_snapshot records.
Rows with missing required fields or missing price create supplier_import_issue records.
Supplier availability does not update Mega Bike inventory.
Internal products are not auto-created.
Supplier products can be linked to internal products manually.
```

After supplier foundation:

```text
Planet Bike Lager Lista CSV import
supplier import run history
supplier import issue review
purchase order foundation
goods receipt into inventory
```
