package com.megabike.sef.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.megabike.sef.domain.*;
import com.megabike.sef.infrastructure.SefPublicApiClient;
import com.megabike.sef.infrastructure.dto.SefPurchaseInvoiceOverviewDto;
import com.megabike.shared.api.ApiException;
import com.megabike.supplier.domain.SupplierRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SefPurchaseInvoiceSyncServiceTests {
	@Mock SefPublicApiClient sefClient;
	@Mock SefConnectionRepository connectionRepository;
	@Mock SefInvoiceRepository invoiceRepository;
	@Mock SefInvoiceDocumentRepository documentRepository;
	@Mock SefInvoiceLineRepository lineRepository;
	@Mock SefInvoiceVatSummaryRepository vatSummaryRepository;
	@Mock SefInvoicePaymentRepository paymentRepository;
	@Mock SefSyncRunRepository syncRunRepository;
	@Mock SupplierRepository supplierRepository;
	@Mock SefUblInvoiceParser ublInvoiceParser;
	@Mock ObjectMapper objectMapper;
	@InjectMocks SefPurchaseInvoiceSyncService service;

	@Test
	void pendingPdfDoesNotPreventXmlAndInvoiceSyncOrCreateFakeDocument() {
		UUID connectionId = UUID.randomUUID();
		SefConnection connection = new SefConnection(connectionId, "123456789", null, "Test", "key", true, Instant.now());
		SefInvoice invoice = new SefInvoice(UUID.randomUUID(), connection, 7L, SefInvoiceDirection.PURCHASE, Instant.now());
		SefPurchaseInvoiceOverviewDto overview = mock(SefPurchaseInvoiceOverviewDto.class);
		when(overview.invoiceId()).thenReturn(7L);
		when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(connection));
		when(invoiceRepository.findByConnectionIdAndDirectionAndSefInvoiceId(connectionId, SefInvoiceDirection.PURCHASE, 7L))
				.thenReturn(Optional.of(invoice));
		when(invoiceRepository.save(any())).thenReturn(invoice);
		when(syncRunRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(sefClient.getPurchaseInvoiceOverview("key", null, null, null)).thenReturn(List.of(overview));
		byte[] xml = "<Invoice/>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
		when(sefClient.downloadPurchaseInvoiceXml("key", 7L)).thenReturn(xml);
		when(ublInvoiceParser.parse(xml)).thenReturn(new SefUblInvoiceParser.ParsedInvoice(List.of(), List.of(), List.of()));
		when(sefClient.downloadPurchaseInvoicePdf("key", 7L))
				.thenThrow(new ApiException(HttpStatus.CONFLICT, "SEF_PDF_NOT_READY", "Pending"));

		SefSyncRun run = service.syncPurchaseInvoices(connectionId, null, null, true);

		assertEquals(1, run.getInvoiceCount());
		assertEquals(1, run.getDocumentCount());
		verify(documentRepository, never()).save(argThat(document -> document.getDocumentType() == SefInvoiceDocumentType.EXTENDED_PDF));
		verify(documentRepository).save(argThat(document -> document.getDocumentType() == SefInvoiceDocumentType.UBL_XML));
	}
}
