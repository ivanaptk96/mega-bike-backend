package com.megabike.catalog.api;

import com.megabike.catalog.application.ProductImageService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/internal/products/{productId}/images")
@PreAuthorize("hasAuthority('PRODUCT_READ')")
public class ProductImageController {
	private final ProductImageService service;
	public ProductImageController(ProductImageService service) { this.service = service; }
	@GetMapping("/{imageId}")
	public ResponseEntity<byte[]> get(@PathVariable UUID productId, @PathVariable UUID imageId) {
		var image = service.get(productId, imageId);
		return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType()))
				.cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff").body(image.content());
	}
}
