package com.megabike.sef.infrastructure;

import com.megabike.shared.api.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SefPublicApiClientTests {
	private MockRestServiceServer server;
	private SefPublicApiClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl("https://sef.example");
		server = MockRestServiceServer.bindTo(builder).build();
		client = new SefPublicApiClient(builder.build());
	}

	@Test
	void acceptsPdfBytes() {
		byte[] pdf = "%PDF-1.7\ncontent".getBytes(StandardCharsets.US_ASCII);
		server.expect(requestTo("https://sef.example/api/publicApi/purchase-invoice/pdf?invoiceId=7"))
				.andRespond(withSuccess(pdf, MediaType.APPLICATION_PDF));
		assertArrayEquals(pdf, client.downloadPurchaseInvoicePdf("test-key", 7));
		server.verify();
	}

	@Test
	void pendingJsonWithSuccessfulHttpStatusIsNotAPdf() {
		server.expect(requestTo("https://sef.example/api/publicApi/purchase-invoice/pdf?invoiceId=7"))
				.andRespond(withSuccess("{\"message\":\"Extended PDF file creation has been initiated for invoice Id 7.\"}", MediaType.APPLICATION_JSON));
		ApiException error = assertThrows(ApiException.class, () -> client.downloadPurchaseInvoicePdf("test-key", 7));
		assertEquals("SEF_PDF_NOT_READY", error.getCode());
		assertEquals(409, error.getStatus().value());
	}

	@Test
	void rejectsOtherNonPdfResponses() {
		server.expect(requestTo("https://sef.example/api/publicApi/purchase-invoice/pdf?invoiceId=7"))
				.andRespond(withSuccess("<html>error</html>", MediaType.TEXT_HTML));
		ApiException error = assertThrows(ApiException.class, () -> client.downloadPurchaseInvoicePdf("test-key", 7));
		assertEquals("SEF_INVALID_PDF", error.getCode());
	}
}
