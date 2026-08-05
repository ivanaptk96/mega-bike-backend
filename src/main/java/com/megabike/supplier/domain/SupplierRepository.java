package com.megabike.supplier.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {
	boolean existsByCode(String code);
	boolean existsByCodeAndIdNot(String code, UUID id);
	List<Supplier> findAllByOrderByNameAsc();
}
