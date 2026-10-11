package com.megabike.supplier.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {
	@org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
	@org.springframework.data.jpa.repository.Query("select s from Supplier s where s.id = :id")
	Optional<Supplier> findLockedById(@org.springframework.data.repository.query.Param("id") UUID id);
	boolean existsByCode(String code);
	boolean existsByCodeAndIdNot(String code, UUID id);
	Optional<Supplier> findByVatRegistrationNumber(String vatRegistrationNumber);
	Optional<Supplier> findByRegistrationNumber(String registrationNumber);
	List<Supplier> findAllByOrderByNameAsc();
	List<Supplier> findByClassificationOrderByNameAsc(SupplierClassification classification);
}
