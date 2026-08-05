package com.megabike.supplier.api;

import java.util.List;

public record SupplierProductPageResponse(
		List<SupplierProductResponse> content,
		int page,
		int size,
		long totalElements,
		int totalPages,
		boolean first,
		boolean last
) {
}
