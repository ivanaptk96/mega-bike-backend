package com.megabike.sef.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SefConnectionRepository extends JpaRepository<SefConnection, UUID> {

	List<SefConnection> findByEnabledTrue();

	Optional<SefConnection> findByCompanyTaxId(String companyTaxId);

	boolean existsByCompanyTaxId(String companyTaxId);

	boolean existsByCompanyTaxIdAndIdNot(String companyTaxId, UUID id);
}
