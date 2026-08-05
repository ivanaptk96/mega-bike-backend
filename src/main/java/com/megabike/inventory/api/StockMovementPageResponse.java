package com.megabike.inventory.api;

import java.util.List;

public record StockMovementPageResponse(
		List<StockMovementResponse> content,
		int page,
		int size,
		long totalElements,
		int totalPages,
		boolean first,
		boolean last
) {
}
