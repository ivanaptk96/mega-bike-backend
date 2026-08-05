package com.megabike.inventory.api;

import com.megabike.inventory.application.InventoryService;
import com.megabike.inventory.domain.MovementType;
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

	@GetMapping("/movements")
	@PreAuthorize("hasAuthority('STOCK_RECEIVE')")
	public StockMovementPageResponse listMovements(
			@RequestParam(required = false) UUID productId,
			@RequestParam(required = false) UUID stockLocationId,
			@RequestParam(required = false) MovementType movementType,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size
	) {
		return inventoryService.listMovements(productId, stockLocationId, movementType, page, size);
	}

	@GetMapping("/products/{productId}/movements")
	@PreAuthorize("hasAuthority('STOCK_RECEIVE')")
	public StockMovementPageResponse listProductMovements(
			@PathVariable UUID productId,
			@RequestParam(required = false) UUID stockLocationId,
			@RequestParam(required = false) MovementType movementType,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size
	) {
		return inventoryService.listMovements(productId, stockLocationId, movementType, page, size);
	}

	@PostMapping("/adjustments")
	@PreAuthorize("hasAuthority('STOCK_ADJUST')")
	public StockMovementResponse adjust(@Valid @RequestBody StockAdjustmentRequest request, Authentication authentication) {
		return inventoryService.adjust(request, authentication.getName());
	}
}
