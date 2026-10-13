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
	UUID categoryId(String slug) { return jdbc.queryForObject("select id from category where slug=?", UUID.class, slug); }
	UUID supplierId() { return jdbc.queryForObject("select supplier_id from sef_invoice where id=?", UUID.class, invoiceId); }
	@Test
	void suggestsTyreCategoryButLeavesMissingValveForTheEmployee() throws Exception {
		jdbc.update("update sef_invoice_line set item_name='Unutrašnja guma 29 AV' where invoice_id=?", invoiceId);
		var suggestion = start().lines().getFirst().categorySuggestion();
		assertThat(suggestion.categoryId()).isEqualTo(categoryId("delovi-gume-unutrasnje-29-av"));
		assertThat(suggestion.source()).isEqualTo("NAME_RULE");
		assertThat(suggestion.requiresSelection()).isFalse();
		assertThat(suggestion.categoryPath()).isEqualTo("Delovi / Gume / Unutrašnje / 29″ / AV");
		jdbc.update("update goods_receipt_line set item_name='Unutrašnja guma 29' where receipt_id=(select id from goods_receipt where invoice_id=?)", invoiceId);
		var incomplete = mapper.readValue(mvc.perform(get("/api/internal/goods-receipts/" + invoiceId))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), GoodsReceiptResponse.class).lines().getFirst().categorySuggestion();
		assertThat(incomplete.requiresSelection()).isTrue();
		assertThat(incomplete.categoryId()).isEqualTo(categoryId("delovi-gume-unutrasnje-29"));
	}

	@Test
	void savesConfirmedChoiceAndUsesItAheadOfNameRulesForAnUnlinkedSku() throws Exception {
		UUID supplier = supplierId();
		jdbc.update("update sef_invoice_line set item_name='Lula volana' where invoice_id=?", invoiceId);
		var receipt = start();
		assertThat(receipt.lines().getFirst().categorySuggestion().source()).isEqualTo("NAME_RULE");
		UUID chosen = categoryId("biciklizam-oprema-pumpe");
		approve(new GoodsReceiptRequest.Approval(List.of(new GoodsReceiptRequest.Line(receipt.lines().getFirst().id(), null,
				new GoodsReceiptRequest.NewProduct("Pumpa", chosen), new BigDecimal("20"), new BigDecimal("1500"))), receipt.version()));
		assertThat(jdbc.queryForObject("select category_id from receipt_category_mapping where supplier_id=? and key_type='SUPPLIER_CODE' and source_key='CAP-1'", UUID.class, supplier)).isEqualTo(chosen);
		assertThat(jdbc.queryForObject("select confirmed_by from receipt_category_mapping where supplier_id=? and key_type='SUPPLIER_CODE' and source_key='CAP-1'", String.class, supplier)).isEqualTo("admin");
		// Model a later supplier reimport with no product link or recoverable historical link.
		jdbc.update("update supplier_product set product_id=null where supplier_id=?", supplier);
		jdbc.update("update goods_receipt_line set product_id=null where receipt_id=(select id from goods_receipt where invoice_id=?)", invoiceId);
		anotherInvoice();
		jdbc.update("update sef_invoice_line set item_name='Lula volana' where invoice_id=?", invoiceId);
		var next = start().lines().getFirst();
		assertThat(next.productId()).isNull();
		assertThat(next.categorySuggestion().categoryId()).isEqualTo(chosen);
		assertThat(next.categorySuggestion().source()).isEqualTo("SAVED_SUPPLIER_CODE");
	}

	@Test
	void savedChoicesAreScopedToSupplierAndInactiveCategoriesAreIgnored() throws Exception {
		UUID other = suppliers.save(new Supplier(UUID.randomUUID(), "OTHER-" + UUID.randomUUID(), "Drugi dobavljač", null, null, "RSD", true, Instant.now())).getId();
		UUID pump = categoryId("biciklizam-oprema-pumpe");
		jdbc.update("insert into receipt_category_mapping values (?,'SUPPLIER_CODE','CAP-1',?,'admin',now())", other, pump);
		jdbc.update("update sef_invoice_line set item_name='Lula volana' where invoice_id=?", invoiceId);
		var receipt = start();
		assertThat(receipt.lines().getFirst().categorySuggestion().source()).isEqualTo("NAME_RULE");
		jdbc.update("insert into receipt_category_mapping values (?,'SUPPLIER_CODE','CAP-1',?,'admin',now())", supplierId(), categoryId("fitnes"));
		var refreshed = mapper.readValue(mvc.perform(get("/api/internal/goods-receipts/" + invoiceId))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), GoodsReceiptResponse.class);
		assertThat(refreshed.lines().getFirst().categorySuggestion().categoryId()).isEqualTo(categoryId("biciklizam-delovi-lule-i-adapteri"));
	}

	@Test
	void suggestsSavedBarcodeAndLearnsSupplierGroupsOnlyWithExplicitSelection() throws Exception {
		UUID supplier = supplierId();
		UUID supplierProduct = seedSupplierCatalog(null);
		UUID pump = categoryId("biciklizam-oprema-pumpe");
		jdbc.update("update supplier_product set supplier_barcode='00012345', category_text='Oprema dobavljača' where id=?", supplierProduct);
		jdbc.update("insert into receipt_category_mapping values (?,'BARCODE','00012345',?,'admin',now())", supplier, pump);
		var receipt = start();
		assertThat(receipt.lines().getFirst().categorySuggestion().source()).isEqualTo("SAVED_BARCODE");
		assertThat(receipt.lines().getFirst().categorySuggestion().categoryId()).isEqualTo(pump);
		assertThat(jdbc.queryForObject("select count(*) from receipt_category_mapping where supplier_id=? and key_type='SUPPLIER_CATEGORY'", Integer.class, supplier)).isZero();
		approve(new GoodsReceiptRequest.Approval(List.of(new GoodsReceiptRequest.Line(receipt.lines().getFirst().id(), null,
				new GoodsReceiptRequest.NewProduct("Pumpa", pump), new BigDecimal("20"), new BigDecimal("1500"), true)), receipt.version()));
		assertThat(jdbc.queryForObject("select category_id from receipt_category_mapping where supplier_id=? and key_type='SUPPLIER_CATEGORY' and source_key='oprema dobavljaca'", UUID.class, supplier)).isEqualTo(pump);
		anotherInvoice();
		jdbc.update("update sef_invoice_line set seller_item_id='CAP-OTHER' where invoice_id=?", invoiceId);
		jdbc.update("insert into supplier_product(id,supplier_id,supplier_code,supplier_product_name,category_text,active,created_at,version) values (?,?,'CAP-OTHER','Nepoznato','OPREMA DOBAVLJAČA',true,now(),0)", UUID.randomUUID(), supplier);
		var next = start().lines().getFirst();
		assertThat(next.productId()).isNull();
		assertThat(next.categorySuggestion().source()).isEqualTo("SAVED_SUPPLIER_CATEGORY");
		assertThat(next.categorySuggestion().categoryId()).isEqualTo(pump);
	}

	@Test
	void confirmingExistingProductKeepsItsCategoryAndDoesNotLearnABroadSupplierGroup() throws Exception {
		UUID supplierProduct = seedSupplierCatalog(existingProduct);
		jdbc.update("update supplier_product set category_text='Široka grupa dobavljača' where id=?", supplierProduct);
		var receipt = start();
		UUID original = jdbc.queryForObject("select category_id from product where id=?", UUID.class, existingProduct);
		assertThat(receipt.lines().getFirst().categorySuggestion().source()).isEqualTo("EXISTING_PRODUCT");
		assertThat(receipt.lines().getFirst().categorySuggestion().categoryId()).isEqualTo(original);
		approve(approval(receipt, 20, "25000"));
		assertThat(jdbc.queryForObject("select category_id from product where id=?", UUID.class, existingProduct)).isEqualTo(original);
		assertThat(jdbc.queryForObject("select count(*) from receipt_category_mapping where supplier_id=? and key_type='SUPPLIER_CATEGORY'", Integer.class, supplierId())).isZero();
	}

	@Test
	void failedBatchDoesNotLearnCategoriesOrCreateProducts() throws Exception {
		var invoice = invoices.findById(invoiceId).orElseThrow();
		sourceLines.save(new SefInvoiceLine(UUID.randomUUID(), invoice, 2, "2", "Nepoznata stavka", null, "CAP-2", null, "H87",
				new BigDecimal("2"), new BigDecimal("100"), null, null, "S", new BigDecimal("20"), null, null));
		var receipt = start();
		int count = jdbc.queryForObject("select count(*) from product", Integer.class);
		var n = new GoodsReceiptRequest.NewProduct("Novi artikal", categoryId("biciklizam-oprema-pumpe"));
		var batch = new GoodsReceiptRequest.Approval(List.of(
				new GoodsReceiptRequest.Line(receipt.lines().get(0).id(), null, n, new BigDecimal("20"), new BigDecimal("200")),
				new GoodsReceiptRequest.Line(receipt.lines().get(1).id(), null, n, new BigDecimal("3"), new BigDecimal("200"))), receipt.version());
		mvc.perform(post("/api/internal/goods-receipts/" + invoiceId + "/approve").contentType("application/json").content(mapper.writeValueAsString(batch)))
				.andExpect(status().isConflict());
		assertThat(jdbc.queryForObject("select count(*) from receipt_category_mapping where supplier_id=?", Integer.class, supplierId())).isZero();
		assertThat(jdbc.queryForObject("select count(*) from product", Integer.class)).isEqualTo(count);
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
	UUID seedSupplierCatalog(UUID linkedProduct) {
		UUID supplierId = jdbc.queryForObject("select supplier_id from sef_invoice where id=?", UUID.class, invoiceId);
		UUID supplierProductId = UUID.randomUUID();
		UUID imageId = UUID.randomUUID();
		jdbc.update("insert into supplier_product(id,supplier_id,product_id,supplier_code,supplier_product_name,brand_name,description,active,created_at,version) values (?,?,?,'CAP-1','Bicikl M crveni','Test brand','Opis dobavljača',true,now(),0)", supplierProductId, supplierId, linkedProduct);
		jdbc.update("insert into supplier_catalog_image(id,supplier_id,sha256,content_type,source_url,content,captured_at) values (?,?,'receipt-test-image','image/jpeg','https://supplier.example/bike.jpg',?,now())", imageId, supplierId, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});
		jdbc.update("insert into supplier_catalog_product(supplier_product_id,model_code,source_url,metadata,captured_at,stock_date) values (?,'CAP','https://supplier.example/bike',?::jsonb,now(),current_date)", supplierProductId, "{\"imageIds\":[\"" + imageId + "\"]}");
		return supplierProductId;
	}
	void anotherInvoice() {
		var previous = invoices.findById(invoiceId).orElseThrow();
		var supplier = suppliers.findById(jdbc.queryForObject("select supplier_id from sef_invoice where id=?", UUID.class, invoiceId)).orElseThrow();
		var connection = connections.findById(previous.getConnection().getId()).orElseThrow();
		var now = Instant.now();
		var invoice = new SefInvoice(UUID.randomUUID(), connection, 2L, SefInvoiceDirection.PURCHASE, now);
		invoice.applyPurchaseOverview(supplier, null, null, "TEST-21", "380", "Approved", supplier.getName(), null, null,
				"RSD", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, now, now, null, now);
		invoices.save(invoice); invoiceId = invoice.getId();
		sourceLines.save(new SefInvoiceLine(UUID.randomUUID(), invoice, 1, "1", "Bicikl", null, "CAP-1", null, "H87",
				new BigDecimal("20"), new BigDecimal("10000"), null, null, "S", new BigDecimal("20"), null, null));
	}
	@Test
	void supplierSkuAutomaticallySelectsExistingProductAndCopiesItsSavedImage() throws Exception {
		UUID supplierProductId = seedSupplierCatalog(existingProduct);
		int productCount = jdbc.queryForObject("select count(*) from product", Integer.class);
		var before = stock();
		var receipt = start();
		assertThat(receipt.lines().getFirst().productId()).isEqualTo(existingProduct);
		assertThat(receipt.lines().getFirst().imageUrl()).startsWith("/api/internal/supplier-catalog-images/");
		var received = approve(approval(receipt, 20, "25000"));
		assertThat(stock()).isEqualByComparingTo(before.add(new BigDecimal("20")));
		assertThat(jdbc.queryForObject("select count(*) from product", Integer.class)).isEqualTo(productCount);
		assertThat(jdbc.queryForObject("select product_id from supplier_product where id=?", UUID.class, supplierProductId)).isEqualTo(existingProduct);
		assertThat(received.lines().getFirst().imageUrl()).startsWith("/api/internal/products/" + existingProduct + "/images/");
		assertThat(jdbc.queryForObject("select count(*) from product_image where product_id=?", Integer.class, existingProduct)).isPositive();
	}
	@Test
	void newProductKeepsImagesIndependentlyAndTheNextInvoiceReusesItsSupplierSku() throws Exception {
		UUID supplierProductId = seedSupplierCatalog(null);
		var r = start();
		var category = jdbc.queryForObject("select category_id from product where id=?", UUID.class, existingProduct);
		var newProduct = new GoodsReceiptRequest.NewProduct("Novi bicikl", category);
		var received = approve(new GoodsReceiptRequest.Approval(List.of(new GoodsReceiptRequest.Line(r.lines().getFirst().id(), null, newProduct, new BigDecimal("20"), new BigDecimal("23000"))), r.version()));
		UUID productId = received.lines().getFirst().productId();
		String imageUrl = received.lines().getFirst().imageUrl();
		assertThat(jdbc.queryForObject("select product_id from supplier_product where id=?", UUID.class, supplierProductId)).isEqualTo(productId);
		assertThat(jdbc.queryForObject("select purchase_price from product where id=?", BigDecimal.class, productId)).isEqualByComparingTo("10000");
		assertThat(jdbc.queryForObject("select brand_name from product where id=?", String.class, productId)).isEqualTo("Test brand");
		assertThat(jdbc.queryForObject("select description from product where id=?", String.class, productId)).isEqualTo("Opis dobavljača");
		int productCount = jdbc.queryForObject("select count(*) from product", Integer.class);
		anotherInvoice();
		var next = start();
		assertThat(next.lines().getFirst().productId()).isEqualTo(productId);
		// A stale client proposing a new product must still reuse the established SKU link.
		approve(new GoodsReceiptRequest.Approval(List.of(new GoodsReceiptRequest.Line(next.lines().getFirst().id(), null, newProduct, new BigDecimal("20"), new BigDecimal("24000"))), next.version()));
		assertThat(jdbc.queryForObject("select count(*) from product", Integer.class)).isEqualTo(productCount);
		assertThat(jdbc.queryForObject("select quantity_on_hand from inventory_item where product_id=?", BigDecimal.class, productId)).isEqualByComparingTo("40");
		assertThat(jdbc.queryForObject("select count(*) from product_image where product_id=?", Integer.class, productId)).isEqualTo(1);
		jdbc.update("delete from supplier_catalog_product where supplier_product_id=?", supplierProductId);
		jdbc.update("delete from supplier_catalog_image where supplier_id=(select supplier_id from supplier_product where id=?)", supplierProductId);
		var reader = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("viewer")
				.authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_EMPLOYEE"), new org.springframework.security.core.authority.SimpleGrantedAuthority("PRODUCT_READ"));
		mvc.perform(get(imageUrl).with(reader)).andExpect(status().isOk()).andExpect(content().contentType("image/jpeg"))
				.andExpect(content().bytes(new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff}));
		mvc.perform(get("/api/internal/products/" + productId).with(reader)).andExpect(status().isOk())
				.andExpect(jsonPath("$.imageIds.length()").value(1))
				.andExpect(jsonPath("$.suppliers.length()").value(1))
				.andExpect(jsonPath("$.suppliers[0].name").value("Kapriolo test"));
		mvc.perform(get(imageUrl)).andExpect(status().isForbidden());
		mvc.perform(get(imageUrl.replace(productId.toString(), existingProduct.toString())).with(reader)).andExpect(status().isNotFound());
	}
	@Test
	void repeatedSupplierSkuInOneInvoiceCreatesOneProductAndAddsBothQuantities() throws Exception {
		seedSupplierCatalog(null);
		assertRepeatedSkuCreatesOneProduct();
	}
	@Test
	void unknownSupplierSkuIsLinkedForBothLinesWithinTheSameConfirmation() throws Exception {
		assertRepeatedSkuCreatesOneProduct();
	}
	void assertRepeatedSkuCreatesOneProduct() throws Exception {
		var invoice = invoices.findById(invoiceId).orElseThrow();
		sourceLines.save(new SefInvoiceLine(UUID.randomUUID(), invoice, 2, "2", "Bicikl", null, "CAP-1", null, "H87",
				new BigDecimal("2"), new BigDecimal("10000"), null, null, "S", new BigDecimal("20"), null, null));
		var r = start();
		var category = jdbc.queryForObject("select category_id from product where id=?", UUID.class, existingProduct);
		var newProduct = new GoodsReceiptRequest.NewProduct("Bicikl", category);
		int before = jdbc.queryForObject("select count(*) from product", Integer.class);
		var received = approve(new GoodsReceiptRequest.Approval(r.lines().stream().map(l -> new GoodsReceiptRequest.Line(l.id(), null, newProduct, l.expectedQuantity().setScale(0), new BigDecimal("23000"))).toList(), r.version()));
		assertThat(received.lines()).extracting(GoodsReceiptResponse.Line::productId).containsOnly(received.lines().getFirst().productId());
		assertThat(jdbc.queryForObject("select count(*) from product", Integer.class)).isEqualTo(before + 1);
		assertThat(jdbc.queryForObject("select quantity_on_hand from inventory_item where product_id=?", BigDecimal.class, received.lines().getFirst().productId())).isEqualByComparingTo("22");
	}
	@Test
	void supplierSkuFromAnotherSupplierCannotSelectOurProduct() throws Exception {
		var now = Instant.now();
		var other = suppliers.save(new Supplier(UUID.randomUUID(), "OTHER-" + UUID.randomUUID(), "Drugi dobavljač", null, null, "RSD", true, now));
		jdbc.update("insert into supplier_product(id,supplier_id,product_id,supplier_code,supplier_product_name,active,created_at,version) values (?,?,?,'CAP-1','Drugi bicikl',true,now(),0)", UUID.randomUUID(), other.getId(), existingProduct);
		assertThat(start().lines().getFirst().productId()).isNull();
	}
	@Test
	void cannotOverrideAnEstablishedSupplierSkuLink() throws Exception {
		seedSupplierCatalog(existingProduct);
		var r = start(); var before = stock();
		var differentProduct = UUID.fromString("50000000-0000-0000-0000-000000000002");
		var request = new GoodsReceiptRequest.Approval(List.of(new GoodsReceiptRequest.Line(r.lines().getFirst().id(), differentProduct, null, new BigDecimal("20"), new BigDecimal("25000"))), r.version());
		mvc.perform(post("/api/internal/goods-receipts/" + invoiceId + "/approve").contentType("application/json").content(mapper.writeValueAsString(request))).andExpect(status().isConflict());
		assertThat(stock()).isEqualByComparingTo(before);
		assertThat(jdbc.queryForObject("select count(*) from stock_movement where receipt_line_id=?", Integer.class, r.lines().getFirst().id())).isZero();
	}
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
