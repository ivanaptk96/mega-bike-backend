package com.megabike.sef.api;

import java.util.List;

public record SefInvoicePageResponse(
		List<SefInvoiceResponse> content,
		int page,
		int size,
		long totalElements,
		int totalPages,
		boolean first,
		boolean last
) {
}
