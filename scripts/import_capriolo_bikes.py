#!/usr/bin/env python3
"""Validate a completed public bike capture and generate a supplier-only SQL import.

Does not create price/quantity snapshots, our products or inventory movements.
Website retail prices remain informational catalogue metadata. Re-imports use
stable supplier SKU and image identifiers, preserving existing product links.
"""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import uuid
from import_planetbike_bikes import image_type, literal

NAMESPACE = uuid.UUID("6e5c5b85-5293-45bd-bc8f-58d88a8fdf2e")


def identifier(kind, value):
    return str(uuid.uuid5(NAMESPACE, f"capriolo:{kind}:{value}"))


def prepare(capture):
    source = json.loads((capture / "source.json").read_text())
    completed = json.loads((capture / "completed.json").read_text())
    if source["scope"] != "bicikli" or completed["products"] != source["expectedProducts"]:
        raise ValueError("Capture scope/count is invalid")
    if json.loads((capture / "issues.json").read_text()):
        raise ValueError("Resolve capture issues before import")
    entries = [json.loads(p.read_text()) for p in sorted((capture / "products").glob("*.json"))]
    if len(entries) != completed["products"] or len({e["supplierCode"] for e in entries}) != len(entries):
        raise ValueError("Missing products or duplicate SKU")
    manifest = json.loads((capture / "image-manifest.json").read_text())
    images = {}
    for entry in entries:
        if not entry["supplierCode"] or not entry["name"] or not entry["category"].startswith("Bicikli"):
            raise ValueError("Incomplete product or product outside Bicikli")
        if (entry["rawData"]["product"].get("product_type") or "single") != "single" or entry["rawData"].get("variantItems"):
            raise ValueError("Grouped variants require explicit expansion before import")
        for field, limit in [("supplierCode", 120), ("barcode", 80), ("name", 300), ("brand", 160), ("category", 300), ("sourceUrl", 1000)]:
            if len(entry.get(field) or "") > limit:
                raise ValueError(f"{field} exceeds database limit")
        dt.datetime.fromisoformat(entry["capturedAt"])
        amount = entry.get("referenceRetailPrice")
        if amount is not None and (not isinstance(amount, (int, float)) or amount < 0):
            raise ValueError("Invalid reference retail price")
        entry["imageIds"] = []
        for url in entry["imageUrls"]:
            asset = manifest[url]
            data = (capture / "images" / asset["file"]).read_bytes()
            sha = hashlib.sha256(data).hexdigest()
            if sha != asset["sha256"]:
                raise ValueError("Image content differs from manifest")
            content_type = image_type(data)
            image_id = identifier("image", sha)
            images.setdefault(sha, {"id": image_id, "sourceUrl": url, "content": data, "contentType": content_type})
            if image_id not in entry["imageIds"]:
                entry["imageIds"].append(image_id)
    return source, completed, entries, images


