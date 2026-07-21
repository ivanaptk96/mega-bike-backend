# Mega Bike - Catalog Model

## 1. Goal

The catalog module describes products that Mega Bike can buy, stock, search, and sell.

This module should start small, but it must leave room for:

- internal Mega Bike product IDs
- human-readable product codes
- external IDs from future integrations
- generated barcodes
- supplier-specific product codes
- purchase prices
- retail prices
- VAT handling
- discounts
- product search
- future Allegra POS integration
- future webshop data such as images and descriptions

The first implementation should model catalog data only.

Inventory, purchasing, POS synchronization, and webshop publishing should use the catalog but should not be mixed directly into the first catalog entities.

## 2. Current Business Context

Mega Bike currently sells in the physical store.

Current retail system:

```text
Allegra POS
```

Current understanding:

- Allegra POS is used as the cash register / retail system.
- Product and inventory information are at least partially maintained there.
- The accountant maintains official stock records, including the lager list.
- Some inventory and pricing workflows are still manual.
- There is currently one physical store / warehouse.
- Webshop is planned later, not part of the first catalog implementation.
- Manufacturer images and descriptions may be available later.

This means the catalog should not assume that Mega Bike backend is already the only source of product truth.

For now, the backend should be designed so it can later synchronize with Allegra and suppliers.

## 3. Module Boundary

Recommended package:

```text
src/main/java/com/megabike/catalog
```

Suggested structure:

```text
catalog
├── api
├── application
├── domain
└── infrastructure
```

First implementation:

```text
Category
Product
```

Postpone:

```text
inventory quantities
stock movements
supplier orders
Allegra synchronization
webshop publishing
product images
product variants
price history
discount rules
```

## 4. Identifiers

Products will probably need several identifiers.

### Internal System ID

Technical primary key:

```text
id UUID
```

Used by:

- database relations
- internal APIs
- services
- references from other modules

This should not be shown as the main business identifier to employees.

### Human-Readable Product Code

Mega Bike's own product code:

```text
productCode
```

Example:

```text
MB-BIKE-000123
```

Used by:

- employees
- search
- printed labels
- imports/exports
- support/debugging

This should be unique.

### External ID

External system identifier:

```text
externalId
```

Important distinction:

```text
externalId is not the same as supplier product code.
```

An `externalId` is useful when syncing with systems such as Allegra POS or some future integration.

Because there may be several external systems later, the long-term model may become:

```text
product_external_reference
- id
- product_id
- system_name
- external_id
```

For v1, we can either:

- keep a nullable `externalId` on `product`
- or postpone it until Allegra integration design is clearer

Recommended for v1:

```text
Add nullable external_id to product.
```

Reason:

```text
It gives us a place to store Allegra's product identifier later without building the full integration model now.
```

### Barcode

Mega Bike wants generated barcodes.

For v1, product can have:

```text
barcode
```

Long term, this should probably become:

```text
product_barcode
- id
- product_id
- value
- type
- primary
```

Reason:

```text
Some products may have supplier/manufacturer barcodes, generated internal barcodes, or multiple barcodes for packaging variants.
```

Recommended for v1:

```text
Add nullable unique barcode to product.
```

Later we can extract it into a separate table if needed.

## 5. Category

Start with category before product.

Reason:

```text
Category is smaller and lets us establish catalog module structure, Liquibase layout, authorization, validation, and integration tests before product complexity.
```

Suggested fields:

```text
id UUID
name
slug
parent_id nullable
active
created_at
updated_at
version
```

Hierarchy examples:

```text
Bikes
  MTB
  Road
  Kids

Parts
  Brakes
  Chains
  Tires
```

First category endpoints:

```text
POST /api/internal/categories
GET  /api/internal/categories
GET  /api/internal/categories/{id}
PUT  /api/internal/categories/{id}
```

Authorization:

```text
PRODUCT_READ   can list/read categories
PRODUCT_WRITE  can create/update categories
```

## 6. Product V1

Implemented v1 fields:

```text
id UUID
product_code
external_id nullable
barcode nullable
name
description nullable
brand_name nullable
category_id
unit
purchase_price
retail_price
vat_rate
retail_price_includes_vat
active
created_at
updated_at
version
```

Use `BigDecimal` in Java for money and VAT.

Do not use `double`.

Current product endpoints:

```text
POST /api/internal/products
GET  /api/internal/products
GET  /api/internal/products/{id}
PUT  /api/internal/products/{id}
```

Current list filters:

```text
query       searches name, product code, barcode, brand name, external id
categoryId filters by category UUID
active     filters active/inactive products
```

Current product errors:

```text
400 PRODUCT_CATEGORY_NOT_FOUND
404 PRODUCT_NOT_FOUND
409 PRODUCT_CODE_EXISTS
409 PRODUCT_BARCODE_EXISTS
```

