package com.megabike.sef.api;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record SefPurchaseInvoiceSyncRequest(
		@NotNull
		Instant dateFrom,

		@NotNull
		Instant dateTo,

		boolean downloadDocuments
) {
}
