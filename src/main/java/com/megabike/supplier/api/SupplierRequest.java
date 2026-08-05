package com.megabike.supplier.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SupplierRequest(
		@NotBlank
		@Size(max = 50)
		String code,

		@NotBlank
		@Size(max = 200)
		String name,

		@Size(max = 500)
		String publicUrl,

		@Size(max = 500)
		String b2bUrl,

		@NotBlank
		@Pattern(regexp = "[A-Z]{3}")
		String defaultCurrency,

		@NotNull
		Boolean active
) {
}
