package com.megabike.supplier.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.megabike.identity.api.LoginRequest;
import com.megabike.identity.api.LoginResponse;
import com.megabike.shared.api.ErrorResponse;
import com.megabike.supplier.domain.SupplierClassification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SupplierIntegrationTests {

	private static final UUID PLANET_BIKE_ID = UUID.fromString("80000000-0000-0000-0000-000000000001");
	private static final UUID DEV_PRODUCT_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
			.withDatabaseName("megabike_supplier_test")
			.withUsername("megabike")
			.withPassword("megabike");

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.liquibase.change-log", () -> "classpath:/db/changelog/db.changelog-dev.xml");
		registry.add("megabike.security.jwt.secret", () -> "supplier-integration-test-secret-that-is-long-enough");
	}

	@LocalServerPort
	int port;

	@Autowired
	ObjectMapper objectMapper;

	HttpClient httpClient;

	@BeforeEach
	void setUp() {
		httpClient = HttpClient.newHttpClient();
	}

	@Test
	void managerCanReadPlanetBikeSupplier() {
		String token = login("manager@megabike.local", "password").accessToken();

		TestResponse<SupplierResponse> supplier = get("/api/internal/suppliers/" + PLANET_BIKE_ID, token, SupplierResponse.class);

		assertThat(supplier.status()).isEqualTo(HttpStatus.OK.value());
		assertThat(supplier.body().code()).isEqualTo("PLANET_BIKE");
		assertThat(supplier.body().defaultCurrency()).isEqualTo("RSD");
	}

	@Test
	void managerCanClassifySupplier() {
		String token = login("manager@megabike.local", "password").accessToken();

		TestResponse<SupplierResponse> response = put(
				"/api/internal/suppliers/" + PLANET_BIKE_ID + "/classification",
				new SupplierClassificationRequest(SupplierClassification.MERCHANDISE),
				token,
				SupplierResponse.class
		);

		assertThat(response.status()).isEqualTo(HttpStatus.OK.value());
		assertThat(response.body().classification()).isEqualTo(SupplierClassification.MERCHANDISE);

		TestResponse<SupplierResponse[]> merchandise = get(
				"/api/internal/suppliers?classification=MERCHANDISE",
				token,
				SupplierResponse[].class
		);
		assertThat(merchandise.status()).isEqualTo(HttpStatus.OK.value());
		assertThat(merchandise.body())
				.extracting(SupplierResponse::id)
				.contains(PLANET_BIKE_ID);
	}

	@Test
	void employeeCannotReadSuppliers() {
		String token = login("employee@megabike.local", "password").accessToken();

		TestResponse<ErrorResponse> response = get("/api/internal/suppliers", token, ErrorResponse.class);

		assertThat(response.status()).isEqualTo(HttpStatus.FORBIDDEN.value());
		assertThat(response.body().code()).isEqualTo("ACCESS_DENIED");
	}

	@Test
	void managerCanCreateAndUpdateSupplierProduct() {
		String token = login("manager@megabike.local", "password").accessToken();
		String supplierCode = "PB-" + UUID.randomUUID();

		TestResponse<SupplierProductResponse> create = post(
				"/api/internal/supplier-products",
				new SupplierProductRequest(PLANET_BIKE_ID, DEV_PRODUCT_ID, supplierCode, "8600000000011", "Planet Bike Test Product", "PB", "Parts", null, null, true),
				token,
				SupplierProductResponse.class
		);

		assertThat(create.status()).isEqualTo(HttpStatus.OK.value());
		assertThat(create.body().supplierCode()).isEqualTo(supplierCode);
		assertThat(create.body().productId()).isEqualTo(DEV_PRODUCT_ID);

		TestResponse<SupplierProductPageResponse> list = get("/api/internal/supplier-products?supplierId=" + PLANET_BIKE_ID + "&query=" + supplierCode, token, SupplierProductPageResponse.class);
		assertThat(list.status()).isEqualTo(HttpStatus.OK.value());
		assertThat(list.body().content()).extracting(SupplierProductResponse::id).contains(create.body().id());

		TestResponse<SupplierProductResponse> update = put(
				"/api/internal/supplier-products/" + create.body().id(),
				new SupplierProductRequest(PLANET_BIKE_ID, DEV_PRODUCT_ID, supplierCode, "8600000000011", "Planet Bike Test Product Updated", "PB", "Parts", "Updated", "https://planetbike.rs/image.jpg", true),
				token,
				SupplierProductResponse.class
		);

		assertThat(update.status()).isEqualTo(HttpStatus.OK.value());
		assertThat(update.body().supplierProductName()).isEqualTo("Planet Bike Test Product Updated");
	}

	@Test
	void managerCanImportPlanetBikeCsv() {
		String token = login("manager@megabike.local", "password").accessToken();
		String supplierCode = "PB-CSV-" + UUID.randomUUID();
		String csv = """
				sifra;naziv;barkod;brend;kategorija;cena;stanje;opis;slika_url
				%s;Planet Bike CSV Product;8600000000999;Planet Bike;Bikes;1.234,56;3;Opis proizvoda;https://planetbike.rs/product.jpg
				;Missing Code Product;;;;999,00;;;
				""".formatted(supplierCode);

		TestResponse<SupplierImportRunResponse> importRun = multipart(
				"/api/internal/suppliers/" + PLANET_BIKE_ID + "/imports",
				"lager-lista.csv",
				csv,
				token,
				SupplierImportRunResponse.class
		);

		assertThat(importRun.status()).isEqualTo(HttpStatus.OK.value());
		assertThat(importRun.body().totalRows()).isEqualTo(2);
		assertThat(importRun.body().createdSupplierProducts()).isEqualTo(1);
		assertThat(importRun.body().priceSnapshots()).isEqualTo(1);
		assertThat(importRun.body().issuesCount()).isEqualTo(1);

		TestResponse<SupplierProductPageResponse> products = get("/api/internal/supplier-products?supplierId=" + PLANET_BIKE_ID + "&query=" + supplierCode, token, SupplierProductPageResponse.class);
		assertThat(products.status()).isEqualTo(HttpStatus.OK.value());
		assertThat(products.body().content()).hasSize(1);
		assertThat(products.body().content().getFirst().supplierProductName()).isEqualTo("Planet Bike CSV Product");

		TestResponse<SupplierImportIssueResponse[]> issues = get("/api/internal/supplier-imports/" + importRun.body().id() + "/issues", token, SupplierImportIssueResponse[].class);
		assertThat(issues.status()).isEqualTo(HttpStatus.OK.value());
		assertThat(issues.body()).extracting(SupplierImportIssueResponse::code).contains("SUPPLIER_IMPORT_REQUIRED_FIELDS_MISSING");
	}

	private LoginResponse login(String email, String password) {
		return post("/api/auth/login", new LoginRequest(email, password), null, LoginResponse.class).body();
	}

	private <T> TestResponse<T> get(String path, String accessToken, Class<T> responseType) {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url(path)))
				.header("Authorization", "Bearer " + accessToken)
				.GET()
				.build();
		return exchange(request, responseType);
	}

	private <T> TestResponse<T> post(String path, Object body, String accessToken, Class<T> responseType) {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url(path)))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(writeJson(body)));
		if (accessToken != null) {
			request.header("Authorization", "Bearer " + accessToken);
		}
		return exchange(request.build(), responseType);
	}

	private <T> TestResponse<T> put(String path, Object body, String accessToken, Class<T> responseType) {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url(path)))
				.header("Content-Type", "application/json")
				.header("Authorization", "Bearer " + accessToken)
				.PUT(HttpRequest.BodyPublishers.ofString(writeJson(body)))
				.build();
		return exchange(request, responseType);
	}

	private <T> TestResponse<T> multipart(String path, String filename, String content, String accessToken, Class<T> responseType) {
		String boundary = "----MegaBikeBoundary" + UUID.randomUUID();
		String body = "--" + boundary + "\r\n"
				+ "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
				+ "Content-Type: text/csv\r\n\r\n"
				+ content
				+ "\r\n--" + boundary + "--\r\n";
		HttpRequest request = HttpRequest.newBuilder(URI.create(url(path)))
				.header("Authorization", "Bearer " + accessToken)
				.header("Content-Type", "multipart/form-data; boundary=" + boundary)
				.POST(HttpRequest.BodyPublishers.ofByteArray(body.getBytes(StandardCharsets.UTF_8)))
				.build();
		return exchange(request, responseType);
	}

	private <T> TestResponse<T> exchange(HttpRequest request, Class<T> responseType) {
		try {
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			return new TestResponse<>(response.statusCode(), readJson(response.body(), responseType));
		} catch (Exception exception) {
			throw new IllegalStateException("HTTP request failed", exception);
		}
	}

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (Exception exception) {
			throw new IllegalStateException("Unable to write JSON", exception);
		}
	}

	private <T> T readJson(String json, Class<T> responseType) {
		try {
			return objectMapper.readValue(json, responseType);
		} catch (Exception exception) {
			throw new IllegalStateException("Unable to read JSON: " + json, exception);
		}
	}

	private String url(String path) {
		return "http://127.0.0.1:" + port + path;
	}

	private record TestResponse<T>(int status, T body) {
	}
}
