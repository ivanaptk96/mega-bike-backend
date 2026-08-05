package com.megabike.inventory.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StockLocationRepository extends JpaRepository<StockLocation, UUID> {
}
