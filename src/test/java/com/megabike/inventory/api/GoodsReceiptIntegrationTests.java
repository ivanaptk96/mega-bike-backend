package com.megabike.inventory.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.megabike.inventory.domain.ReceiptStatus;
import com.megabike.sef.domain.*;
import com.megabike.supplier.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "admin", authorities = {"ROLE_ADMIN", "STOCK_RECEIVE", "SUPPLIER_MANAGE", "PRODUCT_WRITE"})
class GoodsReceiptIntegrationTests {
	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");
	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry r) {
		r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		r.add("spring.datasource.username", POSTGRES::getUsername);
		r.add("spring.datasource.password", POSTGRES::getPassword);
		r.add("spring.liquibase.change-log", () -> "classpath:/db/changelog/db.changelog-dev.xml");
		r.add("spring.docker.compose.enabled", () -> "false");
		r.add("megabike.security.jwt.secret", () -> "goods-receipt-test-secret-that-is-long-enough");
	}
	@Autowired MockMvc mvc;
	@Autowired ObjectMapper mapper;
	@Autowired JdbcTemplate jdbc;
	@Autowired SefConnectionRepository connections;
	@Autowired SefInvoiceRepository invoices;
	@Autowired SefInvoiceLineRepository sourceLines;
	@Autowired SupplierRepository suppliers;
	UUID invoiceId;
	UUID location = UUID.fromString("60000000-0000-0000-0000-000000000001");
	UUID existingProduct = UUID.fromString("50000000-0000-0000-0000-000000000001");
	@BeforeEach
	void setup() {
		var now = Instant.now();
		var supplier = new Supplier(UUID.randomUUID(), "TEST-" + UUID.randomUUID(), "Kapriolo test", null, null, "RSD", true, now);
		supplier.updateClassification(SupplierClassification.MERCHANDISE, now); suppliers.save(supplier);
		var connection = connections.save(new SefConnection(UUID.randomUUID(), String.valueOf(java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999)), null, "Test", "test-secret", true, now));
		var invoice = new SefInvoice(UUID.randomUUID(), connection, 1L, SefInvoiceDirection.PURCHASE, now);
		invoice.applyPurchaseOverview(supplier, null, null, "TEST-20", "380", "Approved", supplier.getName(), null, null,
				"RSD", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, now, now, null, now);
		invoices.save(invoice); invoiceId = invoice.getId();
		sourceLines.save(new SefInvoiceLine(UUID.randomUUID(), invoice, 1, "1", "Bicikl", null, "CAP-1", null, "H87",
				new BigDecimal("20"), new BigDecimal("10000"), null, null, "S", new BigDecimal("20"), null, null));
	}
	GoodsReceiptResponse start() throws Exception {
		return mapper.readValue(mvc.perform(post("/api/internal/goods-receipts/" + invoiceId + "/start")
				.contentType("application/json").content(mapper.writeValueAsString(new GoodsReceiptRequest(location))))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), GoodsReceiptResponse.class);
	}
	GoodsReceiptRequest.Approval approval(GoodsReceiptResponse r, int quantity, String price) {
		return new GoodsReceiptRequest.Approval(List.of(new GoodsReceiptRequest.Line(r.lines().getFirst().id(), existingProduct, null,
				BigDecimal.valueOf(quantity), new BigDecimal(price))), r.version());
	}
	GoodsReceiptResponse approve(GoodsReceiptRequest.Approval request) throws Exception {
		return mapper.readValue(mvc.perform(post("/api/internal/goods-receipts/" + invoiceId + "/approve")
				.contentType("application/json").content(mapper.writeValueAsString(request)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), GoodsReceiptResponse.class);
	}
	BigDecimal stock() { return jdbc.queryForObject("select quantity_on_hand from inventory_item where product_id = ? and stock_location_id = ?", BigDecimal.class, existingProduct, location); }
	@Test
	void partialAndFullReceiptUpdateStockPriceAndAuditAndRejectReplay() throws Exception {
		var before = stock();
		var r = start(); assertThat(r.status()).isEqualTo(ReceiptStatus.PENDING); assertThat(stock()).isEqualByComparingTo(before);
		var request = approval(r, 18, "25000.50");
		var partial = approve(request);
		assertThat(partial.status()).isEqualTo(ReceiptStatus.PARTIAL);
		assertThat(partial.lines().getFirst().receivedQuantity()).isEqualByComparingTo("18");
		assertThat(stock()).isEqualByComparingTo(before.add(new BigDecimal("18")));
		assertThat(jdbc.queryForObject("select retail_price from product where id = ?", BigDecimal.class, existingProduct)).isEqualByComparingTo("25000.50");
		assertThat(jdbc.queryForObject("select receipt_retail_price from stock_movement where receipt_line_id = ?", BigDecimal.class, r.lines().getFirst().id())).isEqualByComparingTo("25000.50");
		mvc.perform(post("/api/internal/goods-receipts/" + invoiceId + "/approve").contentType("application/json").content(mapper.writeValueAsString(request))).andExpect(status().isConflict());
		var full = approve(approval(partial, 2, "26000"));
		assertThat(full.status()).isEqualTo(ReceiptStatus.RECEIVED);
		assertThat(stock()).isEqualByComparingTo(before.add(new BigDecimal("20")));
		assertThat(full.lines().getFirst().approvedBy()).isEqualTo("admin");
		assertThat(jdbc.queryForObject("select status from goods_receipt where invoice_id = ?", String.class, invoiceId)).isEqualTo("RECEIVED");
	}
	@Test
	void overReceiptAndInvalidBulkRollbackWithoutChangingPriceOrStock() throws Exception {
		var r = start(); var before = stock();
		mvc.perform(post("/api/internal/goods-receipts/" + invoiceId + "/approve").contentType("application/json")
				.content(mapper.writeValueAsString(approval(r, 21, "30000")))).andExpect(status().isConflict());
		var valid = approval(r, 10, "30000").lines().getFirst();
		var invalid = new GoodsReceiptRequest.Line(UUID.randomUUID(), existingProduct, null, BigDecimal.ONE, new BigDecimal("30000"));
		var priceBefore = jdbc.queryForObject("select retail_price from product where id = ?", BigDecimal.class, existingProduct);
		mvc.perform(post("/api/internal/goods-receipts/" + invoiceId + "/approve").contentType("application/json")
				.content(mapper.writeValueAsString(new GoodsReceiptRequest.Approval(List.of(valid, invalid), r.version())))).andExpect(status().isBadRequest());
		assertThat(stock()).isEqualByComparingTo(before);
		assertThat(jdbc.queryForObject("select retail_price from product where id = ?", BigDecimal.class, existingProduct)).isEqualByComparingTo(priceBefore);
		assertThat(jdbc.queryForObject("select count(*) from stock_movement where receipt_line_id = ?", Integer.class, valid.lineId())).isZero();
	}
	@Test
	void newCatalogProductIsCreatedAtConfirmationAndSnapshotSurvivesResync() throws Exception {
		var r = start(); var line = r.lines().getFirst();
		assertThat(line.productCode()).matches("MB-[0-9]{6,}");
		assertThat(start().lines().getFirst().productCode()).isEqualTo(line.productCode());
		assertThat(jdbc.queryForObject("select count(*) from product where product_code = ?", Integer.class, line.productCode())).isZero();
		// Sync replaces SEF lines; the receipt retains its own quantities and identifiers.
		jdbc.update("delete from sef_invoice_line where invoice_id = ?", invoiceId);
		var category = jdbc.queryForObject("select category_id from product where id = ?", UUID.class, existingProduct);
		var product = new GoodsReceiptRequest.NewProduct("Novi bicikl", category);
		var full = approve(new GoodsReceiptRequest.Approval(List.of(new GoodsReceiptRequest.Line(line.id(), null, product, new BigDecimal("20"), new BigDecimal("23000"))), r.version()));
		assertThat(full.status()).isEqualTo(ReceiptStatus.RECEIVED);
		UUID newId = full.lines().getFirst().productId();
		assertThat(jdbc.queryForObject("select retail_price from product where id = ?", BigDecimal.class, newId)).isEqualByComparingTo("23000");
		assertThat(full.lines().getFirst().productCode()).isEqualTo(line.productCode());
		assertThat(jdbc.queryForObject("select purchase_price from product where id = ?", BigDecimal.class, newId)).isEqualByComparingTo("10000");
		assertThat(jdbc.queryForObject("select vat_rate from product where id = ?", BigDecimal.class, newId)).isEqualByComparingTo("20");
		assertThat(jdbc.queryForObject("select quantity_on_hand from inventory_item where product_id = ?", BigDecimal.class, newId)).isEqualByComparingTo("20");
	}
	@Test
	@WithMockUser(authorities = {"ROLE_ADMIN", "STOCK_RECEIVE", "SUPPLIER_MANAGE"})
	void priceWriteAuthorityIsRequired() throws Exception {
		var r = start();
		mvc.perform(post("/api/internal/goods-receipts/" + invoiceId + "/approve").contentType("application/json")
				.content(mapper.writeValueAsString(approval(r, 20, "30000")))).andExpect(status().isForbidden());
	}
	@Test
	void concurrentConfirmationsIncreaseStockOnce() throws Exception {
		var r = start(); var before = stock();
		String body = mapper.writeValueAsString(approval(r, 20, "31000"));
		var gate = new java.util.concurrent.CountDownLatch(1);
		try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
			java.util.concurrent.Callable<Integer> task = () -> {
				gate.await();
				return mvc.perform(post("/api/internal/goods-receipts/" + invoiceId + "/approve")
						.with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin")
								.authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"),
										new org.springframework.security.core.authority.SimpleGrantedAuthority("STOCK_RECEIVE"),
										new org.springframework.security.core.authority.SimpleGrantedAuthority("SUPPLIER_MANAGE"),
										new org.springframework.security.core.authority.SimpleGrantedAuthority("PRODUCT_WRITE")))
						.contentType("application/json").content(body)).andReturn().getResponse().getStatus();
			};
			var first = executor.submit(task); var second = executor.submit(task); gate.countDown();
			assertThat(List.of(first.get(10, java.util.concurrent.TimeUnit.SECONDS), second.get(10, java.util.concurrent.TimeUnit.SECONDS)))
					.containsExactlyInAnyOrder(200, 409);
		}
		assertThat(stock()).isEqualByComparingTo(before.add(new BigDecimal("20")));
	}

	@Test
	void receiptUsesSupplierImageForInvoiceItem() throws Exception {
		UUID supplierId = jdbc.queryForObject("select supplier_id from sef_invoice where id = ?", UUID.class, invoiceId);
		jdbc.update("""
				insert into supplier_product (id, supplier_id, supplier_code, supplier_product_name, image_url, active, created_at, version)
				values (?, ?, 'CAP-1', 'Bicikl', 'https://example.com/bike.jpg', true, now(), 0)
				""", UUID.randomUUID(), supplierId);
		assertThat(start().lines().getFirst().imageUrl()).isEqualTo("https://example.com/bike.jpg");
	}

	@Test
	void caprioloInvoiceCanReceivePiecesAndCompleteKitsWithoutExpandingQuantities() throws Exception {
		var invoice = invoices.findById(invoiceId).orElseThrow();
		sourceLines.save(new SefInvoiceLine(UUID.randomUUID(), invoice, 2, "2", "GUR-GUR MAGNESIUM", null, "CAP-2", null, "XKI",
				BigDecimal.ONE, new BigDecimal("3800"), null, null, "S", new BigDecimal("20"), null, null));
		var r = start();
		assertThat(r.lines()).hasSize(2);
		var kit = r.lines().get(1);
		assertThat(kit.unitCode()).isEqualTo("XKI");
		assertThat(kit.expectedQuantity()).isEqualByComparingTo("1");
		var category = jdbc.queryForObject("select category_id from product where id = ?", UUID.class, existingProduct);
		var result = approve(new GoodsReceiptRequest.Approval(List.of(
				approval(r, 20, "25000").lines().getFirst(),
				new GoodsReceiptRequest.Line(kit.id(), null, new GoodsReceiptRequest.NewProduct("GUR-GUR MAGNESIUM", category),
						BigDecimal.ONE, new BigDecimal("5000"))), r.version()));
		assertThat(result.status()).isEqualTo(ReceiptStatus.RECEIVED);
		assertThat(jdbc.queryForObject("select quantity_on_hand from inventory_item where product_id = ?", BigDecimal.class,
				result.lines().get(1).productId())).isEqualByComparingTo("1");
	}

	@Test
	void unsupportedMeasurementExplainsWhichLineNeedsConversion() throws Exception {
		jdbc.update("update sef_invoice_line set unit_code = 'KGM' where invoice_id = ?", invoiceId);
		mvc.perform(post("/api/internal/goods-receipts/" + invoiceId + "/start").contentType("application/json")
				.content(mapper.writeValueAsString(new GoodsReceiptRequest(location))))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RECEIPT_UNIT_INVALID"))
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("KGM")));
	}

	@Test
	void missingPriceIsRejected() throws Exception {
		var r = start();
		mvc.perform(post("/api/internal/goods-receipts/" + invoiceId + "/approve").contentType("application/json")
				.content("{\"version\":0,\"lines\":[{\"lineId\":\"" + r.lines().getFirst().id() + "\",\"productId\":\"" + existingProduct + "\",\"quantity\":20}]}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}
}
