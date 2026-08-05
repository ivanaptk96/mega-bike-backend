package com.megabike.sef.api;

import com.megabike.sef.domain.SefInvoiceDirection;
import com.megabike.sef.domain.SefSyncStatus;

import java.time.Instant;
import java.util.UUID;

public record SefSyncRunResponse(
		UUID id,
		UUID connectionId,
		SefInvoiceDirection direction,
		SefSyncStatus status,
		Instant dateFrom,
		Instant dateTo,
		Instant startedAt,
		Instant finishedAt,
		int invoiceCount,
		int documentCount,
		String errorMessage
) {
}
