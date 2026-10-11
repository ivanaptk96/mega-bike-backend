package com.megabike.supplier.api;

import com.megabike.supplier.application.SupplierCatalogService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/internal")
@PreAuthorize("hasAuthority('SUPPLIER_MANAGE')")
public class SupplierCatalogController {

	private final SupplierCatalogService service;

	public SupplierCatalogController(SupplierCatalogService service) { this.service = service; }

	@GetMapping("/suppliers/{supplierId}/catalog")
	public SupplierCatalogService.CatalogPage list(@PathVariable UUID supplierId,
			@RequestParam(required = false) String query,
			@RequestParam(defaultValue = "false") boolean availableOnly,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return service.list(supplierId, query, availableOnly, page, size);
	}

	@GetMapping("/supplier-catalog-products/{id}")
	public Map<String, Object> details(@PathVariable UUID id) { return service.details(id); }

	@GetMapping("/supplier-catalog-images/{id}")
	public ResponseEntity<byte[]> image(@PathVariable UUID id) {
		var image = service.image(id);
		return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType()))
				.cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff").body(image.content());
	}
}
