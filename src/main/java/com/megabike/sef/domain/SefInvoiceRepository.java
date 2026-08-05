package com.megabike.sef.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface SefInvoiceRepository extends JpaRepository<SefInvoice, UUID> {

	Optional<SefInvoice> findByConnectionIdAndDirectionAndSefInvoiceId(UUID connectionId, SefInvoiceDirection direction, Long sefInvoiceId);

	List<SefInvoice> findByConnectionIdAndDirectionOrderBySentAtDescImportedAtDesc(UUID connectionId, SefInvoiceDirection direction);
}
