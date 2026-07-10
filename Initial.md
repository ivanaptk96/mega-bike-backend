# Mega Bike – Backend Architecture Notes

# Goals
### Phase 1 — Foundation
- project configuration
- PostgreSQL
- Liquibase XML
- global exception handling
- auditing
- Spring Security
- users, roles and permissions
- login
### Phase 2 — Product catalog
- categories
- brands
- products
- SKUs
- barcodes
- retail and purchase prices
- product search
### Phase 3 — Suppliers and purchasing
- suppliers
- supplier product codes
- purchase orders
- purchase-order items
- order statuses
### Phase 4 — Inventory
- warehouse
- goods receipt
- stock items
- stock movements
- manual stock corrections
### Phase 5 — Sales
- customers
- sales orders
- payments
- stock reservations
- order completion and stock reduction
### Phase 6 — Documents and integrations
- document storage
- supplier invoice upload
- email/order import
- eFaktura
- reports
### Phase 7 — Public webshop
- public catalog
- webshop customer accounts
- cart
- checkout
- online payment
- delivery
- customer order history

Mega Bike is initially an internal business application for a bike shop,
with room to later become a public webshop and potentially support wholesale/distribution.

The system should eventually help with:

products and bike parts
suppliers such as Capriolo
supplier purchase orders
incoming stock
inventory and stock movements
retail sales/orders
customers
employees and permissions
documents and invoices
future eFaktura integration
future public webshop
possible future B2B customers if the company starts distributing goods

The email example from Capriolo showed that supplier orders contain:

order number
creation date
buyer/company information
delivery information
ordered items
quantities and prices

That suggests supplier ordering should be a proper domain in the system rather than just storing an email or PDF.

## 2. Recommended architecture

Start with a modular monolith.

Do not start with microservices.

A modular monolith gives you:

one Spring Boot application
one database
simple deployment
clear domain boundaries
the possibility of extracting modules into services later

Use DDD ideas to organize the code, but avoid making the first version overly theoretical.

mega-bike-backend
│
├── identity
├── catalog
├── inventory
├── purchasing
├── sales
├── customer
├── organization
├── documents
└── shared

Each module should own its own business rules.

For example:

catalog knows what a product is
inventory knows how much stock exists
purchasing knows what was ordered from suppliers
sales knows what was sold to customers

A product should not contain twenty unrelated responsibilities just because everything uses a product.

## 3. Suggested domain modules
Identity and access

Responsible for:

login
passwords
authentication
authorization
users
roles
permissions

Initial roles:

### ADMIN
### OWNER
### MANAGER
### EMPLOYEE
### WAREHOUSE
### SALES

You probably do not need all of them immediately.

A reasonable first version is:

### ADMIN
### MANAGER
### EMPLOYEE

Example permissions:

Action	Admin	Manager	Employee
Manage users	Yes	No	No
Manage products	Yes	Yes	Limited
Manage suppliers	Yes	Yes	No
Create supplier orders	Yes	Yes	Maybe
Receive stock	Yes	Yes	Yes
View purchase prices	Yes	Yes	Maybe no
Create sales	Yes	Yes	Yes
View reports	Yes	Yes	Limited

Long term, permissions will likely be better than hard-coded roles.

For example:

PRODUCT_READ
PRODUCT_WRITE
PURCHASE_ORDER_CREATE
PURCHASE_ORDER_APPROVE
STOCK_ADJUST
USER_MANAGE
REPORT_VIEW

A role can then contain several permissions.

Future users can include:

internal employees
webshop customers
B2B company users

Do not put all of them in one User entity with dozens of nullable fields.

Use one authentication identity, with separate business profiles:

### UserAccount
### Employee
### Customer
### BusinessCustomer
### Organization

Responsible for your own company data:

legal company name
### PIB
registration number
addresses
contact information
bank accounts
store locations
warehouses

Even if there is currently only one shop, model locations separately enough that another store or warehouse can be added later.

### Organization
### Location
### Warehouse
### Catalog

Responsible for describing products.

Possible entities:

### Product
### ProductVariant
### Category
### Brand
### Manufacturer
### ProductImage
### ProductBarcode

A bike may have variants such as:

