package com.megabike.inventory.api;

import com.megabike.inventory.domain.MovementType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StockMovementResponse(
		UUID id,
		UUID productId,
		String productCode,
		String productName,
		UUID stockLocationId,
		String stockLocationCode,
		String stockLocationName,
		MovementType movementType,
		BigDecimal quantityDelta,
		BigDecimal quantityAfter,
		String reason,
		String referenceText,
		String createdBy,
		Instant createdAt
) {
}
