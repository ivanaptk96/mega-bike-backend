package com.megabike.catalog.api;

import java.time.Instant;
import java.util.UUID;

public record CategoryResponse(
		UUID id,
		String name,
		String slug,
		UUID parentId,
		String parentName,
		boolean active,
		Instant createdAt,
		Instant updatedAt
) {
}
