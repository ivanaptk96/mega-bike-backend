package com.megabike.supplier.api;

import java.time.Instant;
import java.util.UUID;

public record SupplierImportIssueResponse(
		UUID id,
		UUID importRunId,
		int rowNumber,
		String code,
		String message,
		String rawRow,
		Instant createdAt
) {
}
