#!/usr/bin/env python3
"""Slow, resumable capture of Capriolo's public Bicikli catalogue (no stock import).

Uses the same public guest endpoints as their website, then saves product pages
and original gallery images. Every network request is sequential with a pause.
No login or database access is performed. Captures belong outside Git.
"""
import argparse
import datetime as dt
import hashlib
from html.parser import HTMLParser
import json
from pathlib import Path
import random
import re
import subprocess
import time
from urllib.parse import urlparse

SITE = "https://www.capriolo.com"
API = "https://api.capriolo.croonus.app/api/v1/b2c/"


def save_json(path, value):
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2))
    temporary.replace(path)


class Node:
    def __init__(self, tag, attrs=None):
        self.tag, self.attrs, self.children = tag, dict(attrs or []), []

    def text(self):
        return " ".join(c.text() if isinstance(c, Node) else c for c in self.children).strip()

    def walk(self):
        yield self
        for child in self.children:
            if isinstance(child, Node):
                yield from child.walk()


class Document(HTMLParser):
    def __init__(self, html):
        super().__init__(convert_charrefs=True)
        self.root = Node("root")
        self.stack = [self.root]
        self.feed(html)

    def handle_starttag(self, tag, attrs):
        node = Node(tag, attrs)
        self.stack[-1].children.append(node)
        if tag not in {"area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr"}:
            self.stack.append(node)

    def handle_endtag(self, tag):
        for i in range(len(self.stack) - 1, 0, -1):
            if self.stack[i].tag == tag:
                del self.stack[i:]
                break

    def handle_data(self, data):
        self.stack[-1].children.append(data)


def dictionaries(value):
    if isinstance(value, dict):
        yield value
        for child in value.values():
            yield from dictionaries(child)
    elif isinstance(value, list):
        for child in value:
            yield from dictionaries(child)


def flight_records(document):
    stream = ""
    for node in document.root.walk():
        if node.tag != "script":
            continue
        for frame in re.findall(r"self\.__next_f\.push\((.*?)\)(?:;|$)", "".join(c for c in node.children if isinstance(c, str)), re.S):
            try:
                value = json.loads(frame)
                if value[0] == 1:
                    stream += value[1]
            except (ValueError, IndexError):
                pass
    records = {}
    decoder = json.JSONDecoder()
    data = stream.encode()
    position = 0
    while position < len(data):
        match = re.match(rb"([0-9a-f]+):", data[position:])
        if not match:
            position += 1
            continue
        key = match[1].decode()
        position += match.end()
        text = re.match(rb"T([0-9a-f]+),", data[position:])
        if text:
            position += text.end()
            length = int(text[1], 16)
            records[key] = data[position:position + length].decode()
            position += length
            continue
        tail = data[position:].decode()
        try:
            value, consumed = decoder.raw_decode(tail)
            records[key] = value
            position += len(tail[:consumed].encode())
        except ValueError:
            end = data.find(b"\n", position)
            position = len(data) if end < 0 else end + 1
    return records


