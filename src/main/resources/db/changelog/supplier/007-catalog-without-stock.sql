-- Public supplier catalogues do not have a wholesale stock-list date.
ALTER TABLE supplier_catalog_product ALTER COLUMN stock_date DROP NOT NULL;
