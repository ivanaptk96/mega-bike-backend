package com.megabike.sef.api;

import java.time.Instant;
import java.util.UUID;

public record SefConnectionResponse(
		UUID id,
		String companyTaxId,
		String companyRegistrationNumber,
		String companyName,
		boolean enabled,
		Instant lastPurchaseSyncAt,
		Instant createdAt,
		Instant updatedAt
) {
}
