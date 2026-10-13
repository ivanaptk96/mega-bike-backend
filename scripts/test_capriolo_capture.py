"""Offline checks for capture parsing and import completeness; no network or database."""
import datetime as dt
import contextlib
import io
import json
from pathlib import Path
import tempfile
import unittest

from capture_capriolo_bikes import Capture, parse_product
from import_capriolo_bikes import prepare


def fixture():
    listing = {
        "id": 10, "product_type": "single", "basic_data": {"sku": "BIKE-17"},
        "image": [], "link": {"absolute_link": "https://www.capriolo.com/bike"},
        "categories": [{"slug_path": "/bicikli/mtb", "category_path_name": "Bicikli > MTB"}],
        "price": {"price_defined": True, "currency": "rsd", "price": {"calc": {"per_item": {"total": 42999}}}},
    }
    description = "<p>Dugačak opis sa slovima č, ć i š.\nDrugi red.</p>"
    product = {"id": 10, "product_type": "single", "basic_data": {
        "sku": "BIKE-17", "name": "Bicikl 17", "barcode": "123456", "brand_name": "Capriolo", "description": "$27"}}
    props = {"id": 10, "basic_data": {"data": {"item": product, "variant_items": []}}, "product_gallery": {"gallery": []}}
    # React flight text records have UTF-8 byte lengths and no trailing newline.
    flight = f"27:T{len(description.encode()):x},{description}9:" + json.dumps(props) + "\n"
    html = '<script>self.__next_f.push(' + json.dumps([1, flight]) + ')</script>'
    html += '<div><div>Specifikacija</div><div><div><div>Ram</div><div>Aluminijum</div></div></div></div>'
    return html, listing


class CaprioloCaptureTests(unittest.TestCase):
    def test_gallery_limit_preserves_downloaded_images_and_can_be_expanded_later(self):
        html, listing = fixture()
        urls = [f"https://api.capriolo.croonus.app/image-{i}.webp" for i in range(7)]
        listing["image"] = urls
        downloaded = []

        class OfflineCapture(Capture):
            def api(self, endpoint, path, body=None):
                if endpoint == "categories/product/tree":
                    return [{"slug": "bicikli", "counts": {"products": {"total": 1}}}]
                return {"items": [listing], "pagination": {"total_items": 1, "total_pages": 1}}

            def fetch(self, url, path, method="GET", body=None):
                if path.exists():
                    return path.read_bytes()
                if url.endswith("robots.txt"):
                    content = b"User-agent: *\nDisallow:\n"
                elif url.endswith("/bike"):
                    content = html.encode()
                else:
                    downloaded.append(url)
                    content = b"RIFF0000WEBP" + url.encode()
                path.write_bytes(content)
                return content

        with tempfile.TemporaryDirectory() as temp, contextlib.redirect_stdout(io.StringIO()):
            capture = Path(temp)
            OfflineCapture(capture, 2, max_images=5).run()
            entry = json.loads((capture / "products/10.json").read_text())
            self.assertEqual(entry["imageUrls"], urls[:5])
            self.assertEqual(entry["sourceImageUrls"], urls)
            self.assertEqual(downloaded, urls[:5])
            OfflineCapture(capture, 2, image_mode="primary").run()
            self.assertEqual(downloaded, urls[:5])
            self.assertEqual(len(json.loads((capture / "products/10.json").read_text())["imageUrls"]), 5)
            OfflineCapture(capture, 2).run()
            self.assertEqual(downloaded, urls)
            _, _, entries, images = prepare(capture)
            self.assertEqual(len(entries[0]["imageIds"]), 7)
            self.assertEqual(len(images), 7)

    def test_long_unicode_description_gallery_and_specifications(self):
        html, listing = fixture()
        product = parse_product(html, listing)
        self.assertIn("č, ć i š", product["description"])
        self.assertIn("Drugi red", product["description"])
        self.assertEqual(product["supplierCode"], "BIKE-17")
        self.assertEqual(product["referenceRetailPrice"], 42999)
        self.assertEqual(product["specifications"], [{"label": "Ram", "value": "Aluminijum"}])

    def test_rejects_page_from_another_sku_and_categories_outside_bikes(self):
        html, listing = fixture()
        listing["basic_data"]["sku"] = "OTHER"
        with self.assertRaisesRegex(ValueError, "SKU mismatch"):
            parse_product(html, listing)
        listing["basic_data"]["sku"] = "BIKE-17"
        listing["categories"][0]["slug_path"] = "/fitness"
        with self.assertRaisesRegex(ValueError, "outside Bicikli"):
            parse_product(html, listing)

    def test_import_requires_complete_capture_and_unique_supplier_codes(self):
        with tempfile.TemporaryDirectory() as temp:
            capture = Path(temp)
            (capture / "products").mkdir()
            for name, data in {"source": {"scope": "bicikli", "expectedProducts": 1},
                               "completed": {"products": 1}, "issues": [], "image-manifest": {}}.items():
                (capture / f"{name}.json").write_text(json.dumps(data))
            with self.assertRaisesRegex(ValueError, "Missing products"):
                prepare(capture)
            html, listing = fixture()
            product = parse_product(html, listing)
            product["capturedAt"] = dt.datetime.now(dt.timezone.utc).isoformat()
            (capture / "products/10.json").write_text(json.dumps(product))
            _, _, entries, images = prepare(capture)
            self.assertEqual(len(entries), 1)
            self.assertEqual(images, {})
            (capture / "products/11.json").write_text(json.dumps(product))
            for name in ["source", "completed"]:
                file = capture / f"{name}.json"
                data = json.loads(file.read_text())
                data["expectedProducts" if name == "source" else "products"] = 2
                file.write_text(json.dumps(data))
            with self.assertRaisesRegex(ValueError, "duplicate SKU"):
                prepare(capture)


if __name__ == "__main__":
    unittest.main()
