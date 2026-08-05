package com.megabike.inventory.api;

import com.megabike.inventory.application.InventoryService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/internal/inventory")
public class InventoryController {

	private final InventoryService inventoryService;

	public InventoryController(InventoryService inventoryService) {
		this.inventoryService = inventoryService;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('STOCK_RECEIVE')")
	public InventoryPageResponse list(
			@RequestParam(required = false) String query,
			@RequestParam(required = false) UUID stockLocationId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size
	) {
		return inventoryService.list(query, stockLocationId, page, size);
	}

	@GetMapping("/products/{productId}")
	@PreAuthorize("hasAuthority('STOCK_RECEIVE')")
	public InventoryItemResponse getProductInventory(
			@PathVariable UUID productId,
			@RequestParam UUID stockLocationId
	) {
		return inventoryService.getProductInventory(productId, stockLocationId);
	}

	@PostMapping("/adjustments")
	@PreAuthorize("hasAuthority('STOCK_ADJUST')")
	public StockMovementResponse adjust(@Valid @RequestBody StockAdjustmentRequest request, Authentication authentication) {
		return inventoryService.adjust(request, authentication.getName());
	}
}