def parse_product(html, listing):
    document = Document(html)
    records = flight_records(document)
    candidates = [obj for value in records.values() for obj in dictionaries(value)
                  if obj.get("id") == listing["id"] and isinstance(obj.get("basic_data"), dict)
                  and obj["basic_data"].get("sku")]
    if not candidates:
        raise ValueError(f"Product page has no matching product data: {listing['id']}")
    product = candidates[0]
    basic = product["basic_data"]
    if basic["sku"] != listing["basic_data"]["sku"]:
        raise ValueError("Listing/page SKU mismatch")
    description = basic.get("description") or basic.get("short_description") or ""
    if re.fullmatch(r"\$[0-9a-f]+", description):
        description = records.get(description[1:])
        if not isinstance(description, str):
            raise ValueError("Unresolved product description")
    description = Document(description).root.text() if "<" in description else description
    specifications = []
    for node in document.root.walk():
        children = [c for c in node.children if isinstance(c, Node)]
        for index, child in enumerate(children[:-1]):
            if child.text() != "Specifikacija":
                continue
            for row in children[index + 1].children:
                if not isinstance(row, Node):
                    continue
                cells = [c for c in row.children if isinstance(c, Node)]
                if len(cells) == 2 and cells[0].text():
                    specifications.append({"label": cells[0].text(), "value": cells[1].text()})
    images = list(listing.get("image") or [])
    variants = []
    for value in records.values():
        for obj in dictionaries(value):
            if "product_gallery" in obj and obj.get("id") == listing["id"]:
                gallery = obj["product_gallery"] or {}
                images = [e["image"] for e in gallery.get("gallery", []) if e.get("image")] or images
                variants = ((obj.get("basic_data") or {}).get("data") or {}).get("variant_items") or []
    categories = listing.get("categories") or []
    if not categories or any(not c.get("slug_path", "").startswith("/bicikli") for c in categories):
        raise ValueError("Unexpected category outside Bicikli")
    price = listing.get("price") or {}
    per_item = ((price.get("price") or {}).get("calc") or {}).get("per_item") or {}
    retail = per_item.get("total") if price.get("price_defined") else None
    return {
        "remoteId": listing["id"], "supplierCode": basic["sku"], "modelCode": basic["sku"],
        "name": basic["name"], "barcode": basic.get("barcode"), "brand": basic.get("brand_name"),
        "category": " / ".join(dict.fromkeys(c["category_path_name"] for c in categories)),
        "description": description, "specifications": specifications, "variations": [],
        "sourceUrl": listing["link"]["absolute_link"], "imageUrls": list(dict.fromkeys(images)),
        "referenceRetailPrice": retail, "referencePriceCurrency": (price.get("currency") or "RSD").upper(),
        "referencePriceIncludesVat": True if retail is not None else None,
        "rawData": {"product": product, "variantItems": variants, "listing": listing},
    }


