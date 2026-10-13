package com.megabike.inventory.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ReceiptCategoryMappingRepository {
	private final JdbcTemplate jdbc;

	public ReceiptCategoryMappingRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

	public Optional<UUID> find(UUID supplierId, String keyType, String key) {
		if (supplierId == null || key == null || key.isBlank()) return Optional.empty();
		return jdbc.queryForList("""
				select category_id from receipt_category_mapping
				where supplier_id = ? and key_type = ? and source_key = ?
				""", UUID.class, supplierId, keyType, key.trim()).stream().findFirst();
	}

	public void remember(UUID supplierId, String keyType, String key, UUID categoryId, String actor) {
		if (supplierId == null || key == null || key.isBlank()) return;
		jdbc.update("""
				insert into receipt_category_mapping(supplier_id, key_type, source_key, category_id, confirmed_by, confirmed_at)
				values (?, ?, ?, ?, ?, current_timestamp)
				on conflict (supplier_id, key_type, source_key) do update
				set category_id = excluded.category_id, confirmed_by = excluded.confirmed_by, confirmed_at = excluded.confirmed_at
				""", supplierId, keyType, key.trim(), categoryId, actor);
	}
}