size
color
model year

A simple spare part may not require variants.

Possible product fields:

id
sku
name
description
brand
category
barcode
unit
purchasePrice
retailPrice
vatRate
active

Be careful with putting stockQuantity directly on Product. Stock belongs to inventory and can differ by warehouse.

### Suppliers

This could initially live inside purchasing, or later become its own module.

### Supplier
### SupplierContact
### SupplierAddress
### SupplierProduct

SupplierProduct can map your internal product to the supplier’s:

supplier SKU
supplier product name
supplier price
minimum order quantity

This becomes useful when importing price lists or processing supplier orders.

### Purchasing

Responsible for everything you order from suppliers.

### PurchaseOrder
### PurchaseOrderItem
### GoodsReceipt
### GoodsReceiptItem
### SupplierInvoice

Suggested purchase-order lifecycle:

### DRAFT
### SUBMITTED
### CONFIRMED
PARTIALLY_RECEIVED
### RECEIVED
### CANCELLED

Example:

Employee creates a draft order.
Manager submits it to Capriolo.
Supplier confirms the order.
Goods arrive.
Employee creates a goods receipt.
Inventory is increased.
Supplier invoice is attached or imported.

The purchase order should store a price snapshot. Do not depend on the current product price after the order has been placed.

### PurchaseOrderItem
- productId
- supplierSku
- productName
- quantity
- unitPrice
- vatRate

The copied name and price preserve the historical order even if the product changes later.

### Inventory

Responsible for physical stock.

Core entities:

### Warehouse
### StockItem
### StockMovement
### StockReservation

Rather than only maintaining a quantity, record movements:

PURCHASE_RECEIPT
### SALE
CUSTOMER_RETURN
SUPPLIER_RETURN
MANUAL_ADJUSTMENT
TRANSFER_IN
TRANSFER_OUT

A StockMovement gives you an audit history.

### StockMovement
- productId
- warehouseId
- quantityChange
- type
- referenceType
- referenceId
- createdAt
- createdBy

Current stock can be calculated or maintained in StockItem.

### StockItem
- productId
- warehouseId
- availableQuantity
- reservedQuantity

When a goods receipt is confirmed, it should create inventory movements. Purchasing should not directly modify inventory tables wherever it wants.

Conceptually:

### Purchasing confirms receipt
↓
Inventory receives StockReceived command/event
↓
### Inventory creates stock movements

Inside a modular monolith this can initially be an ordinary application-service call.

### Sales

Responsible for customer orders and shop sales.

### SalesOrder
### SalesOrderItem
### Payment
### Shipment
### Return

Possible lifecycle:

### DRAFT
### CONFIRMED
### PAID
### READY
### SHIPPED
### COMPLETED
### CANCELLED

Initially this might represent sales entered by an employee.

Later, the webshop can create the same type of sales order.

That is an important design choice:

### Employee order
### Webshop order
B2B order
↓
### SalesOrder

They may have different creation flows, but should use the same core sales rules.

### Customers
### Customer
### IndividualCustomer
### BusinessCustomer
### CustomerAddress

You do not need inheritance immediately. A simpler model could be:

### Customer
- type: INDIVIDUAL | BUSINESS
- firstName
- lastName
- companyName
- pib
- email
- phone

Later, when the public webshop is built, a customer may have a UserAccount, but guest checkout could still create a customer without login credentials.

### Documents

Responsible for attachments and generated documents:

supplier order confirmation
invoice
delivery note
product image
warranty document
### PDF
email attachment
### Document
- id
- filename
- contentType
- storageKey
- documentType
- referenceType
- referenceId

Store metadata in the database and files in:

local filesystem during development
S3-compatible storage later

Do not store large files directly in PostgreSQL unless there is a strong reason.

eFaktura integration

Treat eFaktura as an external integration, not as part of the sales domain itself.

Possible future structure:

integration
└── efaktura
├── EFakturaClient
├── EFakturaMapper
├── EFakturaConfiguration
└── EFakturaSyncService

Your internal invoice model should not become a copy of the eFaktura API model.

Use a mapping layer:

### Internal Invoice
↓
### EFakturaMapper
↓
### EFaktura request