class Capture:
    def __init__(self, directory, delay, image_mode="all", max_images=None):
        self.directory, self.delay, self.last_request = directory, delay, 0
        self.image_mode = image_mode
        self.max_images = max_images
        self.guest = f"device_megabike_catalog_{random.getrandbits(64):x}"
        for name in ["pages", "products", "images", "listings"]:
            (directory / name).mkdir(parents=True, exist_ok=True)

    def fetch(self, url, path, method="GET", body=None):
        if path.exists():
            return path.read_bytes()
        if urlparse(url).hostname not in {"www.capriolo.com", "api.capriolo.croonus.app"}:
            raise ValueError("Unexpected external capture URL")
        temporary = path.with_suffix(path.suffix + ".part")
        for attempt in range(4):
            time.sleep(max(0, self.last_request + self.delay - time.monotonic()))
            self.last_request = time.monotonic()
            args = ["curl", "--silent", "--show-error", "--fail", "--location", "--max-time", "90",
                    "--user-agent", "MegaBike public catalogue archive (sequential, slow requests)",
                    "--output", str(temporary), "--request", method]
            if url.startswith(API):
                args += ["--header", f"customer-token: {self.guest}", "--header", f"device-token: {self.guest}",
                         "--header", f"referer-url: {SITE}/bicikli"]
            if body is not None:
                args += ["--header", "Content-Type: application/json", "--data", json.dumps(body)]
            result = subprocess.run(args + [url], capture_output=True, text=True)
            if result.returncode == 0:
                temporary.replace(path)
                return path.read_bytes()
            if "403" in result.stderr:
                raise RuntimeError("Access denied; stopping capture")
            print(f"Retry {attempt + 1}: {url}: {result.stderr.strip()}", flush=True)
            time.sleep(15 * (attempt + 1))
        raise RuntimeError(f"Could not download {url}")

    def api(self, endpoint, path, body=None):
        result = json.loads(self.fetch(API + endpoint, path, "LIST" if body else "GET", body))
        if result.get("success") is not True:
            raise ValueError(f"API did not return a successful capture: {result.get('message')}")
        return result["payload"]

    def run(self):
        robots = self.fetch(SITE + "/robots.txt", self.directory / "robots.txt").decode()
        # Refuse new rules that require manual scope review.
        if re.search(r"^Disallow:[ \t]*\S+", robots, re.M):
            raise ValueError("robots.txt rules changed; review before capture")
        tree = self.api("categories/product/tree", self.directory / "categories.json")
        root = next(c for c in tree if c["slug"] == "bicikli")
        entries = []
        page = 1
        expected = None
        while True:
            payload = self.api("products/category/list/bicikli", self.directory / "listings" / f"{page:03}.json",
                               {"page": page, "limit": 100, "sort": {"field": "id", "direction": "asc"}, "filters": [], "render": True})
            pagination = payload["pagination"]
            expected = expected or pagination["total_items"]
            if expected != pagination["total_items"]:
                raise ValueError("Catalogue changed during pagination; restart with a fresh capture")
            entries.extend(payload["items"])
            print(f"Listing {page}/{pagination['total_pages']}: {len(entries)}/{expected}", flush=True)
            if page >= pagination["total_pages"]:
                break
            page += 1
        if len(entries) != expected or len({e["id"] for e in entries}) != expected:
            raise ValueError("Incomplete or duplicated listing pagination")
        save_json(self.directory / "source.json", {"supplier": "CAPRIOLO", "site": SITE, "scope": "bicikli",
                  "startedAt": dt.datetime.now(dt.timezone.utc).isoformat(), "expectedProducts": expected,
                  "categoryCountAtCapture": root["counts"]["products"]["total"], "requestPauseSeconds": self.delay,
                  "imageMode": self.image_mode, "maxImages": self.max_images})
        manifest_path = self.directory / "image-manifest.json"
        manifest = json.loads(manifest_path.read_text()) if manifest_path.exists() else {}
        issues = []
        for index, listing in enumerate(entries, 1):
            try:
                path = self.directory / "products" / f"{listing['id']}.json"
                if path.exists():
                    entry = json.loads(path.read_text())
                else:
                    url = listing["link"]["absolute_link"]
                    html = self.fetch(url, self.directory / "pages" / f"{listing['id']}.html").decode()
                    entry = parse_product(html, listing)
                    entry["capturedAt"] = dt.datetime.now(dt.timezone.utc).isoformat()
                    save_json(path, entry)
                entry.setdefault("sourceImageUrls", list(entry["imageUrls"]))
                original_urls = entry["sourceImageUrls"]
                limit = 1 if self.image_mode == "primary" else self.max_images
                selected_urls = original_urls[:limit]
                # Preserve already downloaded images when reducing the gallery limit.
                entry["imageUrls"] = [
                    url for url in original_urls
                    if url in selected_urls or url in manifest or
                    (self.directory / "images" / (hashlib.sha256(url.encode()).hexdigest() + ".bin")).exists()]
                save_json(path, entry)
                for url in entry["imageUrls"]:
                    filename = hashlib.sha256(url.encode()).hexdigest() + ".bin"
                    data = self.fetch(url, self.directory / "images" / filename)
                    from import_planetbike_bikes import image_type
                    content_type = image_type(data)
                    manifest[url] = {"file": filename, "sha256": hashlib.sha256(data).hexdigest(), "contentType": content_type}
                save_json(manifest_path, manifest)
                print(f"Product {index}/{expected}: {entry['supplierCode']} — {len(entry['imageUrls'])} images", flush=True)
            except Exception as error:
                issues.append({"id": listing["id"], "error": str(error)})
                print(f"Product {index}/{expected} FAILED: {error}", flush=True)
                if "Access denied" in str(error):
                    break
            save_json(self.directory / "progress.json", {"processed": index, "expected": expected,
                      "savedProducts": len(list((self.directory / "products").glob("*.json"))), "images": len(manifest), "issues": issues})
        save_json(self.directory / "issues.json", issues)
        if issues:
            raise ValueError(f"Capture has {len(issues)} issues; resume before importing")
        save_json(self.directory / "completed.json", {"products": expected, "images": len(manifest),
                  "finishedAt": dt.datetime.now(dt.timezone.utc).isoformat(), "imageMode": self.image_mode,
                  "maxImages": self.max_images})


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("capture", type=Path)
    parser.add_argument("--delay", type=float, default=3)
    parser.add_argument("--images", choices=["all", "primary"], default="all")
    parser.add_argument("--max-images", type=int)
    args = parser.parse_args()
    if args.delay < 1:
        parser.error("Keep at least one second between sequential requests")
    if args.max_images is not None and args.max_images < 1:
        parser.error("--max-images must be at least one")
    Capture(args.capture, args.delay, args.images, args.max_images).run()
