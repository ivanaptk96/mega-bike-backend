package com.megabike.identity.api;

import java.time.Instant;

public record TokenResponse(
		String accessToken,
		String tokenType,
		long expiresInSeconds,
		Instant expiresAt
) {
}
