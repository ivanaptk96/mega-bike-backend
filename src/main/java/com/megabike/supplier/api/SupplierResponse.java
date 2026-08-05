package com.megabike.supplier.api;

import java.time.Instant;
import java.util.UUID;

public record SupplierResponse(
		UUID id,
		String code,
		String name,
		String registrationNumber,
		String vatRegistrationNumber,
		String publicUrl,
		String b2bUrl,
		String defaultCurrency,
		boolean active,
		Instant createdAt,
		Instant updatedAt
) {
}
