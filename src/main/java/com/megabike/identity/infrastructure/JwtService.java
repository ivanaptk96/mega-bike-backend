package com.megabike.identity.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class JwtService {

	private static final String HMAC_SHA256 = "HmacSHA256";
	private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

	private final Clock clock;
	private final String issuer;
	private final String secret;
	private final long accessTokenTtlSeconds;

	public JwtService(
			@Value("${megabike.security.jwt.issuer:mega-bike-backend}") String issuer,
			@Value("${megabike.security.jwt.secret}") String secret,
			@Value("${megabike.security.jwt.access-token-ttl-seconds:900}") long accessTokenTtlSeconds
	) {
		this.clock = Clock.systemUTC();
		this.issuer = issuer;
		this.secret = secret;
		this.accessTokenTtlSeconds = accessTokenTtlSeconds;
	}

	public GeneratedToken createAccessToken(UserDetails userDetails) {
		Instant issuedAt = Instant.now(clock);
		Instant expiresAt = issuedAt.plusSeconds(accessTokenTtlSeconds);

		String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
		String payload = "{"
				+ "\"iss\":\"" + escapeJson(issuer) + "\","
				+ "\"sub\":\"" + escapeJson(userDetails.getUsername()) + "\","
				+ "\"iat\":" + issuedAt.getEpochSecond() + ","
				+ "\"exp\":" + expiresAt.getEpochSecond() + ","
				+ "\"authorities\":" + authoritiesJson(userDetails)
				+ "}";

		String encodedHeader = encodeJson(header);
		String encodedPayload = encodeJson(payload);
		String signingInput = encodedHeader + "." + encodedPayload;
		String signature = sign(signingInput);

		return new GeneratedToken(signingInput + "." + signature, expiresAt, accessTokenTtlSeconds);
	}

	private List<String> authorities(UserDetails userDetails) {
		return userDetails.getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.sorted()
				.toList();
	}

	private String authoritiesJson(UserDetails userDetails) {
		return authorities(userDetails).stream()
				.map(authority -> "\"" + escapeJson(authority) + "\"")
				.collect(Collectors.joining(",", "[", "]"));
	}

	private String encodeJson(String json) {
		return BASE64_URL_ENCODER.encodeToString(json.getBytes(StandardCharsets.UTF_8));
	}

	private String escapeJson(String value) {
		return value
				.replace("\\", "\\\\")
				.replace("\"", "\\\"");
	}

	private String sign(String signingInput) {
		try {
			Mac mac = Mac.getInstance(HMAC_SHA256);
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
			return BASE64_URL_ENCODER.encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception exception) {
			throw new IllegalStateException("Unable to sign JWT", exception);
		}
	}

	public record GeneratedToken(String value, Instant expiresAt, long expiresInSeconds) {
	}
}