def write_import(capture, output):
    source, completed, entries, images = prepare(capture)
    stamp = completed["finishedAt"]
    digest = hashlib.sha256(json.dumps(entries, ensure_ascii=False, sort_keys=True).encode()).hexdigest()
    run_id = identifier("import", digest)
    changelog = Path(__file__).resolve().parents[1] / "src/main/resources/db/changelog/supplier"
    with output.open("w") as sql:
        sql.write("\\set ON_ERROR_STOP on\nBEGIN;\nSET standard_conforming_strings = on;\n")
        sql.write((changelog / "006-supplier-catalog.sql").read_text() + "\n")
        sql.write((changelog / "007-catalog-without-stock.sql").read_text() + "\n")
        sql.write("CREATE TEMP TABLE capriolo_target AS SELECT id FROM supplier WHERE vat_registration_number='101443962' OR (code='CAPRIOLO' AND NOT EXISTS (SELECT 1 FROM supplier WHERE vat_registration_number='101443962'));\n")
        sql.write("DO $$ BEGIN IF (SELECT count(*) FROM capriolo_target) <> 1 THEN RAISE EXCEPTION 'Unique Capriolo supplier missing'; END IF; END $$;\n")
        sql.write("CREATE TEMP TABLE capriolo_before AS SELECT supplier_code FROM supplier_product WHERE supplier_id=(SELECT id FROM capriolo_target);\n")
        sql.write(f"INSERT INTO supplier_import_run (id,supplier_id,source_type,status,original_filename,content_type,sha256_hash,uploaded_by,started_at,finished_at,total_rows,created_supplier_products,updated_supplier_products,price_snapshots,issues_count) SELECT {literal(run_id)},id,'PUBLIC_CATALOG','COMPLETED',{literal('capriolo-bicikli-' + stamp[:10] + '.json')},'application/json',{literal(digest)},'capriolo-public-bikes-import',{literal(source['startedAt'])},{literal(stamp)},{len(entries)},0,0,0,0 FROM capriolo_target ON CONFLICT (id) DO NOTHING;\n")
        for sha, image in images.items():
            sql.write(f"INSERT INTO supplier_catalog_image (id,supplier_id,sha256,content_type,source_url,content,captured_at) SELECT {literal(image['id'])},id,{literal(sha)},{literal(image['contentType'])},{literal(image['sourceUrl'])},decode('{image['content'].hex()}','hex'),{literal(stamp)} FROM capriolo_target ON CONFLICT (supplier_id,sha256) DO NOTHING;\n")
        for entry in entries:
            sku = entry["supplierCode"]
            quoted = [literal(v) for v in [identifier("product", sku), sku, entry.get("barcode"), entry["name"], entry["brand"], entry["category"], entry["description"][:4000], entry["imageUrls"][0] if entry["imageUrls"] else None, stamp]]
            sql.write(f"INSERT INTO supplier_product (id,supplier_id,supplier_code,supplier_barcode,supplier_product_name,brand_name,category_text,description,image_url,active,created_at,version) SELECT {quoted[0]},id,{','.join(quoted[1:8])},true,{quoted[8]},0 FROM capriolo_target ON CONFLICT (supplier_id,supplier_code) DO UPDATE SET supplier_product_name=excluded.supplier_product_name,brand_name=excluded.brand_name,category_text=excluded.category_text,description=excluded.description,image_url=excluded.image_url,supplier_barcode=coalesce(excluded.supplier_barcode,supplier_product.supplier_barcode),active=true,updated_at=excluded.created_at,version=supplier_product.version+1;\n")
            actual_id = f"(SELECT id FROM supplier_product WHERE supplier_id=(SELECT id FROM capriolo_target) AND supplier_code={literal(sku)})"
            metadata = {k: v for k, v in entry.items() if k not in {"imageUrls", "capturedAt"}}
            metadata["catalogSource"] = "PUBLIC_WEBSITE"
            metadata["referencePriceCapturedAt"] = entry["capturedAt"]
            sql.write(f"INSERT INTO supplier_catalog_product (supplier_product_id,model_code,source_url,metadata,captured_at,stock_date) VALUES ({actual_id},{literal(entry['modelCode'])},{literal(entry['sourceUrl'])},{literal(json.dumps(metadata,ensure_ascii=False))}::jsonb,{literal(entry['capturedAt'])},NULL) ON CONFLICT (supplier_product_id) DO UPDATE SET model_code=excluded.model_code,source_url=excluded.source_url,metadata=excluded.metadata,captured_at=excluded.captured_at;\n")
        sql.write(f"UPDATE supplier_import_run SET created_supplier_products=(SELECT count(*) FROM supplier_product WHERE supplier_id=(SELECT id FROM capriolo_target) AND supplier_code NOT IN (SELECT supplier_code FROM capriolo_before)),updated_supplier_products={len(entries)}-(SELECT count(*) FROM supplier_product WHERE supplier_id=(SELECT id FROM capriolo_target) AND supplier_code NOT IN (SELECT supplier_code FROM capriolo_before)) WHERE id={literal(run_id)} AND created_supplier_products=0 AND updated_supplier_products=0;\n")
        sql.write("COMMIT;\n")
    output.chmod(0o600)
    summary = {"scope": "bicikli", "products": len(entries), "images": len(images),
               "productsWithDescription": sum(bool(e["description"]) for e in entries),
               "productsWithImages": sum(bool(e["imageIds"]) for e in entries),
               "productsWithSpecifications": sum(bool(e["specifications"]) for e in entries),
               "productsWithReferencePrice": sum(e["referenceRetailPrice"] is not None for e in entries),
               "importId": run_id, "stockSnapshots": 0}
    (capture / "summary.json").write_text(json.dumps(summary, indent=2))
    print(json.dumps(summary))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("capture", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    write_import(args.capture, args.output)
