package com.megabike.inventory.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID>, JpaSpecificationExecutor<StockMovement> {
}
