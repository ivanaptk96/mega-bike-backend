# Capriolo public bicycle catalogue

`capture_capriolo_bikes.py <private-capture-directory> --delay 2 --max-images 5` captures only
the public **Bicikli** root and its subcategories. Delovi, Oprema, Fitness, Lov
and Ribolov are excluded from this run. Catalogue pagination is checked against
the source total and unique product IDs. Requests are sequential with the
specified delay (default 3 seconds). Failed requests retry with longer waits. Access denial stops the
capture. Existing pages/images are reused when resuming the same directory.
The first five gallery images are downloaded per new bicycle; any images already
downloaded before changing the limit are preserved. All original gallery URLs
are retained as `sourceImageUrls`. `--images primary` downloads only the cover
image; omit `--max-images` with `--images all` to fill remaining gallery images
later, reusing the archived pages and existing image files.

The public guest catalogue endpoints are the ones used by Capriolo's frontend.
Guest identifiers are generated in memory; no account login or private B2B
session is needed. Product descriptions, specifications and galleries are read
from public product pages. Full HTML, source JSON, original image bytes and their
hashes are retained outside Git, under
`../../megabike/data/suppliers/capriolo/bicikli/<date>` from this module.
`progress.json` records progress; `issues.json` and `completed.json` establish
whether the entire capture is ready for import.

`import_capriolo_bikes.py <private-capture-directory> --output <private-import.sql>`
validates a completed capture (scope, counts, unique SKU, image hashes/signatures
and field limits), then generates a single PostgreSQL transaction. Import with
`psql -v ON_ERROR_STOP=1 -f <private-import.sql>` against the intended database.
The Capriolo supplier is identified by PIB **101443962**, with `CAPRIOLO` as a
fallback. Missing or ambiguous suppliers abort the transaction. No application
needs to be started to capture or import.

The importer saves supplier products, catalogue metadata and image bytes. Stable
SKU/image IDs make repeat imports safe and existing links to Mega Bike products
are preserved. Public retail prices are kept as `referenceRetailPrice`, currency
and capture timestamp in catalogue metadata. They never become purchase prices,
stock snapshots, our products or inventory movements. The import history source
is `PUBLIC_CATALOG`; a catalogue without stock has no stock-list date.

The Capriolo supplier details screen shows a catalogue with archived images,
description, specifications and clearly labelled informational website prices.
It has no supplier-stock tab. Catalogue/image APIs still require
`SUPPLIER_MANAGE`. The updated backend/frontend are started manually by the user.

Offline parser/completeness checks:
`python3 -m unittest discover -s scripts -p 'test_capriolo_capture.py'`.

## Completed capture, 12 October 2026

The completed bicycle capture imported 402 supplier articles and 1,321 unique
images into the local database. Descriptions were available for 272 articles,
specifications for 277, and images and reference retail prices for all 402.
No supplier stock snapshots, Mega Bike products or inventory movements were
created. Captured data and generated import SQL remain outside Git; committing
these scripts does not transfer the catalogue database or image archive.

Supplier category paths are retained in `supplier_product.category_text` and
catalogue metadata. They are not assignments to the Mega Bike category tree.
Our category is suggested during goods receipt, can be changed in the dropdown,
and is assigned to a new product when the receipt is approved. Existing linked
products keep their category. See `../ReceiptCategorySuggestions.md`.
