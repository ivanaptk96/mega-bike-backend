package com.megabike.catalog.domain;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

	boolean existsByProductCode(String productCode);

	boolean existsByProductCodeAndIdNot(String productCode, UUID id);

	boolean existsByBarcode(String barcode);

	boolean existsByBarcodeAndIdNot(String barcode, UUID id);

	@EntityGraph(attributePaths = "category")
	Optional<Product> findWithCategoryById(UUID id);
}
