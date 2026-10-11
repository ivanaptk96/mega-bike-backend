#!/usr/bin/env python3
"""Build an atomic, repeatable supplier-only PostgreSQL import from a bike capture.

No login credentials or database credentials are accepted or stored by this script.
Private captures and generated SQL must live outside Git. Run the SQL using psql
with ON_ERROR_STOP=1. Original model JSON and every variant are retained in metadata.
"""
import argparse
import datetime as dt
import hashlib
import json
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import urljoin
import uuid

NAMESPACE = uuid.UUID("4ad8a8f0-e803-481b-b825-1a4858c35277")


def identifier(kind, value):
    return str(uuid.uuid5(NAMESPACE, f"planet-bike:{kind}:{value}"))


def literal(value):
    if value is None:
        return "NULL"
    return "'" + str(value).replace("'", "''") + "'"


class PlainText(HTMLParser):
    def __init__(self):
        super().__init__()
        self.parts = []

    def handle_data(self, data):
        self.parts.append(data)

    def handle_endtag(self, tag):
        if tag in {"p", "li", "h4", "h5", "div", "br"}:
            self.parts.append("\n")


def description(attributes):
    html = next((a.get("value", "") for a in attributes if a["code"] == "description"), "")
    parser = PlainText()
    parser.feed(html)
    return "\n".join(line.strip() for line in "".join(parser.parts).splitlines() if line.strip())


def image_type(data):
    if data.startswith(b"\xff\xd8\xff"):
        return "image/jpeg"
    if data.startswith(b"\x89PNG\r\n\x1a\n"):
        return "image/png"
    if data.startswith(b"RIFF") and data[8:12] == b"WEBP":
        return "image/webp"
    if data.startswith((b"GIF87a", b"GIF89a")):
        return "image/gif"
    raise ValueError("Downloaded asset is not a supported raster image")


def prepare(capture):
    source = json.loads((capture / "source.json").read_text())
    models = []
    listings = []
    for file in sorted(capture.glob("listing-*.json")):
        listing = json.loads(file.read_text())
        listings.extend(listing["products"])
    for listing in listings:
        model = json.loads((capture / "products" / f"{listing['id']}.json").read_text())
        if model.get("sku") != listing["sku"]:
            raise ValueError("Model capture does not match listing")
        models.append((listing, model))
    if len(listings) != len({m["sku"] for m in listings}):
        raise ValueError("Duplicate model SKU")
    stock_rows = json.loads((capture / "stock-rows.json").read_text())[2:]
    stocks = {row[0]: row for row in stock_rows}
    if len(stocks) != len(stock_rows):
        raise ValueError("Duplicate stock SKU")
    variants = {v["sku"] for _, model in models for v in (model.get("children") or [model])}
    if variants != stocks.keys():
        raise ValueError(f"Stock/catalog mismatch: {len(variants - stocks.keys())} unlisted variants, {len(stocks.keys() - variants)} unlisted stock rows")
    manifest = json.loads((capture / "image-manifest.json").read_text())
    images = {}
    for url, asset in manifest.items():
        data = (capture / "images" / asset["file"]).read_bytes()
        sha = hashlib.sha256(data).hexdigest()
        asset["id"] = identifier("image", sha)
        images.setdefault(sha, {"id": asset["id"], "sourceUrl": url, "content": data, "contentType": image_type(data)})
    entries = []
    for listing, model in models:
        for variant in model.get("children") or [model]:
            attributes = variant.get("attributes") or model.get("attributes", [])
            gallery = variant.get("image_gallery") or model.get("image_gallery", [])
            if not gallery and variant.get("image"):
                gallery = [{"url": variant["image"]}]
            urls = [urljoin("https://admin.planetbike-b2b.rs", g.get("large_url") or g["url"]) for g in gallery]
            specifications = [a for a in attributes if a["code"] != "description"]
            brand = next((a["value"] for a in attributes if a["code"] == "brand"), listing.get("brand"))
            row = stocks[variant["sku"]]
            quantity = float(row[3])
            price = float(row[4])
            if quantity < 0 or price < 0:
                raise ValueError("Negative stock or price")
            entries.append({"supplierCode": variant["sku"], "modelCode": model["sku"], "name": variant["name"],
                            "brand": brand, "category": " / ".join(b["label"] for b in model.get("breadcrumbs", []) if b.get("url")),
                            "description": description(attributes), "specifications": specifications,
                            "variations": variant.get("variations", []), "imageIds": list(dict.fromkeys(manifest[u]["id"] for u in urls)),
                            "sourceUrl": "https://planetbike-b2b.rs/" + listing["url_key"] + ".html",
                            "remoteImageUrl": urls[0] if urls else None,
                            "rawData": {"listing": listing, "model": model, "variant": variant, "stockRow": row},
                            "quantity": quantity, "price": price})
    return source, images, entries


