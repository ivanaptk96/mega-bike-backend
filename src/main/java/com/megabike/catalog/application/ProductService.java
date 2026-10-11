package com.megabike.catalog.application;

import com.megabike.catalog.api.ProductPageResponse;
import com.megabike.catalog.api.ProductRequest;
import com.megabike.catalog.api.ProductResponse;
import com.megabike.catalog.domain.Category;
import com.megabike.catalog.domain.CategoryRepository;
import com.megabike.catalog.domain.Product;
import com.megabike.catalog.domain.ProductRepository;
import com.megabike.shared.api.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ProductService {

	private final ProductRepository productRepository;
	private final CategoryRepository categoryRepository;
	private final Clock clock;
	private final CategoryService categoryService;
	private final ProductImageService images;

	public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository, CategoryService categoryService, ProductImageService images) {
		this.productRepository = productRepository;
		this.categoryRepository = categoryRepository;
		this.categoryService = categoryService;
		this.images = images;
		this.clock = Clock.systemUTC();
	}

	@Transactional
	public ProductResponse create(ProductRequest request) {
		String productCode = normalizeRequired(request.productCode());
		String barcode = normalizeOptional(request.barcode());

		if (productRepository.existsByProductCode(productCode)) {
			throw new ApiException(HttpStatus.CONFLICT, "PRODUCT_CODE_EXISTS", "Product code already exists.");
		}
		if (barcode != null && productRepository.existsByBarcode(barcode)) {
			throw new ApiException(HttpStatus.CONFLICT, "PRODUCT_BARCODE_EXISTS", "Product barcode already exists.");
		}

		Product product = new Product(
				UUID.randomUUID(),
				productCode,
				normalizeOptional(request.externalId()),
				barcode,
				normalizeRequired(request.name()),
				normalizeOptional(request.description()),
				normalizeOptional(request.brandName()),
				resolveCategory(request.categoryId()),
				request.unit(),
				request.purchasePrice(),
				request.retailPrice(),
				request.vatRate(),
				request.retailPriceIncludesVat(),
				request.active(),
				Instant.now(clock)
		);

		return toResponse(productRepository.save(product));
	}

	@Transactional(readOnly = true)
	public ProductPageResponse list(String query, UUID categoryId, Boolean active, int page, int size, String sort, String direction) {
		Page<ProductResponse> products = productRepository.findAll(
						searchSpecification(normalizeOptional(query), categoryService.visibleCategoryIds(categoryId), active),
						PageRequest.of(validatePage(page), validateSize(size), resolveSort(sort, direction))
				).map(this::toResponse);

		return new ProductPageResponse(
				products.getContent(),
				products.getNumber(),
				products.getSize(),
				products.getTotalElements(),
				products.getTotalPages(),
				products.isFirst(),
				products.isLast()
		);
	}

	@Transactional(readOnly = true)
	public ProductResponse get(UUID id) {
		return toResponse(findById(id));
	}

	@Transactional
	public ProductResponse update(UUID id, ProductRequest request) {
		Product product = findById(id);
		String productCode = normalizeRequired(request.productCode());
		String barcode = normalizeOptional(request.barcode());

		if (productRepository.existsByProductCodeAndIdNot(productCode, id)) {
			throw new ApiException(HttpStatus.CONFLICT, "PRODUCT_CODE_EXISTS", "Product code already exists.");
		}
		if (barcode != null && productRepository.existsByBarcodeAndIdNot(barcode, id)) {
			throw new ApiException(HttpStatus.CONFLICT, "PRODUCT_BARCODE_EXISTS", "Product barcode already exists.");
		}

		product.update(
				productCode,
				normalizeOptional(request.externalId()),
				barcode,
				normalizeRequired(request.name()),
				normalizeOptional(request.description()),
				normalizeOptional(request.brandName()),
				resolveCategory(request.categoryId()),
				request.unit(),
				request.purchasePrice(),
				request.retailPrice(),
				request.vatRate(),
				request.retailPriceIncludesVat(),
				request.active(),
				Instant.now(clock)
		);

		return toResponse(product);
	}

	private Product findById(UUID id) {
		return productRepository.findWithCategoryById(id)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product was not found."));
	}

	private Category resolveCategory(UUID categoryId) {
		Category category = categoryRepository.findById(categoryId)
				.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "PRODUCT_CATEGORY_NOT_FOUND", "Product category was not found."));
		if (!category.isActive() || !categoryService.isWebVisible(category) || categoryRepository.existsByParentId(categoryId))
			throw new ApiException(HttpStatus.BAD_REQUEST, "PRODUCT_CATEGORY_INVALID", "Izaberite aktivnu krajnju kategoriju vidljivu na webu.");
		return category;
	}

	private ProductResponse toResponse(Product product) {
		Category category = product.getCategory();
		return new ProductResponse(
				product.getId(),
				product.getProductCode(),
				product.getExternalId(),
				product.getBarcode(),
				product.getName(),
				product.getDescription(),
				product.getBrandName(),
				category.getId(),
				category.getName(),
				product.getUnit(),
				product.getPurchasePrice(),
				product.getRetailPrice(),
				product.getVatRate(),
				product.isRetailPriceIncludesVat(),
				product.isActive(),
				product.getCreatedAt(),
				product.getUpdatedAt(),
				images.ids(product.getId())
		);
	}

	private String normalizeRequired(String value) {
		return value.trim();
	}

	private String normalizeOptional(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	private Specification<Product> searchSpecification(String query, List<UUID> categoryIds, Boolean active) {
		return (root, criteriaQuery, criteriaBuilder) -> {
			List<Predicate> predicates = new ArrayList<>();

			if (active != null) {
				predicates.add(criteriaBuilder.equal(root.get("active"), active));
			}
			predicates.add(root.get("category").get("id").in(categoryIds));
			if (query != null) {
				String pattern = "%" + query.toLowerCase() + "%";
				predicates.add(criteriaBuilder.or(
						criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), pattern),
						criteriaBuilder.like(criteriaBuilder.lower(root.get("productCode")), pattern),
						criteriaBuilder.like(criteriaBuilder.lower(root.get("barcode")), pattern),
						criteriaBuilder.like(criteriaBuilder.lower(root.get("brandName")), pattern),
						criteriaBuilder.like(criteriaBuilder.lower(root.get("externalId")), pattern)
				));
			}

			return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
		};
	}

	private int validatePage(int page) {
		if (page < 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE_REQUEST", "Page must be greater than or equal to 0.");
		}
		return page;
	}

	private int validateSize(int size) {
		if (size < 1 || size > 100) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE_REQUEST", "Size must be between 1 and 100.");
		}
		return size;
	}

	private Sort resolveSort(String sort, String direction) {
		String property = switch (sort) {
			case "name" -> "name";
			case "productCode" -> "productCode";
			case "brandName" -> "brandName";
			case "retailPrice" -> "retailPrice";
			case "createdAt" -> "createdAt";
			case "active" -> "active";
			default -> throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE_REQUEST", "Unsupported product sort field.");
		};

		Sort.Direction sortDirection = switch (direction.toLowerCase()) {
			case "asc" -> Sort.Direction.ASC;
			case "desc" -> Sort.Direction.DESC;
			default -> throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE_REQUEST", "Sort direction must be asc or desc.");
		};

		return Sort.by(sortDirection, property);
	}
}
