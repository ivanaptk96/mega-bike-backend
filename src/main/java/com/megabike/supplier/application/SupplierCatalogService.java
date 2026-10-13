package com.megabike.supplier.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.megabike.shared.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SupplierCatalogService {

	private final JdbcTemplate jdbc;
	private final ObjectMapper mapper;

	public SupplierCatalogService(JdbcTemplate jdbc, ObjectMapper mapper) {
		this.jdbc = jdbc;
		this.mapper = mapper;
	}

	public record CatalogPage(List<Map<String, Object>> content, int page, int size, long totalElements, long totalPages) {}
	public record CatalogImage(byte[] content, String contentType) {}
	public record CatalogSummary(long totalVariants, long inStockVariants, long unavailableVariants,
			long unknownStockVariants, BigDecimal unitsAvailable, Instant catalogCapturedAt,
			String stockDate, String stockFilename, Instant stockCapturedAt) {}

	public CatalogPage list(UUID supplierId, String query, boolean availableOnly, int page, int size) {
		return list(supplierId, query, availableOnly, "ALL", page, size);
	}

	public CatalogPage list(UUID supplierId, String query, boolean availableOnly, String availability, int page, int size) {
		if (page < 0 || size < 1 || size > 100) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", "Page must be positive and size between 1 and 100.");
		}
		if (!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from supplier where id = ?)", Boolean.class, supplierId))) {
			throw new ApiException(HttpStatus.NOT_FOUND, "SUPPLIER_NOT_FOUND", "Supplier not found.");
		}
		String filter = query == null ? "" : query.trim();
		String stockFilter = availableOnly ? "AVAILABLE" : availability;
		if (!Set.of("ALL", "AVAILABLE", "OUT_OF_STOCK", "UNKNOWN").contains(stockFilter))
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AVAILABILITY", "Neispravan filter dostupnosti.");
		String from = """
				from supplier_catalog_product c
				join supplier_product p on p.id = c.supplier_product_id
				left join lateral (
				    select purchase_price, available_quantity, currency, vat_included, captured_at, import_run_id
				    from supplier_price_snapshot where supplier_product_id = p.id
				    order by captured_at desc, id desc limit 1
				) s on true
				left join supplier_import_run r on r.id = s.import_run_id
				where p.supplier_id = ? and p.active
				and (? = '' or strpos(lower(p.supplier_product_name || ' ' || p.supplier_code || ' ' || coalesce(p.brand_name, '')), lower(?)) > 0)
				and (? = 'ALL' or (? = 'AVAILABLE' and s.available_quantity > 0)
				    or (? = 'OUT_OF_STOCK' and s.available_quantity <= 0)
				    or (? = 'UNKNOWN' and s.available_quantity is null))
				""";
		Long total = jdbc.queryForObject("select count(*) " + from, Long.class, supplierId, filter, filter, stockFilter, stockFilter, stockFilter, stockFilter);
		List<Map<String, Object>> items = jdbc.query("""
				select c.metadata - 'rawData' as metadata, p.id, c.source_url, c.captured_at,
				c.stock_date, s.purchase_price, s.available_quantity, s.currency, s.vat_included,
				s.captured_at as stock_captured_at, r.original_filename
				""" + from + " order by p.supplier_product_name, p.supplier_code limit ? offset ?", (rs, row) -> {
			Map<String, Object> item = readMetadata(rs.getString("metadata"));
			item.put("id", rs.getObject("id").toString());
			item.put("sourceUrl", rs.getString("source_url"));
			item.put("capturedAt", rs.getTimestamp("captured_at").toInstant().toString());
			item.put("purchasePrice", rs.getBigDecimal("purchase_price"));
			item.put("availableQuantity", rs.getBigDecimal("available_quantity"));
			item.put("currency", rs.getString("currency"));
			item.put("vatIncluded", rs.getObject("vat_included"));
			item.put("stockFilename", rs.getString("original_filename"));
			var stockTime = rs.getTimestamp("stock_captured_at");
			item.put("stockDate", stockDate(rs.getString("original_filename"), stockTime == null ? null : stockTime.toInstant()));
			item.put("stockCapturedAt", stockTime == null ? null : stockTime.toInstant().toString());
			return item;
		}, supplierId, filter, filter, stockFilter, stockFilter, stockFilter, stockFilter, size, (long) page * size);
		return new CatalogPage(items, page, size, total, (total + size - 1) / size);
	}

	/** Global figures and provenance are independent of the current search and page. */
	public CatalogSummary summary(UUID supplierId) {
		if (!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from supplier where id = ?)", Boolean.class, supplierId)))
			throw new ApiException(HttpStatus.NOT_FOUND, "SUPPLIER_NOT_FOUND", "Supplier not found.");
		var totals = jdbc.query("""
				select count(*) as total,
				    count(*) filter (where s.available_quantity > 0) as available,
				    count(*) filter (where s.available_quantity <= 0) as unavailable,
				    count(*) filter (where s.available_quantity is null) as unknown,
				    coalesce(sum(greatest(s.available_quantity, 0)), 0) as units,
				    max(c.captured_at) as catalog_time
				from supplier_catalog_product c join supplier_product p on p.id = c.supplier_product_id
				left join lateral (
				    select available_quantity from supplier_price_snapshot where supplier_product_id = p.id
				    order by captured_at desc, id desc limit 1
				) s on true
				where p.supplier_id = ? and p.active
				""", (rs, row) -> new CatalogSummary(rs.getLong("total"), rs.getLong("available"), rs.getLong("unavailable"),
					rs.getLong("unknown"), rs.getBigDecimal("units"), rs.getTimestamp("catalog_time") == null ? null : rs.getTimestamp("catalog_time").toInstant(), null, null, null), supplierId).getFirst();
		var latest = jdbc.query("""
				select s.captured_at, r.original_filename from supplier_price_snapshot s
				join supplier_product p on p.id = s.supplier_product_id
				join supplier_catalog_product c on c.supplier_product_id = p.id
				left join supplier_import_run r on r.id = s.import_run_id
				where p.supplier_id = ? and p.active
				order by s.captured_at desc, s.id desc limit 1
				""", (rs, row) -> {
				Instant captured = rs.getTimestamp("captured_at").toInstant();
				String filename = rs.getString("original_filename");
				return new CatalogSummary(totals.totalVariants(), totals.inStockVariants(), totals.unavailableVariants(), totals.unknownStockVariants(),
						totals.unitsAvailable(), totals.catalogCapturedAt(), stockDate(filename, captured), filename, captured);
			}, supplierId);
		return latest.isEmpty() ? totals : latest.getFirst();
	}

	private String stockDate(String filename, Instant captured) {
		String date = captured == null ? null : captured.atOffset(ZoneOffset.UTC).toLocalDate().toString();
		var match = java.util.regex.Pattern.compile("\\d{2}\\.\\d{2}\\.\\d{4}").matcher(filename == null ? "" : filename);
		if (match.find()) {
			try { date = LocalDate.parse(match.group(), DateTimeFormatter.ofPattern("dd.MM.uuuu")).toString(); }
			catch (DateTimeParseException ignored) { /* Keep the capture date. */ }
		}
		return date;
	}

	public Map<String, Object> details(UUID id) {
		List<Map<String, Object>> items = jdbc.query("select metadata from supplier_catalog_product where supplier_product_id = ?",
				(rs, row) -> readMetadata(rs.getString(1)), id);
		if (items.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "SUPPLIER_CATALOG_PRODUCT_NOT_FOUND", "Supplier catalog product not found.");
		return items.getFirst();
	}

	public CatalogImage image(UUID id) {
		List<CatalogImage> items = jdbc.query("select content, content_type from supplier_catalog_image where id = ?",
				(rs, row) -> new CatalogImage(rs.getBytes(1), rs.getString(2)), id);
		if (items.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "SUPPLIER_IMAGE_NOT_FOUND", "Supplier image not found.");
		return items.getFirst();
	}

	private Map<String, Object> readMetadata(String json) {
		try {
			return mapper.readValue(json, new TypeReference<>() {});
		} catch (java.io.IOException exception) {
			throw new IllegalStateException("Invalid supplier catalog metadata", exception);
		}
	}
}
