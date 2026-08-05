package com.megabike.supplier.api;

import java.time.Instant;
import java.util.UUID;

public record SupplierProductResponse(
		UUID id,
		UUID supplierId,
		String supplierCode,
		String supplierName,
		UUID productId,
		String productCode,
		String productName,
		String supplierBarcode,
		String supplierProductName,
		String brandName,
		String categoryText,
		String description,
		String imageUrl,
		boolean active,
		Instant createdAt,
		Instant updatedAt
) {
}
