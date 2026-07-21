package com.megabike.catalog.application;

import com.megabike.catalog.api.CategoryRequest;
import com.megabike.catalog.api.CategoryResponse;
import com.megabike.catalog.domain.Category;
import com.megabike.catalog.domain.CategoryRepository;
import com.megabike.shared.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CategoryService {

	private final CategoryRepository categoryRepository;
	private final SlugGenerator slugGenerator;
	private final Clock clock;

	public CategoryService(CategoryRepository categoryRepository, SlugGenerator slugGenerator) {
		this.categoryRepository = categoryRepository;
		this.slugGenerator = slugGenerator;
		this.clock = Clock.systemUTC();
	}

	@Transactional
	public CategoryResponse create(CategoryRequest request) {
		String slug = resolveSlug(request);
		if (categoryRepository.existsBySlug(slug)) {
			throw new ApiException(HttpStatus.CONFLICT, "CATEGORY_SLUG_EXISTS", "Category slug already exists.");
		}

		Category parent = resolveParent(request.parentId());
		Category category = new Category(
				UUID.randomUUID(),
				request.name().trim(),
				slug,
				parent,
				request.active(),
				Instant.now(clock)
		);

		return toResponse(categoryRepository.save(category));
	}

	@Transactional(readOnly = true)
	public List<CategoryResponse> list() {
		return categoryRepository.findAllByOrderByNameAsc().stream()
				.map(this::toResponse)
				.toList();
	}

	@Transactional(readOnly = true)
	public CategoryResponse get(UUID id) {
		return toResponse(findById(id));
	}

	@Transactional
	public CategoryResponse update(UUID id, CategoryRequest request) {
		Category category = findById(id);
		String slug = resolveSlug(request);

		if (categoryRepository.existsBySlugAndIdNot(slug, id)) {
			throw new ApiException(HttpStatus.CONFLICT, "CATEGORY_SLUG_EXISTS", "Category slug already exists.");
		}
		if (request.parentId() != null && request.parentId().equals(id)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "CATEGORY_PARENT_INVALID", "Category cannot be its own parent.");
		}

		category.update(
				request.name().trim(),
				slug,
				resolveParent(request.parentId()),
				request.active(),
				Instant.now(clock)
		);

		return toResponse(category);
	}

	private Category findById(UUID id) {
		return categoryRepository.findWithParentById(id)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "Category was not found."));
	}

	private Category resolveParent(UUID parentId) {
		if (parentId == null) {
			return null;
		}
		return categoryRepository.findById(parentId)
				.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "CATEGORY_PARENT_NOT_FOUND", "Parent category was not found."));
	}

	private String resolveSlug(CategoryRequest request) {
		if (request.slug() == null || request.slug().isBlank()) {
			return slugGenerator.slugify(request.name());
		}
		return slugGenerator.slugify(request.slug());
	}

	private CategoryResponse toResponse(Category category) {
		Category parent = category.getParent();
		return new CategoryResponse(
				category.getId(),
				category.getName(),
				category.getSlug(),
				parent == null ? null : parent.getId(),
				parent == null ? null : parent.getName(),
				category.isActive(),
				category.getCreatedAt(),
				category.getUpdatedAt()
		);
	}
}
