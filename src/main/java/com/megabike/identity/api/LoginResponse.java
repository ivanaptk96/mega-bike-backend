package com.megabike.identity.api;

import java.time.Instant;
import java.util.List;

public record LoginResponse(
		String accessToken,
		String tokenType,
		long expiresInSeconds,
		Instant expiresAt,
		String email,
		List<String> authorities
) {
}
