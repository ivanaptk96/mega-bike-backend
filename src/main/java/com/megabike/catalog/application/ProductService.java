package com.megabike.catalog.application;

import com.megabike.catalog.api.ProductRequest;
import com.megabike.catalog.api.ProductResponse;
import com.megabike.catalog.domain.Category;
import com.megabike.catalog.domain.CategoryRepository;
import com.megabike.catalog.domain.Product;
import com.megabike.catalog.domain.ProductRepository;
import com.megabike.shared.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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

	public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository) {
		this.productRepository = productRepository;
		this.categoryRepository = categoryRepository;
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
	public List<ProductResponse> list(String query, UUID categoryId, Boolean active) {
		return productRepository.findAll(searchSpecification(normalizeOptional(query), categoryId, active), Sort.by("name").ascending()).stream()
				.map(this::toResponse)
				.toList();
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
		return categoryRepository.findById(categoryId)
				.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "PRODUCT_CATEGORY_NOT_FOUND", "Product category was not found."));
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
				product.getUpdatedAt()
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

	private Specification<Product> searchSpecification(String query, UUID categoryId, Boolean active) {
		return (root, criteriaQuery, criteriaBuilder) -> {
			List<Predicate> predicates = new ArrayList<>();

			if (active != null) {
				predicates.add(criteriaBuilder.equal(root.get("active"), active));
			}
			if (categoryId != null) {
				predicates.add(criteriaBuilder.equal(root.get("category").get("id"), categoryId));
			}
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
}
