package com.megabike.sef.api;

public record SefInvoiceDocumentContent(
		String filename,
		String contentType,
		byte[] content
) {
}
