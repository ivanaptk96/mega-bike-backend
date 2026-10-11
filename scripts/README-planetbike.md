Planet Bike captures contain only the Bicikli category and its current daily bike
stock export. Private captures live outside both Git repositories, under
`../../megabike/data/suppliers/planet-bike/bicikli/<date>` from this module.

Each capture retains paginated listings, full model/variant JSON, downloaded
full-size gallery images, the original XLSX export, a normalized CSV copy,
source metadata and a summary. Credentials and B2B session tokens are excluded.

`import_planetbike_bikes.py <capture-directory> --output <private-import.sql>`
validates that every captured variant matches exactly one stock row, validates
image signatures, deduplicates images by content and generates one transaction.
Run the generated SQL with `psql -v ON_ERROR_STOP=1 -f <private-import.sql>` against
the intended local database. The application can stay stopped during this import.

The importer writes supplier products, catalog metadata, image bytes, an import
record and supplier price/quantity snapshots. It never creates Mega Bike products
or inventory movements and does not automatically link supplier variants to our
products. Full supplier payloads are retained in catalog metadata; the existing
supplier-product description remains a bounded preview. Daily export prices are
contracted wholesale prices without VAT, as confirmed by the portal's separate
`price_incl_tax` fields. Quantity comes from the export's `KOL NA` column.

The catalog is available under the Planet Bike supplier details page after the
updated backend is started manually. Catalog, raw detail and saved image API
endpoints all require `SUPPLIER_MANAGE`. Images are served from our database and
do not need the supplier's login session.

The schema SQL uses `IF NOT EXISTS`, allowing offline import before the next
application start; Liquibase records the same migration on startup. Deterministic
import/snapshot identifiers avoid duplicate snapshots when rebuilding a capture.
