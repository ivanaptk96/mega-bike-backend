package com.megabike.supplier.api;

import com.megabike.supplier.domain.SupplierClassification;

import java.time.Instant;
import java.util.UUID;

public record SupplierResponse(
		UUID id,
		String code,
		String name,
		String displayName,
		String registrationNumber,
		String vatRegistrationNumber,
		SupplierClassification classification,
		String publicUrl,
		String b2bUrl,
		String defaultCurrency,
		boolean active,
		Instant createdAt,
		Instant updatedAt
) {
}
