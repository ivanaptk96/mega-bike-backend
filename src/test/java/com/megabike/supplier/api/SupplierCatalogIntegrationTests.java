package com.megabike.supplier.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(authorities = {"ROLE_ADMIN", "SUPPLIER_MANAGE"})
class SupplierCatalogIntegrationTests {

	@Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");
	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry r) {
		r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		r.add("spring.datasource.username", POSTGRES::getUsername);
		r.add("spring.datasource.password", POSTGRES::getPassword);
		r.add("spring.liquibase.change-log", () -> "classpath:/db/changelog/db.changelog-dev.xml");
		r.add("spring.docker.compose.enabled", () -> "false");
		r.add("megabike.security.jwt.secret", () -> "supplier-catalog-test-secret-that-is-long-enough");
	}
	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired jakarta.persistence.EntityManager entityManager;
	final UUID supplierId = UUID.fromString("80000000-0000-0000-0000-000000000001");
	UUID productId;
	UUID imageId;
	@BeforeEach
	void seedCatalog() {
		productId = UUID.randomUUID();
		imageId = UUID.randomUUID();
		jdbc.update("insert into supplier_product(id,supplier_id,supplier_code,supplier_product_name,brand_name,description,image_url,active,created_at,version) values (?,?,'BIKE-M-RED','Bicycle M Red','Brand','Saved description','https://supplier.example/bike.jpg',true,now(),0)", productId, supplierId);
		jdbc.update("insert into supplier_catalog_product(supplier_product_id,model_code,source_url,metadata,captured_at,stock_date) values (?,'BIKE','https://supplier.example/bike',?::jsonb,now(),'2026-10-11')", productId,
				"{\"supplierCode\":\"BIKE-M-RED\",\"name\":\"Bicycle M Red\",\"description\":\"Full description\",\"specifications\":[{\"label\":\"Size\",\"value\":\"M\"}],\"imageIds\":[\"" + imageId + "\"],\"rawData\":{\"model\":{\"sku\":\"BIKE\"}}}");
		jdbc.update("insert into supplier_catalog_image(id,supplier_id,sha256,content_type,source_url,content,captured_at) values (?,?,'test-sha','image/jpeg','https://supplier.example/bike.jpg',?,now())", imageId, supplierId, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});
	}
	void importStock(String filename, String rows) throws Exception {
		mvc.perform(multipart("/api/internal/suppliers/" + supplierId + "/imports")
				.file(new MockMultipartFile("file", filename, "text/csv", rows.getBytes(StandardCharsets.UTF_8))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.issuesCount").value(0));
		// Requests normally commit separately; this test wraps them in one transaction.
		entityManager.flush();
	}
	@Test
	void showsExactCsvStockPreservesCatalogMetadataAndDoesNotPostOwnStock() throws Exception {
		int productsBefore = jdbc.queryForObject("select count(*) from product", Integer.class);
		int stockBefore = jdbc.queryForObject("select count(*) from stock_movement", Integer.class);
		importStock("Dnevni_lager_Bicikli_12.10.2026.csv", "Šifra;ARTIKAL;Pakovanje;KOL NA;VPCena u RSD sa ugovorenim rabatom\nBIKE-M-RED;Bicycle M Red;1;33;17954.72\n");
		mvc.perform(get("/api/internal/suppliers/" + supplierId + "/catalog"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].availableQuantity").value(33))
				.andExpect(jsonPath("$.content[0].purchasePrice").value(17954.72))
				.andExpect(jsonPath("$.content[0].vatIncluded").value(false))
				.andExpect(jsonPath("$.content[0].stockDate").value("2026-10-12"))
				.andExpect(jsonPath("$.content[0].rawData").doesNotExist());
		assertThat(jdbc.queryForObject("select description from supplier_product where id=?", String.class, productId)).isEqualTo("Saved description");
		assertThat(jdbc.queryForObject("select image_url from supplier_product where id=?", String.class, productId)).isEqualTo("https://supplier.example/bike.jpg");
		assertThat(jdbc.queryForObject("select count(*) from product", Integer.class)).isEqualTo(productsBefore);
		assertThat(jdbc.queryForObject("select count(*) from stock_movement", Integer.class)).isEqualTo(stockBefore);
		assertThat(jdbc.queryForObject("select product_id from supplier_product where id=?", UUID.class, productId)).isNull();
	}
	@Test
	void supportsTheRealSefSupplierIdentifiedByVatNumber() throws Exception {
		jdbc.update("update supplier set code='SEF_100116685',vat_registration_number='100116685' where id=?", supplierId);
		importStock("stock.csv", "supplier_code;name;price;stock\nBIKE-M-RED;Bicycle M Red;100;4\n");
		mvc.perform(get("/api/internal/suppliers/" + supplierId + "/catalog"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content[0].availableQuantity").value(4));
	}
	@Test
	void unknownQuantityDoesNotCountAsAvailableAndFiltersBySku() throws Exception {
		mvc.perform(get("/api/internal/suppliers/" + supplierId + "/catalog").param("availableOnly", "true"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
		mvc.perform(get("/api/internal/suppliers/" + supplierId + "/catalog").param("query", "M-RED"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].availableQuantity").isEmpty());
	}

	UUID additionalCatalogProduct(String code, String name) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into supplier_product(id,supplier_id,supplier_code,supplier_product_name,active,created_at,version) values (?,?,?, ?,true,now(),0)", id, supplierId, code, name);
		jdbc.update("insert into supplier_catalog_product(supplier_product_id,model_code,source_url,metadata,captured_at,stock_date) values (?,'BIKE','https://supplier.example/bike',?::jsonb,now(),'2026-10-11')", id,
				"{\"supplierCode\":\"" + code + "\",\"name\":\"" + name + "\",\"imageIds\":[]}");
		return id;
	}

	@Test
	void separatesAvailableUnavailableAndUnknownStockAndKeepsGlobalDatesForEmptySearches() throws Exception {
		additionalCatalogProduct("BIKE-L-BLUE", "Bicycle L Blue");
		additionalCatalogProduct("BIKE-S-BLACK", "Bicycle S Black");
		importStock("Dnevni_lager_Bicikli_12.10.2026.csv", "supplier_code;name;price;stock\nBIKE-M-RED;Bicycle M Red;100;33\nBIKE-L-BLUE;Bicycle L Blue;110;0\n");
		mvc.perform(get("/api/internal/suppliers/" + supplierId + "/catalog/summary"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.totalVariants").value(3))
				.andExpect(jsonPath("$.inStockVariants").value(1)).andExpect(jsonPath("$.unavailableVariants").value(1))
				.andExpect(jsonPath("$.unknownStockVariants").value(1)).andExpect(jsonPath("$.unitsAvailable").value(33))
				.andExpect(jsonPath("$.stockDate").value("2026-10-12"))
				.andExpect(jsonPath("$.stockFilename").value("Dnevni_lager_Bicikli_12.10.2026.csv"))
				.andExpect(jsonPath("$.catalogCapturedAt").isNotEmpty()).andExpect(jsonPath("$.stockCapturedAt").isNotEmpty());
		for (String filter : java.util.List.of("AVAILABLE", "OUT_OF_STOCK", "UNKNOWN"))
			mvc.perform(get("/api/internal/suppliers/" + supplierId + "/catalog").param("availability", filter))
					.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
		mvc.perform(get("/api/internal/suppliers/" + supplierId + "/catalog").param("query", "nothing-matches"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
		mvc.perform(get("/api/internal/suppliers/" + supplierId + "/catalog/summary"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.stockDate").value("2026-10-12"));
	}

	@Test
	void catalogWithoutStockHasUnknownAvailabilityAndNoInventedStockDate() throws Exception {
		mvc.perform(get("/api/internal/suppliers/" + supplierId + "/catalog/summary"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.totalVariants").value(1))
				.andExpect(jsonPath("$.unknownStockVariants").value(1)).andExpect(jsonPath("$.unitsAvailable").value(0))
				.andExpect(jsonPath("$.stockDate").isEmpty()).andExpect(jsonPath("$.stockCapturedAt").isEmpty());
	}
	@Test
	void retainsRawDetailsAndServesSavedImageWithoutSupplierSession() throws Exception {
		mvc.perform(get("/api/internal/supplier-catalog-products/" + productId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.rawData.model.sku").value("BIKE"));
		mvc.perform(get("/api/internal/supplier-catalog-images/" + imageId)).andExpect(status().isOk())
				.andExpect(content().contentType("image/jpeg")).andExpect(content().bytes(new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff}));
	}
	@Test
	void publicCatalogKeepsRetailPricesSeparateFromPurchasePricesAndStock() throws Exception {
		jdbc.update("update supplier_catalog_product set stock_date=null, metadata=metadata || ?::jsonb where supplier_product_id=?",
				"{\"referenceRetailPrice\":42999,\"referencePriceCurrency\":\"RSD\",\"referencePriceCapturedAt\":\"2026-10-12T09:00:00Z\",\"catalogSource\":\"PUBLIC_WEBSITE\"}", productId);
		mvc.perform(get("/api/internal/suppliers/" + supplierId + "/catalog"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content[0].referenceRetailPrice").value(42999))
				.andExpect(jsonPath("$.content[0].purchasePrice").isEmpty())
				.andExpect(jsonPath("$.content[0].availableQuantity").isEmpty())
				.andExpect(jsonPath("$.content[0].stockDate").isEmpty());
		mvc.perform(get("/api/internal/suppliers/" + supplierId + "/catalog/summary"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.totalVariants").value(1))
				.andExpect(jsonPath("$.inStockVariants").value(0)).andExpect(jsonPath("$.stockDate").isEmpty());
	}
	@Test
	@WithMockUser(authorities = {"ROLE_EMPLOYEE", "PRODUCT_READ"})
	void requiresSupplierPermissionForCatalogDetailsAndImages() throws Exception {
		mvc.perform(get("/api/internal/suppliers/" + supplierId + "/catalog")).andExpect(status().isForbidden());
		mvc.perform(get("/api/internal/supplier-catalog-products/" + productId)).andExpect(status().isForbidden());
		mvc.perform(get("/api/internal/supplier-catalog-images/" + imageId)).andExpect(status().isForbidden());
		mvc.perform(get("/api/internal/suppliers/" + supplierId + "/catalog/summary")).andExpect(status().isForbidden());
	}
	@Test
	void validatesPaginationAndMissingSupplier() throws Exception {
		mvc.perform(get("/api/internal/suppliers/" + supplierId + "/catalog").param("size", "101")).andExpect(status().isBadRequest());
		mvc.perform(get("/api/internal/suppliers/" + UUID.randomUUID() + "/catalog")).andExpect(status().isNotFound());
		mvc.perform(get("/api/internal/suppliers/" + supplierId + "/catalog").param("availability", "WRONG")).andExpect(status().isBadRequest());
		mvc.perform(get("/api/internal/suppliers/" + UUID.randomUUID() + "/catalog/summary")).andExpect(status().isNotFound());
	}
}
