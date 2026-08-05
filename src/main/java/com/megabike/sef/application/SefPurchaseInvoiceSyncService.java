package com.megabike.sef.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.megabike.sef.domain.SefSyncRun;
import com.megabike.sef.domain.SefSyncRunRepository;
import com.megabike.sef.infrastructure.SefPublicApiClient;
import com.megabike.sef.infrastructure.dto.SefPurchaseInvoiceOverviewDto;
import com.megabike.supplier.domain.Supplier;
import com.megabike.supplier.domain.SupplierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class SefPurchaseInvoiceSyncService {

	private final SefPublicApiClient sefClient;
	private final SefConnectionRepository connectionRepository;
	private final SefInvoiceRepository invoiceRepository;
	private final SefInvoiceDocumentRepository documentRepository;
	private final SefInvoiceLineRepository lineRepository;
	private final SefInvoiceVatSummaryRepository vatSummaryRepository;
	private final SefInvoicePaymentRepository paymentRepository;
	private final SefSyncRunRepository syncRunRepository;
	private final SupplierRepository supplierRepository;
	private final SefUblInvoiceParser ublInvoiceParser;
	private final ObjectMapper objectMapper;
	private final Clock clock;

	public SefPurchaseInvoiceSyncService(
			SefPublicApiClient sefClient,
			SefConnectionRepository connectionRepository,
			SefInvoiceRepository invoiceRepository,
			SefInvoiceDocumentRepository documentRepository,
			SefInvoiceLineRepository lineRepository,
			SefInvoiceVatSummaryRepository vatSummaryRepository,
			SefInvoicePaymentRepository paymentRepository,
			SefSyncRunRepository syncRunRepository,
			SupplierRepository supplierRepository,
			SefUblInvoiceParser ublInvoiceParser,
			ObjectMapper objectMapper
	) {
		this.sefClient = sefClient;
		this.connectionRepository = connectionRepository;
		this.invoiceRepository = invoiceRepository;
		this.documentRepository = documentRepository;
		this.lineRepository = lineRepository;
		this.vatSummaryRepository = vatSummaryRepository;
		this.paymentRepository = paymentRepository;
		this.syncRunRepository = syncRunRepository;
		this.supplierRepository = supplierRepository;
		this.ublInvoiceParser = ublInvoiceParser;
		this.objectMapper = objectMapper;
		this.clock = Clock.systemUTC();
	}

	@Transactional
	public SefSyncRun syncPurchaseInvoices(UUID connectionId, Instant dateFrom, Instant dateTo, boolean downloadDocuments) {
		SefConnection connection = connectionRepository.findById(connectionId)
				.orElseThrow(() -> new IllegalArgumentException("SEF connection was not found."));
		Instant startedAt = Instant.now(clock);
		SefSyncRun syncRun = syncRunRepository.save(new SefSyncRun(UUID.randomUUID(), connection, SefInvoiceDirection.PURCHASE, dateFrom, dateTo, startedAt));

		try {
			List<SefPurchaseInvoiceOverviewDto> overviews = sefClient.getPurchaseInvoiceOverview(connection.getApiKeySecret(), null, dateFrom, dateTo);
			int documentCount = 0;
			for (SefPurchaseInvoiceOverviewDto overview : overviews) {
				SefInvoice invoice = upsertPurchaseInvoice(connection, overview);
				if (downloadDocuments) {
					DocumentDownload xmlDownload = downloadOrLoadDocument(connection, invoice, SefInvoiceDocumentType.UBL_XML, "application/xml", "xml");
					documentCount += xmlDownload.created() ? 1 : 0;
					refreshParsedInvoiceData(invoice, xmlDownload.content());

					DocumentDownload pdfDownload = downloadOrLoadDocument(connection, invoice, SefInvoiceDocumentType.EXTENDED_PDF, "application/pdf", "pdf");
					documentCount += pdfDownload.created() ? 1 : 0;
				}
			}

			Instant finishedAt = Instant.now(clock);
			connection.markPurchaseSynced(dateTo == null ? finishedAt : dateTo, finishedAt);
			syncRun.succeed(overviews.size(), documentCount, finishedAt);
			return syncRun;
		} catch (RuntimeException exception) {
			syncRun.fail(exception.getMessage(), Instant.now(clock));
			throw exception;
		}
	}

	private SefInvoice upsertPurchaseInvoice(SefConnection connection, SefPurchaseInvoiceOverviewDto overview) {
		Instant now = Instant.now(clock);
		SefInvoice invoice = invoiceRepository
				.findByConnectionIdAndDirectionAndSefInvoiceId(connection.getId(), SefInvoiceDirection.PURCHASE, overview.invoiceId())
				.orElseGet(() -> new SefInvoice(UUID.randomUUID(), connection, overview.invoiceId(), SefInvoiceDirection.PURCHASE, now));

		invoice.applyPurchaseOverview(
				resolveSupplier(overview, now),
				overview.globUniqId(),
				overview.cirInvoiceId(),
				overview.documentNumber(),
				overview.documentType(),
				overview.status() == null ? "Unknown" : overview.status().name(),
				overview.supplierName(),
				overview.supplierRegistrationNumber(),
				overview.supplierVatRegistrationNumber(),
				overview.currency(),
				overview.sumWithoutVat(),
				overview.vatAmount(),
				overview.amount(),
				overview.sentDate(),
				overview.dueDate(),
				toJson(overview),
				now
		);
		return invoiceRepository.save(invoice);
	}

	private Supplier resolveSupplier(SefPurchaseInvoiceOverviewDto overview, Instant now) {
		String vatRegistrationNumber = normalizeOptional(overview.supplierVatRegistrationNumber());
		if (vatRegistrationNumber != null) {
			Supplier supplier = supplierRepository.findByVatRegistrationNumber(vatRegistrationNumber).orElse(null);
			if (supplier != null) {
				return supplier;
			}
		}

		String registrationNumber = normalizeOptional(overview.supplierRegistrationNumber());
		if (registrationNumber != null) {
			Supplier supplier = supplierRepository.findByRegistrationNumber(registrationNumber).orElse(null);
			if (supplier != null) {
				return supplier;
			}
		}

		return createSupplierFromSef(overview, now);
	}

	private Supplier createSupplierFromSef(SefPurchaseInvoiceOverviewDto overview, Instant now) {
		String name = normalizeOptional(overview.supplierName());
		if (name == null) {
			return null;
		}

		Supplier supplier = new Supplier(
				UUID.randomUUID(),
				generateSupplierCode(name, overview.supplierVatRegistrationNumber(), overview.supplierRegistrationNumber()),
				name,
				normalizeOptional(overview.supplierRegistrationNumber()),
				normalizeOptional(overview.supplierVatRegistrationNumber()),
				null,
				null,
				overview.currency() == null || overview.currency().isBlank() ? "RSD" : overview.currency().trim(),
				true,
				now
		);
		return supplierRepository.save(supplier);
	}

	private String generateSupplierCode(String name, String vatRegistrationNumber, String registrationNumber) {
		String identifier = normalizeOptional(vatRegistrationNumber);
		if (identifier == null) {
			identifier = normalizeOptional(registrationNumber);
		}
		if (identifier != null) {
			return "SEF_" + identifier.replaceAll("[^A-Za-z0-9]", "");
		}

		String base = name.toUpperCase()
				.replaceAll("[^A-Z0-9]+", "_")
				.replaceAll("^_+|_+$", "");
		if (base.isBlank()) {
			base = "SUPPLIER";
		}
		if (base.length() > 42) {
			base = base.substring(0, 42);
		}
		return "SEF_" + base;
	}

	private String normalizeOptional(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	private String toJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Failed to serialize SEF payload.", exception);
		}
	}

	private int downloadMissingDocument(SefConnection connection, SefInvoice invoice, SefInvoiceDocumentType documentType, String contentType, String extension) {
		return downloadOrLoadDocument(connection, invoice, documentType, contentType, extension).created() ? 1 : 0;
	}

	private DocumentDownload downloadOrLoadDocument(SefConnection connection, SefInvoice invoice, SefInvoiceDocumentType documentType, String contentType, String extension) {
		return documentRepository.findByInvoiceIdAndDocumentType(invoice.getId(), documentType)
				.map(document -> new DocumentDownload(document.getContent(), false))
				.orElseGet(() -> downloadAndSaveDocument(connection, invoice, documentType, contentType, extension));
	}

	private DocumentDownload downloadAndSaveDocument(SefConnection connection, SefInvoice invoice, SefInvoiceDocumentType documentType, String contentType, String extension) {
		byte[] content = switch (documentType) {
			case UBL_XML -> sefClient.downloadPurchaseInvoiceXml(connection.getApiKeySecret(), invoice.getSefInvoiceId());
			case EXTENDED_PDF -> sefClient.downloadPurchaseInvoicePdf(connection.getApiKeySecret(), invoice.getSefInvoiceId());
			case SIGNATURE, STATUS_HISTORY_PDF -> throw new IllegalArgumentException("Unsupported purchase invoice download type: " + documentType);
		};

		documentRepository.save(new SefInvoiceDocument(
				UUID.randomUUID(),
				invoice,
				documentType,
				"sef-purchase-" + invoice.getSefInvoiceId() + "." + extension,
				contentType,
				sha256(content),
				content,
				Instant.now(clock)
		));
		return new DocumentDownload(content, true);
	}

	private void refreshParsedInvoiceData(SefInvoice invoice, byte[] xml) {
		SefUblInvoiceParser.ParsedInvoice parsedInvoice = ublInvoiceParser.parse(xml);

		lineRepository.deleteByInvoiceId(invoice.getId());
		vatSummaryRepository.deleteByInvoiceId(invoice.getId());
		paymentRepository.deleteByInvoiceId(invoice.getId());
		lineRepository.flush();
		vatSummaryRepository.flush();
		paymentRepository.flush();

		lineRepository.saveAll(parsedInvoice.lines().stream()
				.map(line -> new SefInvoiceLine(
						UUID.randomUUID(),
						invoice,
						line.lineNumber(),
						line.externalLineId(),
						line.itemName() == null ? "Unknown item" : line.itemName(),
						line.itemDescription(),
						line.sellerItemId(),
						line.buyerItemId(),
						line.unitCode(),
						line.quantity(),
						line.unitPrice(),
						line.discountAmount(),
						line.lineAmountWithoutVat(),
						line.vatCategory(),
						line.vatRate(),
						line.vatAmount(),
						toJson(line)
				))
				.toList());

		vatSummaryRepository.saveAll(parsedInvoice.vatSummaries().stream()
				.map(vatSummary -> new SefInvoiceVatSummary(
						UUID.randomUUID(),
						invoice,
						vatSummary.vatCategory(),
						vatSummary.vatRate(),
						vatSummary.taxableAmount(),
						vatSummary.vatAmount(),
						vatSummary.exemptionReasonCode(),
						vatSummary.exemptionReason(),
						toJson(vatSummary)
				))
				.toList());

		paymentRepository.saveAll(parsedInvoice.payments().stream()
				.map(payment -> new SefInvoicePayment(
						UUID.randomUUID(),
						invoice,
						payment.paymentMeansCode(),
						payment.paymentId(),
						payment.accountNumber(),
						payment.accountName(),
						payment.modelNumber(),
						payment.referenceNumber(),
						invoice.getTotalAmount(),
						invoice.getCurrency(),
						invoice.getDueAt(),
						toJson(payment)
				))
				.toList());
	}

	private String sha256(byte[] content) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available.", exception);
		}
	}

	private record DocumentDownload(byte[] content, boolean created) {
	}
}
