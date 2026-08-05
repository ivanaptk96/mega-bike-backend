package com.megabike.sef.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SefConnectionRequest(
		@NotBlank
		@Size(max = 20)
		String companyTaxId,

		@Size(max = 30)
		String companyRegistrationNumber,

		@NotBlank
		@Size(max = 240)
		String companyName,

		@NotBlank
		@Size(max = 1000)
		String apiKey,

		boolean enabled
) {
}
