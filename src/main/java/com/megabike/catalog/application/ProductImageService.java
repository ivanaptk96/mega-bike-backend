package com.megabike.catalog.application;

import com.megabike.shared.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
public class ProductImageService {
	private final JdbcTemplate jdbc;
	public ProductImageService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
	public record Image(byte[] content, String contentType) {}

	@Transactional(readOnly = true)
	public List<UUID> ids(UUID productId) {
		return jdbc.queryForList("select id from product_image where product_id = ? order by position, id", UUID.class, productId);
	}

	@Transactional(readOnly = true)
	public Image get(UUID productId, UUID imageId) {
		var images = jdbc.query("select content, content_type from product_image where product_id = ? and id = ?",
				(rs, n) -> new Image(rs.getBytes(1), rs.getString(2)), productId, imageId);
		if (images.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_IMAGE_NOT_FOUND", "Product image not found.");
		return images.getFirst();
	}

	@Transactional
	public void copyFromSupplier(UUID productId, UUID supplierProductId) {
		// Keep an existing product's gallery. Copy bytes so supplier refresh/deletion cannot remove our photos.
		if (!ids(productId).isEmpty()) return;
		jdbc.update("""
				insert into product_image(id, product_id, sha256, content_type, content, position, created_at)
				select gen_random_uuid(), ?, i.sha256, i.content_type, i.content, g.position::integer, now()
				from supplier_catalog_product c
				join supplier_product p on p.id = c.supplier_product_id
				cross join lateral jsonb_array_elements_text(c.metadata->'imageIds') with ordinality g(id, position)
				join supplier_catalog_image i on i.id = g.id::uuid and i.supplier_id = p.supplier_id
				where c.supplier_product_id = ?
				on conflict (product_id, sha256) do nothing
				""", productId, supplierProductId);
	}
}
