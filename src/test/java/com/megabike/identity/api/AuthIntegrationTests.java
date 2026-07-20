package com.megabike.identity.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthIntegrationTests {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
			.withDatabaseName("megabike_test")
			.withUsername("megabike")
			.withPassword("megabike");

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.liquibase.change-log", () -> "classpath:/db/changelog/db.changelog-dev.xml");
		registry.add("megabike.security.jwt.secret", () -> "integration-test-secret-that-is-long-enough");
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
	void loginReturnsAccessAndRefreshTokens() {
		TestResponse<LoginResponse> response = login("admin@megabike.local", "password");

		assertThat(response.status()).isEqualTo(HttpStatus.OK.value());
		assertThat(response.body().accessToken()).isNotBlank();
		assertThat(response.body().refreshToken()).isNotBlank();
		assertThat(response.body().email()).isEqualTo("admin@megabike.local");
		assertThat(response.body().authorities()).contains("ROLE_ADMIN", "USER_MANAGE");
	}

	@Test
	void loginRejectsInvalidPassword() {
		TestResponse<ErrorResponse> response = post("/api/auth/login",
				new LoginRequest("admin@megabike.local", "wrong-password"),
				ErrorResponse.class);

		assertThat(response.status()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
		assertThat(response.body().code()).isEqualTo("INVALID_CREDENTIALS");
	}

	@Test
	void loginRejectsInvalidRequestBody() {
		TestResponse<ErrorResponse> response = post("/api/auth/login",
				new LoginRequest("not-an-email", ""),
				ErrorResponse.class);

		assertThat(response.status()).isEqualTo(HttpStatus.BAD_REQUEST.value());
		assertThat(response.body().code()).isEqualTo("VALIDATION_FAILED");
		assertThat(response.body().details()).containsKeys("email", "password");
	}

	@Test
	void meReturnsCurrentUserWithValidToken() {
		LoginResponse login = login("admin@megabike.local", "password").body();

		TestResponse<CurrentUserResponse> response = get("/api/auth/me", login.accessToken(), CurrentUserResponse.class);

		assertThat(response.status()).isEqualTo(HttpStatus.OK.value());
		assertThat(response.body().email()).isEqualTo("admin@megabike.local");
		assertThat(response.body().authorities()).contains("ROLE_ADMIN");
	}

	@Test
	void meRejectsMissingToken() {
		TestResponse<ErrorResponse> response = get("/api/auth/me", null, ErrorResponse.class);

		assertThat(response.status()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
		assertThat(response.body().code()).isEqualTo("AUTHENTICATION_REQUIRED");
	}

	@Test
	void adminRouteRejectsEmployeeToken() {
		LoginResponse login = login("employee@megabike.local", "password").body();

		TestResponse<ErrorResponse> response = get("/api/admin/users", login.accessToken(), ErrorResponse.class);

		assertThat(response.status()).isEqualTo(HttpStatus.FORBIDDEN.value());
		assertThat(response.body().code()).isEqualTo("ACCESS_DENIED");
	}

	@Test
	void refreshReturnsNewAccessToken() {
		LoginResponse login = login("admin@megabike.local", "password").body();

		TestResponse<TokenResponse> response = post("/api/auth/refresh",
				new RefreshTokenRequest(login.refreshToken()),
				TokenResponse.class);

		assertThat(response.status()).isEqualTo(HttpStatus.OK.value());
		assertThat(response.body().accessToken()).isNotBlank();
	}

	@Test
	void logoutRevokesRefreshToken() {
		LoginResponse login = login("admin@megabike.local", "password").body();

		TestResponse<LogoutResponse> logout = post("/api/auth/logout",
				new LogoutRequest(login.refreshToken()),
				login.accessToken(),
				LogoutResponse.class);

		assertThat(logout.status()).isEqualTo(HttpStatus.OK.value());
		assertThat(logout.body().revoked()).isTrue();

		TestResponse<ErrorResponse> refreshAfterLogout = post("/api/auth/refresh",
				new RefreshTokenRequest(login.refreshToken()),
				ErrorResponse.class);

		assertThat(refreshAfterLogout.status()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
		assertThat(refreshAfterLogout.body().code()).isEqualTo("INVALID_CREDENTIALS");
	}

	private TestResponse<LoginResponse> login(String email, String password) {
		return post("/api/auth/login", new LoginRequest(email, password), LoginResponse.class);
	}

	private <T> TestResponse<T> get(String path, String accessToken, Class<T> responseType) {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url(path))).GET();
		if (accessToken != null) {
			request.header("Authorization", "Bearer " + accessToken);
		}
		return exchange(request.build(), responseType);
	}

	private <T> TestResponse<T> post(String path, Object body, Class<T> responseType) {
		return post(path, body, null, responseType);
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
