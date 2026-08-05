package com.megabike.inventory.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InventoryItemResponse(
		UUID id,
		UUID productId,
		String productCode,
		String productName,
		String barcode,
		UUID stockLocationId,
		String stockLocationCode,
		String stockLocationName,
		BigDecimal quantityOnHand,
		Instant createdAt,
		Instant updatedAt
) {
}
