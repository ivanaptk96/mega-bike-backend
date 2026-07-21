package com.megabike.catalog.api;

import com.megabike.catalog.application.ProductService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/internal/products")
public class ProductController {

	private final ProductService productService;

	public ProductController(ProductService productService) {
		this.productService = productService;
	}

	@PostMapping
	@PreAuthorize("hasAuthority('PRODUCT_WRITE')")
	public ProductResponse create(@Valid @RequestBody ProductRequest request) {
		return productService.create(request);
	}

	@GetMapping
	@PreAuthorize("hasAuthority('PRODUCT_READ')")
	public List<ProductResponse> list(
			@RequestParam(required = false) String query,
			@RequestParam(required = false) UUID categoryId,
			@RequestParam(required = false) Boolean active
	) {
		return productService.list(query, categoryId, active);
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('PRODUCT_READ')")
	public ProductResponse get(@PathVariable UUID id) {
		return productService.get(id);
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('PRODUCT_WRITE')")
	public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
		return productService.update(id, request);
	}
}
