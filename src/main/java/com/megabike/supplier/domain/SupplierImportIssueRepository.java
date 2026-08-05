package com.megabike.supplier.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SupplierImportIssueRepository extends JpaRepository<SupplierImportIssue, UUID> {
	List<SupplierImportIssue> findAllByImportRunIdOrderByRowNumberAsc(UUID importRunId);
}
