package com.megabike.catalog.domain;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

	@org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
	@org.springframework.data.jpa.repository.Query("select p from Product p where p.id = :id")
	Optional<Product> findLockedById(@org.springframework.data.repository.query.Param("id") UUID id);

	boolean existsByProductCode(String productCode);

	boolean existsByProductCodeAndIdNot(String productCode, UUID id);

	boolean existsByBarcode(String barcode);

	boolean existsByBarcodeAndIdNot(String barcode, UUID id);

	Optional<Product> findByBarcode(String barcode);

	@EntityGraph(attributePaths = "category")
	Optional<Product> findWithCategoryById(UUID id);
}
