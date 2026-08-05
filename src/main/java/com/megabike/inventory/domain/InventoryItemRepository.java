package com.megabike.inventory.domain;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID>, JpaSpecificationExecutor<InventoryItem> {

	@EntityGraph(attributePaths = {"product", "stockLocation"})
	Optional<InventoryItem> findWithProductAndStockLocationByProductIdAndStockLocationId(UUID productId, UUID stockLocationId);
}
