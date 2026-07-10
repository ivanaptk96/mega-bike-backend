package com.megabike.identity.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class JwtService {

	private static final String HMAC_SHA256 = "HmacSHA256";
	private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

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

	public Optional<ValidatedToken> validateAccessToken(String token) {
		String[] parts = token.split("\\.", -1);
		if (parts.length != 3) {
			return Optional.empty();
		}

		String signingInput = parts[0] + "." + parts[1];
		if (!constantTimeEquals(sign(signingInput), parts[2])) {
			return Optional.empty();
		}

		String payload = decodeBase64Url(parts[1]);
		if (!Objects.equals(issuer, readStringClaim(payload, "iss"))) {
			return Optional.empty();
		}

		Long expiresAtEpochSecond = readLongClaim(payload, "exp");
		if (expiresAtEpochSecond == null || Instant.now(clock).isAfter(Instant.ofEpochSecond(expiresAtEpochSecond))) {
			return Optional.empty();
		}

		String subject = readStringClaim(payload, "sub");
		if (subject == null || subject.isBlank()) {
			return Optional.empty();
		}

		return Optional.of(new ValidatedToken(subject, readStringArrayClaim(payload, "authorities")));
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

	private String decodeBase64Url(String encodedValue) {
		return new String(BASE64_URL_DECODER.decode(encodedValue), StandardCharsets.UTF_8);
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

	private boolean constantTimeEquals(String expected, String actual) {
		return MessageDigest.isEqual(
				expected.getBytes(StandardCharsets.UTF_8),
				actual.getBytes(StandardCharsets.UTF_8)
		);
	}

	private String readStringClaim(String json, String claimName) {
		String prefix = "\"" + claimName + "\":\"";
		int start = json.indexOf(prefix);
		if (start < 0) {
			return null;
		}

		int valueStart = start + prefix.length();
		StringBuilder value = new StringBuilder();
		boolean escaping = false;

		for (int index = valueStart; index < json.length(); index++) {
			char current = json.charAt(index);
			if (escaping) {
				value.append(current);
				escaping = false;
			} else if (current == '\\') {
				escaping = true;
			} else if (current == '"') {
				return value.toString();
			} else {
				value.append(current);
			}
		}

		return null;
	}

	private Long readLongClaim(String json, String claimName) {
		String prefix = "\"" + claimName + "\":";
		int start = json.indexOf(prefix);
		if (start < 0) {
			return null;
		}

		int valueStart = start + prefix.length();
		int valueEnd = valueStart;
		while (valueEnd < json.length() && Character.isDigit(json.charAt(valueEnd))) {
			valueEnd++;
		}

		if (valueEnd == valueStart) {
			return null;
		}

		return Long.parseLong(json.substring(valueStart, valueEnd));
	}

	private List<String> readStringArrayClaim(String json, String claimName) {
		String prefix = "\"" + claimName + "\":[";
		int start = json.indexOf(prefix);
		if (start < 0) {
			return List.of();
		}

		int index = start + prefix.length();
		List<String> values = new ArrayList<>();

		while (index < json.length()) {
			char current = json.charAt(index);
			if (current == ']') {
				return values;
			}
			if (current == ',') {
				index++;
				continue;
			}
			if (current != '"') {
				return List.of();
			}

			StringBuilder value = new StringBuilder();
			boolean escaping = false;
			index++;

			while (index < json.length()) {
				current = json.charAt(index);
				if (escaping) {
					value.append(current);
					escaping = false;
				} else if (current == '\\') {
					escaping = true;
				} else if (current == '"') {
					values.add(value.toString());
					index++;
					break;
				} else {
					value.append(current);
				}
				index++;
			}
		}

		return List.of();
	}

	public record GeneratedToken(String value, Instant expiresAt, long expiresInSeconds) {
	}

	public record ValidatedToken(String subject, List<String> authorities) {
	}
}