def write_import(capture, output):
    source, images, entries = prepare(capture)
    stamp = dt.datetime.now(dt.timezone.utc).isoformat()
    stock_date = dt.date.fromisoformat(source["stockDate"])
    digest = hashlib.sha256((capture / source["stockFilename"]).read_bytes()).hexdigest()
    run_id = identifier("import", digest)
    schema = Path(__file__).resolve().parents[1] / "src/main/resources/db/changelog/supplier/006-supplier-catalog.sql"
    with output.open("w") as sql:
        sql.write("\\set ON_ERROR_STOP on\nBEGIN;\nSET standard_conforming_strings = on;\n")
        sql.write(schema.read_text() + "\n")
        # Prefer the real legal entity synced from SEF, identified by its VAT number.
        # The development seed is only a fallback for fresh development databases.
        sql.write("CREATE TEMP TABLE planetbike_target AS SELECT id FROM supplier WHERE vat_registration_number = '100116685' OR (code = 'PLANET_BIKE' AND NOT EXISTS (SELECT 1 FROM supplier WHERE vat_registration_number = '100116685'));\n")
        sql.write("DO $$ BEGIN IF (SELECT count(*) FROM planetbike_target) <> 1 THEN RAISE EXCEPTION 'Planet Bike supplier missing'; END IF; END $$;\n")
        sql.write(f"INSERT INTO supplier_import_run (id,supplier_id,source_type,status,original_filename,content_type,sha256_hash,uploaded_by,started_at,finished_at,total_rows,created_supplier_products,updated_supplier_products,price_snapshots,issues_count) SELECT {literal(run_id)},id,'B2B_CATALOG','COMPLETED',{literal(source['stockFilename'])},'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',{literal(digest)},'planetbike-bikes-import',{literal(stamp)},{literal(stamp)},{len(entries)},0,0,{len(entries)},0 FROM planetbike_target ON CONFLICT (id) DO NOTHING;\n")
        sql.write("CREATE TEMP TABLE planetbike_before AS SELECT id FROM supplier_product WHERE supplier_id = (SELECT id FROM planetbike_target);\n")
        for sha, image in images.items():
            sql.write(f"INSERT INTO supplier_catalog_image (id,supplier_id,sha256,content_type,source_url,content,captured_at) SELECT {literal(image['id'])},id,{literal(sha)},{literal(image['contentType'])},{literal(image['sourceUrl'])},decode('{image['content'].hex()}','hex'),{literal(stamp)} FROM planetbike_target ON CONFLICT (id) DO NOTHING;\n")
        for entry in entries:
            sku = entry["supplierCode"]
            product_id = identifier("product", sku)
            barcode = next((a["value"] for a in entry["specifications"] if a["code"] in {"ean", "barcode"}), None)
            values = [product_id, sku, barcode, entry["name"], entry["brand"], entry["category"], entry["description"][:4000], entry["remoteImageUrl"], stamp]
            quoted = [literal(v) for v in values]
            sql.write(f"INSERT INTO supplier_product (id,supplier_id,supplier_code,supplier_barcode,supplier_product_name,brand_name,category_text,description,image_url,active,created_at,version) SELECT {quoted[0]},id,{','.join(quoted[1:8])},true,{quoted[8]},0 FROM planetbike_target ON CONFLICT (supplier_id,supplier_code) DO UPDATE SET supplier_product_name=excluded.supplier_product_name,brand_name=excluded.brand_name,category_text=excluded.category_text,description=excluded.description,image_url=excluded.image_url,supplier_barcode=coalesce(excluded.supplier_barcode,supplier_product.supplier_barcode),active=true,updated_at=excluded.created_at,version=supplier_product.version+1;\n")
            actual_id = f"(SELECT id FROM supplier_product WHERE supplier_id=(SELECT id FROM planetbike_target) AND supplier_code={literal(sku)})"
            metadata = {k: v for k, v in entry.items() if k not in {"quantity", "price", "remoteImageUrl"}}
            sql.write(f"INSERT INTO supplier_catalog_product (supplier_product_id,model_code,source_url,metadata,captured_at,stock_date) VALUES ({actual_id},{literal(entry['modelCode'])},{literal(entry['sourceUrl'])},{literal(json.dumps(metadata,ensure_ascii=False))}::jsonb,{literal(stamp)},{literal(stock_date)}) ON CONFLICT (supplier_product_id) DO UPDATE SET model_code=excluded.model_code,source_url=excluded.source_url,metadata=excluded.metadata,captured_at=excluded.captured_at,stock_date=excluded.stock_date;\n")
            snapshot_id = identifier("snapshot", run_id + ":" + sku)
            sql.write(f"INSERT INTO supplier_price_snapshot (id,supplier_product_id,import_run_id,purchase_price,currency,vat_included,available_quantity,captured_at,source) VALUES ({literal(snapshot_id)},{actual_id},{literal(run_id)},{entry['price']},'RSD',false,{entry['quantity']},{literal(stamp)},'PLANET_BIKE_LAGER_LISTA') ON CONFLICT (id) DO NOTHING;\n")
        sql.write(f"UPDATE supplier_import_run SET created_supplier_products=(SELECT count(*) FROM supplier_product p JOIN supplier_catalog_product c ON c.supplier_product_id=p.id WHERE p.supplier_id=(SELECT id FROM planetbike_target) AND p.id NOT IN (SELECT id FROM planetbike_before)), updated_supplier_products={len(entries)}-(SELECT count(*) FROM supplier_product p JOIN supplier_catalog_product c ON c.supplier_product_id=p.id WHERE p.supplier_id=(SELECT id FROM planetbike_target) AND p.id NOT IN (SELECT id FROM planetbike_before)) WHERE id={literal(run_id)} AND started_at={literal(stamp)};\n")
        sql.write("COMMIT;\n")
    output.chmod(0o600)
    summary = {"models": len(list(capture.glob("products/*.json"))), "variants": len(entries), "images": len(images), "stockDate": str(stock_date), "totalQuantity": sum(e["quantity"] for e in entries), "importId": run_id}
    (capture / "summary.json").write_text(json.dumps(summary, indent=2))
    print(json.dumps(summary))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("capture", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    write_import(args.capture, args.output)
