package com.megabike.catalog.api;

import com.megabike.catalog.domain.ProductUnit;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductRequest(
		@NotBlank
		@Size(max = 50)
		String productCode,

		@Size(max = 120)
		String externalId,

		@Size(max = 64)
		String barcode,

		@NotBlank
		@Size(max = 240)
		String name,

		@Size(max = 2000)
		String description,

		@Size(max = 160)
		String brandName,

		@NotNull
		UUID categoryId,

		@NotNull
		ProductUnit unit,

		@NotNull
		@DecimalMin(value = "0.00")
		@Digits(integer = 10, fraction = 2)
		BigDecimal purchasePrice,

		@NotNull
		@DecimalMin(value = "0.00")
		@Digits(integer = 10, fraction = 2)
		BigDecimal retailPrice,

		@NotNull
		@DecimalMin(value = "0.00")
		@DecimalMax(value = "100.00")
		@Digits(integer = 3, fraction = 2)
		BigDecimal vatRate,

		@NotNull
		Boolean retailPriceIncludesVat,

		@NotNull
		Boolean active
) {
}
