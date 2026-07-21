package com.megabike.catalog.api;

import com.megabike.catalog.domain.ProductUnit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
		UUID id,
		String productCode,
		String externalId,
		String barcode,
		String name,
		String description,
		String brandName,
		UUID categoryId,
		String categoryName,
		ProductUnit unit,
		BigDecimal purchasePrice,
		BigDecimal retailPrice,
		BigDecimal vatRate,
		boolean retailPriceIncludesVat,
		boolean active,
		Instant createdAt,
		Instant updatedAt
) {
}
