-- Also used by the offline supplier importer while the application is stopped.
CREATE TABLE IF NOT EXISTS supplier_catalog_product (
    supplier_product_id uuid PRIMARY KEY REFERENCES supplier_product(id) ON DELETE CASCADE,
    model_code varchar(120) NOT NULL,
    source_url varchar(1000) NOT NULL,
    metadata jsonb NOT NULL,
    captured_at timestamptz NOT NULL,
    stock_date date NOT NULL
);
CREATE TABLE IF NOT EXISTS supplier_catalog_image (
    id uuid PRIMARY KEY,
    supplier_id uuid NOT NULL REFERENCES supplier(id) ON DELETE CASCADE,
    sha256 varchar(64) NOT NULL,
    content_type varchar(80) NOT NULL,
    source_url text NOT NULL,
    content bytea NOT NULL,
    captured_at timestamptz NOT NULL,
    UNIQUE (supplier_id, sha256)
);
CREATE INDEX IF NOT EXISTS ix_supplier_price_snapshot_latest
    ON supplier_price_snapshot (supplier_product_id, captured_at DESC, id DESC);