That protects the rest of the application if the external API changes.

## 4. Should you have an admin API and user API?

Yes at the API level, but not as separate applications.

Use separate routes:

/api/admin/**
/api/internal/**
/api/store/**
/api/auth/**

Suggested meaning:

/api/auth
Login, refresh token, logout

/api/internal
### Employee operations

/api/admin
User management, configuration, sensitive reports

/api/store
### Future public webshop endpoints

For example:

GET    /api/internal/products
POST   /api/internal/products
POST   /api/internal/purchase-orders
POST   /api/internal/goods-receipts

GET    /api/admin/users
POST   /api/admin/users
PATCH  /api/admin/users/{id}/roles

GET    /api/store/products
POST   /api/store/orders

Do not duplicate business logic between admin and store controllers.

Both should call application services:

AdminProductController ─┐
├── ProductApplicationService
StoreProductController ─┘

The public controller may return fewer fields and allow fewer operations.

## 5. Internal module structure

Use package-by-feature rather than package-by-technical-layer for the whole application.

Recommended:

com.megabike
├── identity
│   ├── api
│   ├── application
│   ├── domain
│   └── infrastructure
├── catalog
│   ├── api
│   ├── application
│   ├── domain
│   └── infrastructure
├── inventory
├── purchasing
├── sales
└── shared

Within catalog:

catalog
├── api
│   ├── ProductController
│   ├── CreateProductRequest
│   └── ProductResponse
├── application
│   ├── ProductService
│   ├── CreateProductCommand
│   └── ProductMapper
├── domain
│   ├── Product
│   ├── ProductId
│   └── ProductRepository
└── infrastructure
└── persistence
├── ProductJpaEntity
├── SpringDataProductRepository
└── JpaProductRepositoryAdapter

You do not need maximum DDD ceremony immediately.

For the first implementation, using the domain object as a JPA entity is acceptable:

catalog
├── Product
├── ProductRepository
├── ProductService
├── ProductController
└── dto

The important part is that modules remain separated.

## 6. Database approach

Use PostgreSQL and Liquibase XML, since you already chose XML changelogs.

Suggested changelog layout:

db/changelog
├── db.changelog-master.xml
├── identity
│   ├── 001-create-users.xml
│   └── 002-create-roles.xml
├── catalog
│   ├── 001-create-products.xml
│   └── 002-create-categories.xml
├── purchasing
├── inventory
└── sales

Master file:

<databaseChangeLog>
    <include file="db/changelog/identity/001-create-users.xml"/>
    <include file="db/changelog/identity/002-create-roles.xml"/>
    <include file="db/changelog/catalog/001-create-products.xml"/>
</databaseChangeLog>

Use UUIDs unless there is a specific reason to prefer numeric IDs.

Every important table should normally contain:

id
created_at
updated_at
created_by
updated_by
version

version supports optimistic locking with @Version.

## 7. Security approach

Start with Spring Security and JWT-based authentication.

Initial endpoints:

POST /api/auth/login
POST /api/auth/refresh
POST /api/auth/logout
GET  /api/auth/me

For the internal application:

administrator creates employee accounts
employee logs in using email/username and password
password is stored with BCrypt
API returns access and refresh tokens
endpoint access is checked through roles or permissions

Example:

@PreAuthorize("hasAuthority('PRODUCT_WRITE')")
@PostMapping("/api/internal/products")
public ProductResponse createProduct(...) {
}

Security tables might be:

user_account
role
permission
user_role
role_permission
refresh_token

Do not build public customer registration yet, but leave the model open for it.

## 8. The first vertical slice

Do not implement every module simultaneously.

Build this first:

### Login
+
### Product management

A small first release could support:

admin user exists
admin can log in
admin can create a category
admin can create a product
employee can list and search products
authorization is enforced
Liquibase creates the necessary tables
integration tests prove the flow

Endpoints:

POST /api/auth/login

POST /api/internal/categories
GET  /api/internal/categories

POST /api/internal/products
GET  /api/internal/products
GET  /api/internal/products/{id}
PUT  /api/internal/products/{id}

After that, implement:

Supplier → Purchase order → Goods receipt → Stock

That will be your first meaningful business workflow.

