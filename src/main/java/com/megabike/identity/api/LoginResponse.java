package com.megabike.identity.api;

import java.time.Instant;
import java.util.List;

public record LoginResponse(
		String accessToken,
		String refreshToken,
		String tokenType,
		long expiresInSeconds,
		Instant expiresAt,
		Instant refreshTokenExpiresAt,
		String email,
		List<String> authorities
) {
}
