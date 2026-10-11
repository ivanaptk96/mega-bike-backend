package com.megabike.sef.api;

import com.megabike.sef.application.SefIntegrationService;
import com.megabike.sef.application.SefPurchaseInvoiceSyncService;
import com.megabike.sef.domain.SefInvoiceDirection;
import com.megabike.sef.domain.SefSyncRun;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/internal/sef")
public class SefController {

	private final SefIntegrationService sefIntegrationService;
	private final SefPurchaseInvoiceSyncService purchaseInvoiceSyncService;

	public SefController(SefIntegrationService sefIntegrationService, SefPurchaseInvoiceSyncService purchaseInvoiceSyncService) {
		this.sefIntegrationService = sefIntegrationService;
		this.purchaseInvoiceSyncService = purchaseInvoiceSyncService;
	}

	@PostMapping("/connections")
	@PreAuthorize("hasAuthority('SUPPLIER_MANAGE')")
	public SefConnectionResponse createConnection(@Valid @RequestBody SefConnectionRequest request) {
		return sefIntegrationService.createConnection(request);
	}

	@GetMapping("/connections")
	@PreAuthorize("hasAuthority('SUPPLIER_MANAGE')")
	public List<SefConnectionResponse> listConnections() {
		return sefIntegrationService.listConnections();
	}

	@GetMapping("/connections/{connectionId}")
	@PreAuthorize("hasAuthority('SUPPLIER_MANAGE')")
	public SefConnectionResponse getConnection(@PathVariable UUID connectionId) {
		return sefIntegrationService.getConnection(connectionId);
	}

	@PutMapping("/connections/{connectionId}")
	@PreAuthorize("hasAuthority('SUPPLIER_MANAGE')")
	public SefConnectionResponse updateConnection(@PathVariable UUID connectionId, @Valid @RequestBody SefConnectionRequest request) {
		return sefIntegrationService.updateConnection(connectionId, request);
	}

	@PostMapping("/connections/{connectionId}/purchase-invoices/sync")
	@PreAuthorize("hasAuthority('SUPPLIER_MANAGE')")
	public SefSyncRunResponse syncPurchaseInvoices(
			@PathVariable UUID connectionId,
			@Valid @RequestBody SefPurchaseInvoiceSyncRequest request
	) {
		SefSyncRun syncRun = purchaseInvoiceSyncService.syncPurchaseInvoices(
				connectionId,
				request.dateFrom(),
				request.dateTo(),
				request.downloadDocuments()
		);
		return sefIntegrationService.toSyncRunResponse(syncRun);
	}

	@GetMapping("/connections/{connectionId}/purchase-invoices")
	@PreAuthorize("hasAuthority('SUPPLIER_MANAGE')")
	public List<SefInvoiceResponse> listPurchaseInvoices(@PathVariable UUID connectionId) {
		return sefIntegrationService.listPurchaseInvoices(connectionId);
	}

	@GetMapping("/connections/{connectionId}/invoices")
	@PreAuthorize("hasAuthority('SUPPLIER_MANAGE')")
	public List<SefInvoiceResponse> listInvoices(
			@PathVariable UUID connectionId,
			@RequestParam(required = false) SefInvoiceDirection direction
	) {
		return sefIntegrationService.listInvoices(connectionId, direction);
	}

	@GetMapping("/suppliers/{supplierId}/invoices")
	@PreAuthorize("hasAuthority('SUPPLIER_MANAGE')")
	public List<SefInvoiceResponse> listSupplierInvoices(@PathVariable UUID supplierId) {
		return sefIntegrationService.listSupplierInvoices(supplierId);
	}

	@GetMapping("/connections/{connectionId}/invoices/{invoiceId}")
	@PreAuthorize("hasAuthority('SUPPLIER_MANAGE')")
	public SefInvoiceDetailResponse getInvoice(
			@PathVariable UUID connectionId,
			@PathVariable UUID invoiceId
	) {
		return sefIntegrationService.getInvoice(connectionId, invoiceId);
	}

	@GetMapping("/connections/{connectionId}/invoices/{invoiceId}/documents/{documentId}")
	@PreAuthorize("hasAuthority('SUPPLIER_MANAGE')")
	public ResponseEntity<byte[]> getInvoiceDocument(
			@PathVariable UUID connectionId,
			@PathVariable UUID invoiceId,
			@PathVariable UUID documentId,
			@RequestParam(defaultValue = "false") boolean download
	) {
		return documentResponse(
				sefIntegrationService.getInvoiceDocument(connectionId, invoiceId, documentId),
				download
		);
	}

	@GetMapping("/connections/{connectionId}/purchase-invoices/{invoiceId}")
	@PreAuthorize("hasAuthority('SUPPLIER_MANAGE')")
	public SefInvoiceDetailResponse getPurchaseInvoice(
			@PathVariable UUID connectionId,
			@PathVariable UUID invoiceId
	) {
		return sefIntegrationService.getPurchaseInvoice(connectionId, invoiceId);
	}

	@GetMapping("/connections/{connectionId}/purchase-invoices/{invoiceId}/documents/{documentId}")
	@PreAuthorize("hasAuthority('SUPPLIER_MANAGE')")
	public ResponseEntity<byte[]> getPurchaseInvoiceDocument(
			@PathVariable UUID connectionId,
			@PathVariable UUID invoiceId,
			@PathVariable UUID documentId,
			@RequestParam(defaultValue = "false") boolean download
	) {
		return documentResponse(
				sefIntegrationService.getPurchaseInvoiceDocument(connectionId, invoiceId, documentId),
				download
		);
	}

	private ResponseEntity<byte[]> documentResponse(SefInvoiceDocumentContent document, boolean download) {
		ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
				.filename(document.filename(), StandardCharsets.UTF_8)
				.build();
		return ResponseEntity.ok()
				.contentType(parseMediaType(document.contentType()))
				.contentLength(document.content().length)
				.header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
				.header(HttpHeaders.CACHE_CONTROL, "private, no-store")
				.body(document.content());
	}

	private MediaType parseMediaType(String contentType) {
		try {
			return MediaType.parseMediaType(contentType);
		} catch (InvalidMediaTypeException exception) {
			return MediaType.APPLICATION_OCTET_STREAM;
		}
	}
}
