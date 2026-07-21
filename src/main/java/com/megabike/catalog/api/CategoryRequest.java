package com.megabike.catalog.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CategoryRequest(
		@NotBlank
		@Size(max = 200)
		String name,

		@Size(max = 220)
		String slug,

		UUID parentId,

		boolean active
) {
}
