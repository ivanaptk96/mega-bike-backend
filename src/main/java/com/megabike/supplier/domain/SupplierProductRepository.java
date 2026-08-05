package com.megabike.supplier.domain;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface SupplierProductRepository extends JpaRepository<SupplierProduct, UUID>, JpaSpecificationExecutor<SupplierProduct> {
	boolean existsBySupplierIdAndSupplierCode(UUID supplierId, String supplierCode);
	boolean existsBySupplierIdAndSupplierCodeAndIdNot(UUID supplierId, String supplierCode, UUID id);

	@EntityGraph(attributePaths = {"supplier", "product"})
	Optional<SupplierProduct> findWithSupplierAndProductById(UUID id);

	@EntityGraph(attributePaths = {"supplier", "product"})
	Optional<SupplierProduct> findWithSupplierAndProductBySupplierIdAndSupplierCode(UUID supplierId, String supplierCode);
}