## 7. Pricing and VAT

Mega Bike needs:

- purchase price
- retail price
- VAT
- discounts

For v1, store:

```text
purchase_price
retail_price
vat_rate
retail_price_includes_vat
```

Recommended assumption:

```text
Retail price is VAT-inclusive by default.
```

Reason:

```text
Physical-store customer prices are usually presented VAT-inclusive.
```

Purchase price may be supplier-dependent and invoice-dependent. For v1, store one purchase price snapshot on the product, but later purchasing should store its own historical price snapshots.

Do not build complex discounts yet.

For v1, product-level discount can be postponed.

Later options:

```text
manual line discount during sale
product discount
category discount
campaign discount
customer/B2B discount
```

Discounts probably belong closer to `sales`, not core catalog, unless they are purely display/catalog promotions.

## 8. Supplier Codes

Products need to be searchable by supplier code.

Do not put a single supplier code directly on `product`.

Reason:

```text
The same product may be available from multiple suppliers, each with its own code, name, price, and minimum order quantity.
```

Long-term table:

```text
supplier_product
- id
- supplier_id
- product_id
- supplier_code
- supplier_product_name
- supplier_price
- minimum_order_quantity
```

For first product CRUD, postpone supplier tables unless product search by supplier code is absolutely needed immediately.

Recommended:

```text
Document supplier-code search as planned.
Implement it when the purchasing/supplier module starts.
```

## 9. Search Requirements

Products should eventually be searchable by:

```text
name
product code
barcode
brand
category
supplier code
```

V1 search should support:

```text
name
product_code
barcode
brand_name
category_id
active
```

Supplier-code search should come later with supplier product mapping.

First product list endpoint:

```text
GET /api/internal/products?query=&categoryId=&active=
```

## 10. Inventory Boundary

Current physical setup:

```text
one store / one warehouse
```

Even so, do not store stock quantity directly on product.

Reason:

```text
Stock belongs to inventory. Product describes what the item is; inventory describes where and how many exist.
```

Later inventory tables:

```text
warehouse
stock_item
stock_movement
```

For now, product endpoints should not return authoritative stock quantities.

## 11. Allegra POS Boundary

Allegra POS is important, but should not shape the entire product model too early.

Recommended approach:

```text
Build clean internal catalog first.
Add Allegra integration as an adapter later.
Map Allegra IDs/codes into external references.
Do not copy Allegra's API model directly into catalog entities.
```

Future possible package:

```text
integration/allegra
├── AllegraClient
├── AllegraProductMapper
├── AllegraSyncService
└── AllegraConfiguration
```

Open questions for Allegra:

- Does Allegra expose a product API?
- Does Allegra own stock quantities today?
- Does Allegra generate or require barcodes?
- Does Allegra support external product codes?
- How are price updates synchronized?
- How are sales exported/imported?
- How does restocking work through Allegra?

Do not block catalog v1 on these answers.

## 12. Recommended Implementation Order

### Step 1 - Category Foundation

Implement:

```text
category table
Category entity
CategoryRepository
CategoryService
CategoryController
category DTOs
category integration tests
```

Current status:

```text
Implemented.
```

Endpoints:

```text
POST /api/internal/categories
GET  /api/internal/categories
GET  /api/internal/categories/{id}
PUT  /api/internal/categories/{id}
```

Implemented authorization:

```text
PRODUCT_READ   can list/read categories
PRODUCT_WRITE  can create/update categories
```

### Step 2 - Product Foundation

Implement:

```text
product table
Product entity
ProductRepository
ProductService
ProductController
product DTOs
basic product search
product integration tests
```

Endpoints:

```text
POST /api/internal/products
GET  /api/internal/products
GET  /api/internal/products/{id}
PUT  /api/internal/products/{id}
```

### Step 3 - Barcode Generation

Add:

```text
barcode generator
unique barcode validation
label-friendly barcode value
```

### Step 4 - Supplier Mapping

Add when purchasing starts:

```text
supplier
supplier_product
supplier code search
supplier price tracking
```

### Step 5 - Inventory

Add after product CRUD:

```text
warehouse
stock_item
stock_movement
```

## 13. Open Questions

Before implementing full product CRUD, answer these if possible:

1. What should the human-readable product code format be?
2. Should product code be manually entered or generated?
3. Should barcode be generated immediately when product is created?
4. Are retail prices always VAT-inclusive?
5. Can one product have multiple barcodes?
6. Will Allegra remain the source of truth for stock initially?
7. Do employees need category hierarchy immediately?
8. Are brands enough for now, or do we need separate manufacturer vs brand?

For category CRUD, none of these block implementation.

## 14. First Concrete Next Step

Start with category CRUD.

Reason:

```text
It establishes the catalog module, protected business endpoint pattern, validation, docs, and integration-test approach without forcing final product/pricing/integration decisions too early.
```
