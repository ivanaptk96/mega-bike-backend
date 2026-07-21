package com.megabike.catalog.api;

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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CategoryIntegrationTests {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
			.withDatabaseName("megabike_catalog_test")
			.withUsername("megabike")
			.withPassword("megabike");

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.liquibase.change-log", () -> "classpath:/db/changelog/db.changelog-dev.xml");
		registry.add("megabike.security.jwt.secret", () -> "catalog-integration-test-secret-that-is-long-enough");
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
	void adminCanCreateAndReadCategory() {
		String token = login("admin@megabike.local", "password").accessToken();
		String slug = "helmets-" + UUID.randomUUID();

		TestResponse<CategoryResponse> create = post(
				"/api/internal/categories",
				new CategoryRequest("Helmets", slug, null, true),
				token,
				CategoryResponse.class
		);

		assertThat(create.status()).isEqualTo(HttpStatus.OK.value());
		assertThat(create.body().id()).isNotNull();
		assertThat(create.body().slug()).isEqualTo(slug);

		TestResponse<CategoryResponse> get = get(
				"/api/internal/categories/" + create.body().id(),
				token,
				CategoryResponse.class
		);

		assertThat(get.status()).isEqualTo(HttpStatus.OK.value());
		assertThat(get.body().name()).isEqualTo("Helmets");
	}

	@Test
	void managerCanUpdateCategory() {
		String adminToken = login("admin@megabike.local", "password").accessToken();
		String managerToken = login("manager@megabike.local", "password").accessToken();
		CategoryResponse category = post(
				"/api/internal/categories",
				new CategoryRequest("Lights", "lights-" + UUID.randomUUID(), null, true),
				adminToken,
				CategoryResponse.class
		).body();

		TestResponse<CategoryResponse> update = put(
				"/api/internal/categories/" + category.id(),
				new CategoryRequest("Bike Lights", category.slug(), null, false),
				managerToken,
				CategoryResponse.class
		);

		assertThat(update.status()).isEqualTo(HttpStatus.OK.value());
		assertThat(update.body().name()).isEqualTo("Bike Lights");
		assertThat(update.body().active()).isFalse();
	}

	@Test
	void employeeCanListButCannotCreateCategory() {
		String token = login("employee@megabike.local", "password").accessToken();

		TestResponse<CategoryResponse[]> list = get("/api/internal/categories", token, CategoryResponse[].class);
		assertThat(list.status()).isEqualTo(HttpStatus.OK.value());
		assertThat(list.body()).isNotEmpty();

		TestResponse<ErrorResponse> create = post(
				"/api/internal/categories",
				new CategoryRequest("Forbidden", "forbidden-" + UUID.randomUUID(), null, true),
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
				"/api/internal/categories",
				new CategoryRequest("", null, null, true),
				token,
				ErrorResponse.class
		);

		assertThat(response.status()).isEqualTo(HttpStatus.BAD_REQUEST.value());
		assertThat(response.body().code()).isEqualTo("VALIDATION_FAILED");
		assertThat(response.body().details()).containsKey("name");
	}

	@Test
	void getReturnsNotFoundForMissingCategory() {
		String token = login("admin@megabike.local", "password").accessToken();

		TestResponse<ErrorResponse> response = get(
				"/api/internal/categories/" + UUID.randomUUID(),
				token,
				ErrorResponse.class
		);

		assertThat(response.status()).isEqualTo(HttpStatus.NOT_FOUND.value());
		assertThat(response.body().code()).isEqualTo("CATEGORY_NOT_FOUND");
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
