package com.megabike.catalog.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.megabike.catalog.domain.ProductUnit;
import com.megabike.identity.api.LoginRequest;
import com.megabike.identity.api.LoginResponse;
import com.megabike.shared.api.ErrorResponse;
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

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductIntegrationTests {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
			.withDatabaseName("megabike_product_test")
			.withUsername("megabike")
			.withPassword("megabike");

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.liquibase.change-log", () -> "classpath:/db/changelog/db.changelog-dev.xml");
		registry.add("megabike.security.jwt.secret", () -> "product-integration-test-secret-that-is-long-enough");
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
	void adminCanCreateReadSearchAndUpdateProduct() {
		String token = login("admin@megabike.local", "password").accessToken();
		CategoryResponse category = createCategory(token, "Product Test Category");
		ProductRequest request = productRequest(category.id(), "MB-T-" + UUID.randomUUID(), "860" + System.nanoTime(), "Trail Pump");

		TestResponse<ProductResponse> create = post("/api/internal/products", request, token, ProductResponse.class);

		assertThat(create.status()).isEqualTo(HttpStatus.OK.value());
		assertThat(create.body().id()).isNotNull();
		assertThat(create.body().categoryId()).isEqualTo(category.id());
		assertThat(create.body().retailPriceIncludesVat()).isTrue();

		TestResponse<ProductResponse> get = get("/api/internal/products/" + create.body().id(), token, ProductResponse.class);
		assertThat(get.status()).isEqualTo(HttpStatus.OK.value());
		assertThat(get.body().name()).isEqualTo("Trail Pump");

		TestResponse<ProductResponse[]> search = get("/api/internal/products?query=trail&active=true", token, ProductResponse[].class);
		assertThat(search.status()).isEqualTo(HttpStatus.OK.value());
		assertThat(search.body()).extracting(ProductResponse::id).contains(create.body().id());

		ProductRequest updateRequest = productRequest(category.id(), request.productCode(), request.barcode(), "Workshop Trail Pump");
		TestResponse<ProductResponse> update = put("/api/internal/products/" + create.body().id(), updateRequest, token, ProductResponse.class);

		assertThat(update.status()).isEqualTo(HttpStatus.OK.value());
		assertThat(update.body().name()).isEqualTo("Workshop Trail Pump");
		assertThat(update.body().updatedAt()).isNotNull();
	}

	@Test
	void employeeCanListButCannotCreateProduct() {
		String token = login("employee@megabike.local", "password").accessToken();

		TestResponse<ProductResponse[]> list = get("/api/internal/products", token, ProductResponse[].class);
		assertThat(list.status()).isEqualTo(HttpStatus.OK.value());
		assertThat(list.body()).isNotEmpty();

		TestResponse<ErrorResponse> create = post(
				"/api/internal/products",
				productRequest(UUID.fromString("40000000-0000-0000-0000-000000000001"), "MB-F-" + UUID.randomUUID(), null, "Forbidden"),
				token,
				ErrorResponse.class
		);

		assertThat(create.status()).isEqualTo(HttpStatus.FORBIDDEN.value());
		assertThat(create.body().code()).isEqualTo("ACCESS_DENIED");
	}

	@Test
	void createRejectsInvalidRequest() {
		String token = login("admin@megabike.local", "password").accessToken();

		TestResponse<ErrorResponse> response = post(
				"/api/internal/products",
				new ProductRequest("", null, null, "", null, null, null, null, null, null, null, null, null),
				token,
				ErrorResponse.class
		);

		assertThat(response.status()).isEqualTo(HttpStatus.BAD_REQUEST.value());
		assertThat(response.body().code()).isEqualTo("VALIDATION_FAILED");
		assertThat(response.body().details()).containsKeys("productCode", "name", "categoryId", "unit");
	}

	@Test
	void createRejectsDuplicateProductCode() {
		String token = login("admin@megabike.local", "password").accessToken();
		CategoryResponse category = createCategory(token, "Duplicate Product Code Category");
		String productCode = "MB-D-" + UUID.randomUUID();

		post("/api/internal/products", productRequest(category.id(), productCode, null, "First Product"), token, ProductResponse.class);
		TestResponse<ErrorResponse> duplicate = post(
				"/api/internal/products",
				productRequest(category.id(), productCode, null, "Second Product"),
				token,
				ErrorResponse.class
		);

		assertThat(duplicate.status()).isEqualTo(HttpStatus.CONFLICT.value());
		assertThat(duplicate.body().code()).isEqualTo("PRODUCT_CODE_EXISTS");
	}

	@Test
	void createRejectsMissingCategory() {
		String token = login("admin@megabike.local", "password").accessToken();

		TestResponse<ErrorResponse> response = post(
				"/api/internal/products",
				productRequest(UUID.randomUUID(), "MB-M-" + UUID.randomUUID(), null, "Missing Category Product"),
				token,
				ErrorResponse.class
		);

		assertThat(response.status()).isEqualTo(HttpStatus.BAD_REQUEST.value());
		assertThat(response.body().code()).isEqualTo("PRODUCT_CATEGORY_NOT_FOUND");
	}

	private CategoryResponse createCategory(String token, String name) {
		String slug = name.toLowerCase().replace(" ", "-") + "-" + UUID.randomUUID();
		return post("/api/internal/categories", new CategoryRequest(name, slug, null, true), token, CategoryResponse.class).body();
	}

	private ProductRequest productRequest(UUID categoryId, String productCode, String barcode, String name) {
		return new ProductRequest(
				productCode,
				"ALG-" + productCode,
				barcode,
				name,
				"Integration test product",
				"Mega Bike",
				categoryId,
				ProductUnit.PIECE,
				new BigDecimal("10.00"),
				new BigDecimal("19.99"),
				new BigDecimal("20.00"),
				true,
				true
		);
	}

	private LoginResponse login(String email, String password) {
		return post("/api/auth/login", new LoginRequest(email, password), null, LoginResponse.class).body();
	}

	private <T> TestResponse<T> get(String path, String accessToken, Class<T> responseType) {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url(path))).GET();
		request.header("Authorization", "Bearer " + accessToken);
		return exchange(request.build(), responseType);
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
