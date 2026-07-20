package com.megabike.shared.api;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
		String code,
		String message,
		Map<String, String> details,
		Instant timestamp
) {

	public static ErrorResponse of(String code, String message) {
		return new ErrorResponse(code, message, Map.of(), Instant.now());
	}

	public static ErrorResponse withDetails(String code, String message, Map<String, String> details) {
		return new ErrorResponse(code, message, details, Instant.now());
	}
}
