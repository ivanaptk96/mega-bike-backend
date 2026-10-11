package com.megabike.catalog.application;

import com.megabike.catalog.api.ProductResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
public class ProductOriginService {
	private final JdbcTemplate jdbc;
	public ProductOriginService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
	@Transactional(readOnly = true)
	public List<ProductResponse.SupplierOrigin> suppliers(UUID productId) {
		return jdbc.query("""
				select distinct s.id, coalesce(s.display_name, s.name) as name
				from goods_receipt_line l
				join goods_receipt r on r.id = l.receipt_id
				join sef_invoice i on i.id = r.invoice_id
				join supplier s on s.id = i.supplier_id
				where l.product_id = ? and l.received_quantity > 0
				order by name, s.id
				""", (rs, n) -> new ProductResponse.SupplierOrigin(rs.getObject("id", UUID.class), rs.getString("name")), productId);
	}
}
