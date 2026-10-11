package com.megabike.inventory.api;
import com.megabike.inventory.application.GoodsReceiptService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/api/internal/goods-receipts")
@PreAuthorize("hasAuthority('STOCK_RECEIVE') and hasAuthority('SUPPLIER_MANAGE')")
public class GoodsReceiptController {
	private final GoodsReceiptService service;
	public GoodsReceiptController(GoodsReceiptService service) { this.service = service; }
	@GetMapping public Page<GoodsReceiptResponse.Summary> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return service.list(page, size); }
	@GetMapping("/locations") public List<GoodsReceiptResponse.Location> locations() { return service.locations(); }
	@GetMapping("/{invoiceId}") public GoodsReceiptResponse get(@PathVariable UUID invoiceId) { return service.get(invoiceId); }
	@PostMapping("/{invoiceId}/start") public GoodsReceiptResponse start(@PathVariable UUID invoiceId, @Valid @RequestBody GoodsReceiptRequest request) { return service.start(invoiceId, request); }
	@PostMapping("/{invoiceId}/approve")
	@PreAuthorize("hasAuthority('STOCK_RECEIVE') and hasAuthority('SUPPLIER_MANAGE') and hasAuthority('PRODUCT_WRITE')")
	public GoodsReceiptResponse approve(@PathVariable UUID invoiceId, @Valid @RequestBody GoodsReceiptRequest.Approval request, Authentication auth) { return service.approve(invoiceId, request, auth.getName()); }
}
