package com.megabike.sef.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface SefInvoiceRepository extends JpaRepository<SefInvoice, UUID> {

	Optional<SefInvoice> findByConnectionIdAndDirectionAndSefInvoiceId(UUID connectionId, SefInvoiceDirection direction, Long sefInvoiceId);

	Optional<SefInvoice> findByIdAndConnectionIdAndDirection(UUID id, UUID connectionId, SefInvoiceDirection direction);

	Optional<SefInvoice> findByIdAndConnectionId(UUID id, UUID connectionId);

	List<SefInvoice> findByConnectionIdAndDirectionOrderBySentAtDescImportedAtDesc(UUID connectionId, SefInvoiceDirection direction);

	List<SefInvoice> findByConnectionIdOrderBySentAtDescImportedAtDesc(UUID connectionId);

	@Query("""
			select invoice
			from SefInvoice invoice
			where invoice.supplier.id = :supplierId
			order by coalesce(invoice.sentAt, invoice.importedAt) desc, invoice.importedAt desc
			""")
	List<SefInvoice> findBySupplierIdNewestFirst(@Param("supplierId") UUID supplierId);
}
