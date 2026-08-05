package com.megabike.sef.api;

import com.megabike.sef.application.SefIntegrationService;
import com.megabike.sef.application.SefPurchaseInvoiceSyncService;
import com.megabike.sef.domain.SefSyncRun;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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
}
