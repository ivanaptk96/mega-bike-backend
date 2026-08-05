package com.megabike.inventory.api;

import com.fasterxml.jackson.databind.ObjectMapper;
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
class InventoryIntegrationTests {

	private static final UUID STORE_LOCATION_ID = UUID.fromString("60000000-0000-0000-0000-000000000001");
	private static final UUID DEV_PRODUCT_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
			.withDatabaseName("megabike_inventory_test")
			.withUsername("megabike")
			.withPassword("megabike");

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.liquibase.change-log", () -> "classpath:/db/changelog/db.changelog-dev.xml");
		registry.add("megabike.security.jwt.secret", () -> "inventory-integration-test-secret-that-is-long-enough");
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
	void employeeCanListAndReadInventory() {
		String token = login("employee@megabike.local", "password").accessToken();

		TestResponse<InventoryPageResponse> list = get("/api/internal/inventory?page=0&size=10", token, InventoryPageResponse.class);
		assertThat(list.status()).isEqualTo(HttpStatus.OK.value());
		assertThat(list.body().content()).isNotEmpty();
		assertThat(list.body().page()).isZero();
		assertThat(list.body().size()).isEqualTo(10);

		TestResponse<InventoryItemResponse> productInventory = get(
				"/api/internal/inventory/products/" + DEV_PRODUCT_ID + "?stockLocationId=" + STORE_LOCATION_ID,
				token,
				InventoryItemResponse.class
		);

		assertThat(productInventory.status()).isEqualTo(HttpStatus.OK.value());
		assertThat(productInventory.body().productId()).isEqualTo(DEV_PRODUCT_ID);
		assertThat(productInventory.body().stockLocationId()).isEqualTo(STORE_LOCATION_ID);
		assertThat(productInventory.body().quantityOnHand()).isEqualByComparingTo(new BigDecimal("5.000"));
	}

	@Test
	void adminCanAdjustInventory() {
		String token = login("admin@megabike.local", "password").accessToken();

		TestResponse<StockMovementResponse> adjustment = post(
				"/api/internal/inventory/adjustments",
				new StockAdjustmentRequest(DEV_PRODUCT_ID, STORE_LOCATION_ID, new BigDecimal("2.000"), "Cycle count correction", "COUNT-1"),
				token,
				StockMovementResponse.class
		);

		assertThat(adjustment.status()).isEqualTo(HttpStatus.OK.value());
		assertThat(adjustment.body().quantityDelta()).isEqualByComparingTo(new BigDecimal("2.000"));
		assertThat(adjustment.body().quantityAfter()).isEqualByComparingTo(new BigDecimal("7.000"));
		assertThat(adjustment.body().createdBy()).isEqualTo("admin@megabike.local");

		TestResponse<InventoryItemResponse> productInventory = get(
				"/api/internal/inventory/products/" + DEV_PRODUCT_ID + "?stockLocationId=" + STORE_LOCATION_ID,
				token,
				InventoryItemResponse.class
		);

		assertThat(productInventory.body().quantityOnHand()).isEqualByComparingTo(new BigDecimal("7.000"));
	}

	@Test
	void employeeCanListStockMovements() {
		String adminToken = login("admin@megabike.local", "password").accessToken();
		String employeeToken = login("employee@megabike.local", "password").accessToken();
		TestResponse<StockMovementResponse> adjustment = post(
				"/api/internal/inventory/adjustments",
				new StockAdjustmentRequest(DEV_PRODUCT_ID, STORE_LOCATION_ID, new BigDecimal("1.000"), "History endpoint test", "HISTORY-1"),
				adminToken,
				StockMovementResponse.class
		);

		TestResponse<StockMovementPageResponse> movements = get(
				"/api/internal/inventory/movements?productId=" + DEV_PRODUCT_ID + "&stockLocationId=" + STORE_LOCATION_ID + "&movementType=ADJUSTMENT",
				employeeToken,
				StockMovementPageResponse.class
		);

		assertThat(movements.status()).isEqualTo(HttpStatus.OK.value());
		assertThat(movements.body().content()).extracting(StockMovementResponse::id).contains(adjustment.body().id());
		assertThat(movements.body().content().getFirst().movementType()).isEqualTo(adjustment.body().movementType());

		TestResponse<StockMovementPageResponse> productMovements = get(
				"/api/internal/inventory/products/" + DEV_PRODUCT_ID + "/movements?page=0&size=20",
				employeeToken,
				StockMovementPageResponse.class
		);

		assertThat(productMovements.status()).isEqualTo(HttpStatus.OK.value());
		assertThat(productMovements.body().content()).extracting(StockMovementResponse::id).contains(adjustment.body().id());
	}

	@Test
	void managerCannotAdjustInventory() {
		String token = login("manager@megabike.local", "password").accessToken();

		TestResponse<ErrorResponse> response = post(
				"/api/internal/inventory/adjustments",
				new StockAdjustmentRequest(DEV_PRODUCT_ID, STORE_LOCATION_ID, new BigDecimal("1.000"), "Forbidden adjustment", null),
				token,
				ErrorResponse.class
		);

		assertThat(response.status()).isEqualTo(HttpStatus.FORBIDDEN.value());
		assertThat(response.body().code()).isEqualTo("ACCESS_DENIED");
	}

	@Test
	void adjustmentRejectsNegativeResult() {
		String token = login("admin@megabike.local", "password").accessToken();

		TestResponse<ErrorResponse> response = post(
				"/api/internal/inventory/adjustments",
				new StockAdjustmentRequest(DEV_PRODUCT_ID, STORE_LOCATION_ID, new BigDecimal("-999.000"), "Invalid negative stock", null),
				token,
				ErrorResponse.class
		);

		assertThat(response.status()).isEqualTo(HttpStatus.BAD_REQUEST.value());
		assertThat(response.body().code()).isEqualTo("INVENTORY_NEGATIVE_STOCK");
	}

	@Test
	void adjustmentRejectsInvalidRequest() {
		String token = login("admin@megabike.local", "password").accessToken();

		TestResponse<ErrorResponse> response = post(
				"/api/internal/inventory/adjustments",
				new StockAdjustmentRequest(null, null, null, "", null),
				token,
				ErrorResponse.class
		);

		assertThat(response.status()).isEqualTo(HttpStatus.BAD_REQUEST.value());
		assertThat(response.body().code()).isEqualTo("VALIDATION_FAILED");
		assertThat(response.body().details()).containsKeys("productId", "stockLocationId", "quantityDelta", "reason");
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
