package com.megabike.catalog.api;

import com.megabike.catalog.application.CategoryService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/internal/categories")
public class CategoryController {

	private final CategoryService categoryService;

	public CategoryController(CategoryService categoryService) {
		this.categoryService = categoryService;
	}

	@PostMapping
	@PreAuthorize("hasAuthority('PRODUCT_WRITE')")
	public CategoryResponse create(@Valid @RequestBody CategoryRequest request) {
		return categoryService.create(request);
	}

	@GetMapping
	@PreAuthorize("hasAuthority('PRODUCT_READ')")
	public List<CategoryResponse> list() {
		return categoryService.list();
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('PRODUCT_READ')")
	public CategoryResponse get(@PathVariable UUID id) {
		return categoryService.get(id);
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('PRODUCT_WRITE')")
	public CategoryResponse update(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
		return categoryService.update(id, request);
	}
}
