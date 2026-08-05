package com.megabike.inventory.application;

import com.megabike.catalog.domain.Product;
import com.megabike.catalog.domain.ProductRepository;
import com.megabike.inventory.api.InventoryItemResponse;
import com.megabike.inventory.api.InventoryPageResponse;
import com.megabike.inventory.api.StockAdjustmentRequest;
import com.megabike.inventory.api.StockMovementPageResponse;
import com.megabike.inventory.api.StockMovementResponse;
import com.megabike.inventory.domain.InventoryItem;
import com.megabike.inventory.domain.InventoryItemRepository;
import com.megabike.inventory.domain.MovementType;
import com.megabike.inventory.domain.StockLocation;
import com.megabike.inventory.domain.StockLocationRepository;
import com.megabike.inventory.domain.StockMovement;
import com.megabike.inventory.domain.StockMovementRepository;
import com.megabike.shared.api.ApiException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class InventoryService {

	private static final BigDecimal ZERO = new BigDecimal("0.000");

	private final InventoryItemRepository inventoryItemRepository;
	private final StockMovementRepository stockMovementRepository;
	private final ProductRepository productRepository;
	private final StockLocationRepository stockLocationRepository;
	private final Clock clock;

	public InventoryService(
			InventoryItemRepository inventoryItemRepository,
			StockMovementRepository stockMovementRepository,
			ProductRepository productRepository,
			StockLocationRepository stockLocationRepository
	) {
		this.inventoryItemRepository = inventoryItemRepository;
		this.stockMovementRepository = stockMovementRepository;
		this.productRepository = productRepository;
		this.stockLocationRepository = stockLocationRepository;
		this.clock = Clock.systemUTC();
	}

	@Transactional(readOnly = true)
	public InventoryPageResponse list(String query, UUID stockLocationId, int page, int size) {
		Page<InventoryItemResponse> items = inventoryItemRepository.findAll(
				searchSpecification(normalizeOptional(query), stockLocationId),
				PageRequest.of(validatePage(page), validateSize(size), Sort.by("product.name").ascending())
		).map(this::toInventoryItemResponse);

		return new InventoryPageResponse(
				items.getContent(),
				items.getNumber(),
				items.getSize(),
				items.getTotalElements(),
				items.getTotalPages(),
				items.isFirst(),
				items.isLast()
		);
	}

	@Transactional(readOnly = true)
	public InventoryItemResponse getProductInventory(UUID productId, UUID stockLocationId) {
		return toInventoryItemResponse(findInventoryItem(productId, stockLocationId));
	}

	@Transactional
	public StockMovementResponse adjust(StockAdjustmentRequest request, String createdBy) {
		Instant now = Instant.now(clock);
		BigDecimal quantityDelta = request.quantityDelta();

		if (quantityDelta.compareTo(BigDecimal.ZERO) == 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVENTORY_ADJUSTMENT_INVALID", "Quantity delta must not be zero.");
		}

		Product product = productRepository.findById(request.productId())
				.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVENTORY_PRODUCT_NOT_FOUND", "Inventory product was not found."));
		StockLocation stockLocation = stockLocationRepository.findById(request.stockLocationId())
				.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "STOCK_LOCATION_NOT_FOUND", "Stock location was not found."));

		InventoryItem item = inventoryItemRepository
				.findWithProductAndStockLocationByProductIdAndStockLocationId(product.getId(), stockLocation.getId())
				.orElseGet(() -> new InventoryItem(UUID.randomUUID(), product, stockLocation, ZERO, now));

		BigDecimal quantityAfter = item.getQuantityOnHand().add(quantityDelta);
		if (quantityAfter.compareTo(BigDecimal.ZERO) < 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVENTORY_NEGATIVE_STOCK", "Inventory quantity cannot become negative.");
		}

		item.adjust(quantityDelta, now);
		inventoryItemRepository.save(item);

		StockMovement movement = new StockMovement(
				UUID.randomUUID(),
				product,
				stockLocation,
				MovementType.ADJUSTMENT,
				quantityDelta,
				quantityAfter,
				request.reason().trim(),
				normalizeOptional(request.referenceText()),
				createdBy,
				now
		);

		return toStockMovementResponse(stockMovementRepository.save(movement));
	}

	@Transactional(readOnly = true)
	public StockMovementPageResponse listMovements(
			UUID productId,
			UUID stockLocationId,
			MovementType movementType,
			int page,
			int size
	) {
		Page<StockMovementResponse> movements = stockMovementRepository.findAll(
				movementSpecification(productId, stockLocationId, movementType),
				PageRequest.of(validatePage(page), validateSize(size), Sort.by("createdAt").descending())
		).map(this::toStockMovementResponse);

		return new StockMovementPageResponse(
				movements.getContent(),
				movements.getNumber(),
				movements.getSize(),
				movements.getTotalElements(),
				movements.getTotalPages(),
				movements.isFirst(),
				movements.isLast()
		);
	}

	private InventoryItem findInventoryItem(UUID productId, UUID stockLocationId) {
		return inventoryItemRepository.findWithProductAndStockLocationByProductIdAndStockLocationId(productId, stockLocationId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "INVENTORY_ITEM_NOT_FOUND", "Inventory item was not found."));
	}

	private InventoryItemResponse toInventoryItemResponse(InventoryItem item) {
		Product product = item.getProduct();
		StockLocation stockLocation = item.getStockLocation();
		return new InventoryItemResponse(
				item.getId(),
				product.getId(),
				product.getProductCode(),
				product.getName(),
				product.getBarcode(),
				stockLocation.getId(),
				stockLocation.getCode(),
				stockLocation.getName(),
				item.getQuantityOnHand(),
				item.getCreatedAt(),
				item.getUpdatedAt()
		);
	}

	private StockMovementResponse toStockMovementResponse(StockMovement movement) {
		Product product = movement.getProduct();
		StockLocation stockLocation = movement.getStockLocation();
		return new StockMovementResponse(
				movement.getId(),
				product.getId(),
				product.getProductCode(),
				product.getName(),
				stockLocation.getId(),
				stockLocation.getCode(),
				stockLocation.getName(),
				movement.getMovementType(),
				movement.getQuantityDelta(),
				movement.getQuantityAfter(),
				movement.getReason(),
				movement.getReferenceText(),
				movement.getCreatedBy(),
				movement.getCreatedAt()
		);
	}

	private Specification<InventoryItem> searchSpecification(String query, UUID stockLocationId) {
		return (root, criteriaQuery, criteriaBuilder) -> {
			List<Predicate> predicates = new ArrayList<>();

			if (stockLocationId != null) {
				predicates.add(criteriaBuilder.equal(root.get("stockLocation").get("id"), stockLocationId));
			}
			if (query != null) {
				String pattern = "%" + query.toLowerCase() + "%";
				predicates.add(criteriaBuilder.or(
						criteriaBuilder.like(criteriaBuilder.lower(root.get("product").get("name")), pattern),
						criteriaBuilder.like(criteriaBuilder.lower(root.get("product").get("productCode")), pattern),
						criteriaBuilder.like(criteriaBuilder.lower(root.get("product").get("barcode")), pattern)
				));
			}

			return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
		};
	}

	private Specification<StockMovement> movementSpecification(UUID productId, UUID stockLocationId, MovementType movementType) {
		return (root, criteriaQuery, criteriaBuilder) -> {
			List<Predicate> predicates = new ArrayList<>();

			if (productId != null) {
				predicates.add(criteriaBuilder.equal(root.get("product").get("id"), productId));
			}
			if (stockLocationId != null) {
				predicates.add(criteriaBuilder.equal(root.get("stockLocation").get("id"), stockLocationId));
			}
			if (movementType != null) {
				predicates.add(criteriaBuilder.equal(root.get("movementType"), movementType));
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

	private String normalizeOptional(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
