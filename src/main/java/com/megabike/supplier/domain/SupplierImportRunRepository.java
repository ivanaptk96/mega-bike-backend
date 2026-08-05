package com.megabike.supplier.domain;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplierImportRunRepository extends JpaRepository<SupplierImportRun, UUID> {
	@EntityGraph(attributePaths = "supplier")
	Optional<SupplierImportRun> findWithSupplierById(UUID id);

	@EntityGraph(attributePaths = "supplier")
	List<SupplierImportRun> findAllBySupplierIdOrderByStartedAtDesc(UUID supplierId);
}
