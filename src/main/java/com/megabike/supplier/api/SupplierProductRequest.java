package com.megabike.supplier.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SupplierProductRequest(
		@NotNull
		UUID supplierId,

		UUID productId,

		@NotBlank
		@Size(max = 120)
		String supplierCode,

		@Size(max = 80)
		String supplierBarcode,

		@NotBlank
		@Size(max = 300)
		String supplierProductName,

		@Size(max = 160)
		String brandName,

		@Size(max = 300)
		String categoryText,

		@Size(max = 4000)
		String description,

		@Size(max = 1000)
		String imageUrl,

		@NotNull
		Boolean active
) {
}
