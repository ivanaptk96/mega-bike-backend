DO $$
DECLARE mapping record; source_row record; target_row record;
BEGIN
 FOR mapping IN SELECT * FROM (VALUES
  ('bikes', 'biciklizam-bicikli'), ('parts', 'biciklizam-delovi'),
  ('mtb', 'biciklizam-bicikli-mtb'), ('road', 'biciklizam-bicikli-drumski')
 ) AS aliases(old_slug, new_slug) LOOP
  SELECT * INTO source_row FROM category WHERE slug = mapping.old_slug;
  IF NOT FOUND THEN CONTINUE; END IF;
  SELECT * INTO target_row FROM category WHERE slug = mapping.new_slug;
  IF NOT FOUND THEN CONTINUE; END IF;
  UPDATE category SET parent_id = source_row.id WHERE parent_id = target_row.id;
  UPDATE product SET category_id = source_row.id WHERE category_id = target_row.id;
  DELETE FROM category WHERE id = target_row.id;
  UPDATE category SET name = target_row.name, slug = target_row.slug, parent_id = target_row.parent_id,
    web_visible = target_row.web_visible, updated_at = CURRENT_TIMESTAMP, version = version + 1 WHERE id = source_row.id;
 END LOOP;
 -- Correct the two known development samples without guessing categories for other products.
 UPDATE product SET category_id = (SELECT id FROM category WHERE slug = 'biciklizam-oprema-kacige'),
   updated_at = CURRENT_TIMESTAMP, version = version + 1
   WHERE id = '50000000-0000-0000-0000-000000000001' AND product_code = 'MB-P-0001' AND name = 'Trail Helmet M';
 UPDATE product SET category_id = (SELECT id FROM category WHERE slug = 'biciklizam-delovi-pakne-plocice-i-diskovi'),
   updated_at = CURRENT_TIMESTAMP, version = version + 1
   WHERE id = '50000000-0000-0000-0000-000000000002' AND product_code = 'MB-P-0002' AND name = 'Road Brake Pads';
END $$;
