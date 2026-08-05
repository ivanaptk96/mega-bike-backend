package com.megabike.inventory.api;

import java.util.List;

public record InventoryPageResponse(
		List<InventoryItemResponse> content,
		int page,
		int size,
		long totalElements,
		int totalPages,
		boolean first,
		boolean last
) {
}
