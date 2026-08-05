package com.megabike.sef.application;

import com.megabike.sef.api.SefConnectionRequest;
import com.megabike.sef.api.SefConnectionResponse;
import com.megabike.sef.api.SefInvoiceResponse;
import com.megabike.sef.api.SefSyncRunResponse;
import com.megabike.sef.domain.SefConnection;
import com.megabike.sef.domain.SefConnectionRepository;
import com.megabike.sef.domain.SefInvoice;
import com.megabike.sef.domain.SefInvoiceDirection;
import com.megabike.sef.domain.SefInvoiceRepository;
import com.megabike.sef.domain.SefSyncRun;
import com.megabike.shared.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SefIntegrationService {

	private final SefConnectionRepository connectionRepository;
	private final SefInvoiceRepository invoiceRepository;
	private final Clock clock;

	public SefIntegrationService(SefConnectionRepository connectionRepository, SefInvoiceRepository invoiceRepository) {
		this.connectionRepository = connectionRepository;
		this.invoiceRepository = invoiceRepository;
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
		return invoiceRepository.findByConnectionIdAndDirectionOrderBySentAtDescImportedAtDesc(connectionId, SefInvoiceDirection.PURCHASE)
				.stream()
				.map(this::toInvoiceResponse)
				.toList();
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
				invoice.getSefInvoiceId(),
				invoice.getSupplier() == null ? null : invoice.getSupplier().getId(),
				invoice.getSupplier() == null ? null : invoice.getSupplier().getName(),
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
