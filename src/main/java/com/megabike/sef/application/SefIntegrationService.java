package com.megabike.sef.application;

import com.megabike.sef.api.SefConnectionRequest;
import com.megabike.sef.api.SefConnectionResponse;
import com.megabike.sef.api.SefInvoiceDetailResponse;
import com.megabike.sef.api.SefInvoiceDocumentContent;
import com.megabike.sef.api.SefInvoicePageResponse;
import com.megabike.sef.api.SefInvoiceResponse;
import com.megabike.sef.api.SefSyncRunResponse;
import com.megabike.sef.domain.SefConnection;
import com.megabike.sef.domain.SefConnectionRepository;
import com.megabike.sef.domain.SefInvoice;
import com.megabike.sef.domain.SefInvoiceDirection;
import com.megabike.sef.domain.SefInvoiceDocument;
import com.megabike.sef.domain.SefInvoiceDocumentRepository;
import com.megabike.sef.domain.SefInvoiceLine;
import com.megabike.sef.domain.SefInvoiceLineRepository;
import com.megabike.sef.domain.SefInvoicePayment;
import com.megabike.sef.domain.SefInvoicePaymentRepository;
import com.megabike.sef.domain.SefInvoiceRepository;
import com.megabike.sef.domain.SefInvoiceVatSummary;
import com.megabike.sef.domain.SefInvoiceVatSummaryRepository;
import com.megabike.sef.domain.SefSyncRun;
import com.megabike.shared.api.ApiException;
import com.megabike.supplier.domain.SupplierClassification;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class SefIntegrationService {

	private final SefConnectionRepository connectionRepository;
	private final SefInvoiceRepository invoiceRepository;
	private final SefInvoiceDocumentRepository documentRepository;
	private final SefInvoiceLineRepository lineRepository;
	private final SefInvoiceVatSummaryRepository vatSummaryRepository;
	private final SefInvoicePaymentRepository paymentRepository;
	private final Clock clock;

	public SefIntegrationService(
			SefConnectionRepository connectionRepository,
			SefInvoiceRepository invoiceRepository,
			SefInvoiceDocumentRepository documentRepository,
			SefInvoiceLineRepository lineRepository,
			SefInvoiceVatSummaryRepository vatSummaryRepository,
			SefInvoicePaymentRepository paymentRepository
	) {
		this.connectionRepository = connectionRepository;
		this.invoiceRepository = invoiceRepository;
		this.documentRepository = documentRepository;
		this.lineRepository = lineRepository;
		this.vatSummaryRepository = vatSummaryRepository;
		this.paymentRepository = paymentRepository;
		this.clock = Clock.systemUTC();
	}

	@Transactional
	public SefConnectionResponse createConnection(SefConnectionRequest request) {
		String companyTaxId = normalizeRequired(request.companyTaxId());
		if (connectionRepository.existsByCompanyTaxId(companyTaxId)) {
			throw new ApiException(HttpStatus.CONFLICT, "SEF_CONNECTION_EXISTS", "SEF connection already exists for this company tax ID.");
		}

		SefConnection connection = new SefConnection(
				UUID.randomUUID(),
				companyTaxId,
				normalizeOptional(request.companyRegistrationNumber()),
				normalizeRequired(request.companyName()),
				normalizeRequired(request.apiKey()),
				request.enabled(),
				Instant.now(clock)
		);
		return toConnectionResponse(connectionRepository.save(connection));
	}

	@Transactional(readOnly = true)
	public List<SefConnectionResponse> listConnections() {
		return connectionRepository.findAll().stream()
				.map(this::toConnectionResponse)
				.toList();
	}

	@Transactional(readOnly = true)
	public SefConnectionResponse getConnection(UUID id) {
		return toConnectionResponse(findConnection(id));
	}

	@Transactional
	public SefConnectionResponse updateConnection(UUID id, SefConnectionRequest request) {
		SefConnection connection = findConnection(id);
		String companyTaxId = normalizeRequired(request.companyTaxId());
		if (connectionRepository.existsByCompanyTaxIdAndIdNot(companyTaxId, id)) {
			throw new ApiException(HttpStatus.CONFLICT, "SEF_CONNECTION_EXISTS", "SEF connection already exists for this company tax ID.");
		}

		connection.update(
				companyTaxId,
				normalizeOptional(request.companyRegistrationNumber()),
				normalizeRequired(request.companyName()),
				normalizeRequired(request.apiKey()),
				request.enabled(),
				Instant.now(clock)
		);
		return toConnectionResponse(connection);
	}

	@Transactional(readOnly = true)
	public List<SefInvoiceResponse> listPurchaseInvoices(UUID connectionId) {
		findConnection(connectionId);
		return invoiceRepository.findByConnectionIdAndDirectionOrderBySentAtDescImportedAtDesc(
				connectionId,
				SefInvoiceDirection.PURCHASE
		).stream().map(this::toInvoiceResponse).toList();
	}

	@Transactional(readOnly = true)
	public SefInvoicePageResponse listInvoices(
			UUID connectionId,
			SefInvoiceDirection direction,
			SupplierClassification classification,
			int page,
			int size
	) {
		findConnection(connectionId);
		Page<SefInvoiceResponse> invoices = invoiceRepository.findAll(
				invoiceSpecification(connectionId, direction, classification),
				PageRequest.of(
						validatePage(page),
						validateSize(size),
						Sort.by(Sort.Order.desc("sentAt"), Sort.Order.desc("importedAt"))
				)
		).map(this::toInvoiceResponse);

		return new SefInvoicePageResponse(
				invoices.getContent(),
				invoices.getNumber(),
				invoices.getSize(),
				invoices.getTotalElements(),
				invoices.getTotalPages(),
				invoices.isFirst(),
				invoices.isLast()
		);
	}

	@Transactional(readOnly = true)
	public List<SefInvoiceResponse> listSupplierInvoices(UUID supplierId) {
		return invoiceRepository.findBySupplierIdNewestFirst(supplierId).stream()
				.map(this::toInvoiceResponse)
				.toList();
	}

	@Transactional(readOnly = true)
	public SefInvoiceDetailResponse getPurchaseInvoice(UUID connectionId, UUID invoiceId) {
		return getInvoiceDetail(findPurchaseInvoice(connectionId, invoiceId));
	}

	@Transactional(readOnly = true)
	public SefInvoiceDetailResponse getInvoice(UUID connectionId, UUID invoiceId) {
		return getInvoiceDetail(findInvoice(connectionId, invoiceId));
	}

	private SefInvoiceDetailResponse getInvoiceDetail(SefInvoice invoice) {
		UUID invoiceId = invoice.getId();
		return new SefInvoiceDetailResponse(
				toInvoiceResponse(invoice),
				lineRepository.findByInvoiceIdOrderByLineNumberAsc(invoiceId).stream().map(this::toLineResponse).toList(),
				vatSummaryRepository.findByInvoiceIdOrderByVatRateAsc(invoiceId).stream().map(this::toVatSummaryResponse).toList(),
				paymentRepository.findByInvoiceIdOrderByIdAsc(invoiceId).stream().map(this::toPaymentResponse).toList(),
				documentRepository.findByInvoiceIdOrderByDocumentTypeAsc(invoiceId).stream().map(this::toDocumentResponse).toList()
		);
	}

	@Transactional(readOnly = true)
	public SefInvoiceDocumentContent getPurchaseInvoiceDocument(UUID connectionId, UUID invoiceId, UUID documentId) {
		findPurchaseInvoice(connectionId, invoiceId);
		return findDocument(invoiceId, documentId);
	}

	@Transactional(readOnly = true)
	public SefInvoiceDocumentContent getInvoiceDocument(UUID connectionId, UUID invoiceId, UUID documentId) {
		findInvoice(connectionId, invoiceId);
		return findDocument(invoiceId, documentId);
	}

	private SefInvoiceDocumentContent findDocument(UUID invoiceId, UUID documentId) {
		SefInvoiceDocument document = documentRepository.findByIdAndInvoiceId(documentId, invoiceId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SEF_DOCUMENT_NOT_FOUND", "SEF invoice document was not found."));
		return new SefInvoiceDocumentContent(document.getFilename(), document.getContentType(), document.getContent());
	}

	public SefSyncRunResponse toSyncRunResponse(SefSyncRun syncRun) {
		return new SefSyncRunResponse(
				syncRun.getId(),
				syncRun.getConnection().getId(),
				syncRun.getDirection(),
				syncRun.getStatus(),
				syncRun.getDateFrom(),
				syncRun.getDateTo(),
				syncRun.getStartedAt(),
				syncRun.getFinishedAt(),
				syncRun.getInvoiceCount(),
				syncRun.getDocumentCount(),
				syncRun.getErrorMessage()
		);
	}

	private SefConnection findConnection(UUID id) {
		return connectionRepository.findById(id)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SEF_CONNECTION_NOT_FOUND", "SEF connection was not found."));
	}

	private SefInvoice findPurchaseInvoice(UUID connectionId, UUID invoiceId) {
		findConnection(connectionId);
		return invoiceRepository.findByIdAndConnectionIdAndDirection(invoiceId, connectionId, SefInvoiceDirection.PURCHASE)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SEF_INVOICE_NOT_FOUND", "SEF purchase invoice was not found."));
	}

	private SefInvoice findInvoice(UUID connectionId, UUID invoiceId) {
		findConnection(connectionId);
		return invoiceRepository.findByIdAndConnectionId(invoiceId, connectionId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SEF_INVOICE_NOT_FOUND", "SEF invoice was not found."));
	}

	private SefConnectionResponse toConnectionResponse(SefConnection connection) {
		return new SefConnectionResponse(
				connection.getId(),
				connection.getCompanyTaxId(),
				connection.getCompanyRegistrationNumber(),
				connection.getCompanyName(),
				connection.isEnabled(),
				connection.getLastPurchaseSyncAt(),
				connection.getCreatedAt(),
				connection.getUpdatedAt()
		);
	}

	private SefInvoiceResponse toInvoiceResponse(SefInvoice invoice) {
		return new SefInvoiceResponse(
				invoice.getId(),
				invoice.getConnection().getId(),
				invoice.getSefInvoiceId(),
				invoice.getSupplier() == null ? null : invoice.getSupplier().getId(),
				invoice.getSupplier() == null ? null : invoice.getSupplier().getName(),
				invoice.getSupplier() == null ? null : invoice.getSupplier().getDisplayName(),
				invoice.getSupplier() == null ? null : invoice.getSupplier().getClassification(),
				invoice.getDirection(),
				invoice.getGlobalUniqueId(),
				invoice.getCirInvoiceId(),
				invoice.getInvoiceNumber(),
				invoice.getDocumentType(),
				invoice.getStatus(),
				invoice.getCounterpartyName(),
				invoice.getCounterpartyRegistrationNumber(),
				invoice.getCounterpartyVatRegistrationNumber(),
				invoice.getCurrency(),
				invoice.getAmountWithoutVat(),
				invoice.getVatAmount(),
				invoice.getTotalAmount(),
				invoice.getSentAt(),
				invoice.getDueAt(),
				invoice.getImportedAt(),
				invoice.getUpdatedAt()
		);
	}

	private SefInvoiceDetailResponse.LineResponse toLineResponse(SefInvoiceLine line) {
		return new SefInvoiceDetailResponse.LineResponse(
				line.getId(),
				line.getLineNumber(),
				line.getExternalLineId(),
				line.getItemName(),
				line.getItemDescription(),
				line.getSellerItemId(),
				line.getBuyerItemId(),
				line.getUnitCode(),
				line.getQuantity(),
				line.getUnitPrice(),
				line.getDiscountAmount(),
				line.getLineAmountWithoutVat(),
				line.getVatCategory(),
				line.getVatRate(),
				line.getVatAmount()
		);
	}

	private SefInvoiceDetailResponse.VatSummaryResponse toVatSummaryResponse(SefInvoiceVatSummary summary) {
		return new SefInvoiceDetailResponse.VatSummaryResponse(
				summary.getId(),
				summary.getVatCategory(),
				summary.getVatRate(),
				summary.getTaxableAmount(),
				summary.getVatAmount(),
				summary.getExemptionReasonCode(),
				summary.getExemptionReason()
		);
	}

	private SefInvoiceDetailResponse.PaymentResponse toPaymentResponse(SefInvoicePayment payment) {
		return new SefInvoiceDetailResponse.PaymentResponse(
				payment.getId(),
				payment.getPaymentMeansCode(),
				payment.getPaymentId(),
				payment.getAccountNumber(),
				payment.getAccountName(),
				payment.getModelNumber(),
				payment.getReferenceNumber(),
				payment.getAmount(),
				payment.getCurrency(),
				payment.getDueAt()
		);
	}

	private SefInvoiceDetailResponse.DocumentResponse toDocumentResponse(SefInvoiceDocument document) {
		return new SefInvoiceDetailResponse.DocumentResponse(
				document.getId(),
				document.getDocumentType(),
				document.getFilename(),
				document.getContentType(),
				document.getContent().length,
				document.getSha256Hash(),
				document.getDownloadedAt()
		);
	}

	private Specification<SefInvoice> invoiceSpecification(
			UUID connectionId,
			SefInvoiceDirection direction,
			SupplierClassification classification
	) {
		return (root, criteriaQuery, criteriaBuilder) -> {
			List<Predicate> predicates = new ArrayList<>();
			predicates.add(criteriaBuilder.equal(root.get("connection").get("id"), connectionId));
			if (direction != null) {
				predicates.add(criteriaBuilder.equal(root.get("direction"), direction));
			}
			if (classification != null) {
				Join<Object, Object> supplier = root.join("supplier", JoinType.LEFT);
				Predicate hasClassification = criteriaBuilder.equal(
						supplier.get("classification"),
						classification
				);
				predicates.add(classification == SupplierClassification.UNCLASSIFIED
						? criteriaBuilder.or(criteriaBuilder.isNull(supplier.get("id")), hasClassification)
						: hasClassification);
			}
			return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
		};
	}

	private int validatePage(int page) {
		if (page < 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE_REQUEST", "Page must be greater than or equal to 0.");
		}
		return page;
	}

	private int validateSize(int size) {
		if (size < 1 || size > 100) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE_REQUEST", "Size must be between 1 and 100.");
		}
		return size;
	}

	private String normalizeRequired(String value) {
		if (value == null || value.isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SEF_REQUEST", "Required SEF field is missing.");
		}
		return value.trim();
	}

	private String normalizeOptional(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
