package com.megabike.sef.application;

import com.megabike.sef.api.SefInvoiceDetailResponse;
import com.megabike.sef.api.SefInvoiceDocumentContent;
import com.megabike.sef.api.SefInvoicePageResponse;
import com.megabike.sef.domain.SefConnection;
import com.megabike.sef.domain.SefConnectionRepository;
import com.megabike.sef.domain.SefInvoice;
import com.megabike.sef.domain.SefInvoiceDirection;
import com.megabike.sef.domain.SefInvoiceDocument;
import com.megabike.sef.domain.SefInvoiceDocumentRepository;
import com.megabike.sef.domain.SefInvoiceDocumentType;
import com.megabike.sef.domain.SefInvoiceLine;
import com.megabike.sef.domain.SefInvoiceLineRepository;
import com.megabike.sef.domain.SefInvoicePayment;
import com.megabike.sef.domain.SefInvoicePaymentRepository;
import com.megabike.sef.domain.SefInvoiceRepository;
import com.megabike.sef.domain.SefInvoiceVatSummary;
import com.megabike.sef.domain.SefInvoiceVatSummaryRepository;
import com.megabike.shared.api.ApiException;
import com.megabike.supplier.domain.Supplier;
import com.megabike.supplier.domain.SupplierClassification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SefIntegrationServiceTests {

	@Mock
	private SefConnectionRepository connectionRepository;

	@Mock
	private SefInvoiceRepository invoiceRepository;

	@Mock
	private SefInvoiceDocumentRepository documentRepository;

	@Mock
	private SefInvoiceLineRepository lineRepository;

	@Mock
	private SefInvoiceVatSummaryRepository vatSummaryRepository;

	@Mock
	private SefInvoicePaymentRepository paymentRepository;

	private SefIntegrationService service;
	private SefConnection connection;
	private SefInvoice invoice;
	private UUID connectionId;
	private UUID invoiceId;

	@BeforeEach
	void setUp() {
		service = new SefIntegrationService(
				connectionRepository,
				invoiceRepository,
				documentRepository,
				lineRepository,
				vatSummaryRepository,
				paymentRepository
		);
		connectionId = UUID.randomUUID();
		invoiceId = UUID.randomUUID();
		Instant now = Instant.parse("2026-08-05T10:00:00Z");
		connection = new SefConnection(connectionId, "123456789", "12345678", "Mega Bike", "secret", true, now);
		invoice = new SefInvoice(invoiceId, connection, 98765L, SefInvoiceDirection.PURCHASE, now);
		Supplier supplier = new Supplier(
				UUID.randomUUID(),
				"DOBAVLJAC",
				"Dobavljač",
				"87654321",
				"109876543",
				null,
				null,
				"RSD",
				true,
				now
		);
		supplier.updateClassification(SupplierClassification.MERCHANDISE, now);
		supplier.updateDisplayName("Interni dobavljač", now);
		invoice.applyPurchaseOverview(
				supplier,
				"global-id",
				"cir-id",
				"UF-42",
				"380",
				"Approved",
				"Dobavljač",
				"87654321",
				"109876543",
				"RSD",
				new BigDecimal("1000.00"),
				new BigDecimal("200.00"),
				new BigDecimal("1200.00"),
				now,
				now.plusSeconds(86400),
				"{}",
				now
		);
		lenient().when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(connection));
		lenient().when(invoiceRepository.findByIdAndConnectionIdAndDirection(invoiceId, connectionId, SefInvoiceDirection.PURCHASE))
				.thenReturn(Optional.of(invoice));
	}

	@Test
	void returnsStoredInvoiceDetailsAndDocumentMetadata() {
		SefInvoiceLine line = new SefInvoiceLine(UUID.randomUUID(), invoice, 1, "Bicikl");
		SefInvoiceVatSummary vat = new SefInvoiceVatSummary(UUID.randomUUID(), invoice, "S");
		SefInvoicePayment payment = new SefInvoicePayment(UUID.randomUUID(), invoice);
		SefInvoiceDocument document = new SefInvoiceDocument(
				UUID.randomUUID(),
				invoice,
				SefInvoiceDocumentType.EXTENDED_PDF,
				"invoice.pdf",
				"application/pdf",
				"abc123",
				new byte[]{1, 2, 3},
				Instant.parse("2026-08-05T10:05:00Z")
		);
		when(lineRepository.findByInvoiceIdOrderByLineNumberAsc(invoiceId)).thenReturn(List.of(line));
		when(vatSummaryRepository.findByInvoiceIdOrderByVatRateAsc(invoiceId)).thenReturn(List.of(vat));
		when(paymentRepository.findByInvoiceIdOrderByIdAsc(invoiceId)).thenReturn(List.of(payment));
		when(documentRepository.findByInvoiceIdOrderByDocumentTypeAsc(invoiceId)).thenReturn(List.of(document));

		SefInvoiceDetailResponse response = service.getPurchaseInvoice(connectionId, invoiceId);

		assertEquals("UF-42", response.invoice().invoiceNumber());
		assertEquals(SupplierClassification.MERCHANDISE, response.invoice().supplierClassification());
		assertEquals("Interni dobavljač", response.invoice().supplierDisplayName());
		assertEquals(1, response.lines().size());
		assertEquals(1, response.vatSummaries().size());
		assertEquals(1, response.payments().size());
		assertEquals(3, response.documents().getFirst().size());
		assertEquals("invoice.pdf", response.documents().getFirst().filename());
	}

	@Test
	void returnsStoredDocumentContentWithinTheInvoiceScope() {
		UUID documentId = UUID.randomUUID();
		byte[] content = new byte[]{4, 5, 6};
		SefInvoiceDocument document = new SefInvoiceDocument(
				documentId,
				invoice,
				SefInvoiceDocumentType.UBL_XML,
				"invoice.xml",
				"application/xml",
				"def456",
				content,
				Instant.parse("2026-08-05T10:05:00Z")
		);
		when(documentRepository.findByIdAndInvoiceId(documentId, invoiceId)).thenReturn(Optional.of(document));

		SefInvoiceDocumentContent response = service.getPurchaseInvoiceDocument(connectionId, invoiceId, documentId);

		assertEquals("invoice.xml", response.filename());
		assertEquals("application/xml", response.contentType());
		assertArrayEquals(content, response.content());
	}

	@Test
	void listsSupplierInvoicesWithTheirConnection() {
		UUID supplierId = invoice.getSupplier().getId();
		when(invoiceRepository.findBySupplierIdNewestFirst(supplierId)).thenReturn(List.of(invoice));

		var response = service.listSupplierInvoices(supplierId);

		assertEquals(1, response.size());
		assertEquals(connectionId, response.getFirst().connectionId());
		assertEquals(invoiceId, response.getFirst().id());
	}

	@Test
	void listsFilteredInvoicesAsAPage() {
		when(invoiceRepository.findAll(
				org.mockito.ArgumentMatchers.<Specification<SefInvoice>>any(),
				any(Pageable.class)
		))
				.thenReturn(new PageImpl<>(List.of(invoice), PageRequest.of(1, 10), 21));

		SefInvoicePageResponse response = service.listInvoices(
				connectionId,
				SefInvoiceDirection.PURCHASE,
				SupplierClassification.MERCHANDISE,
				1,
				10
		);

		assertEquals(1, response.content().size());
		assertEquals("UF-42", response.content().getFirst().invoiceNumber());
		assertEquals(1, response.page());
		assertEquals(10, response.size());
		assertEquals(21, response.totalElements());
		assertEquals(3, response.totalPages());
		assertFalse(response.first());
		assertFalse(response.last());
	}

	@Test
	void rejectsInvalidInvoicePageParameters() {
		assertThrows(ApiException.class, () -> service.listInvoices(
				connectionId,
				SefInvoiceDirection.PURCHASE,
				SupplierClassification.MERCHANDISE,
				-1,
				10
		));
	}
}
