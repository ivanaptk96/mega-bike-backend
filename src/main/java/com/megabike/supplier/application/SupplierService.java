package com.megabike.supplier.application;

import com.megabike.catalog.domain.Product;
import com.megabike.catalog.domain.ProductRepository;
import com.megabike.shared.api.ApiException;
import com.megabike.supplier.api.SupplierImportIssueResponse;
import com.megabike.supplier.api.SupplierImportRunResponse;
import com.megabike.supplier.api.SupplierClassificationRequest;
import com.megabike.supplier.api.SupplierProductPageResponse;
import com.megabike.supplier.api.SupplierProductRequest;
import com.megabike.supplier.api.SupplierProductResponse;
import com.megabike.supplier.api.SupplierRequest;
import com.megabike.supplier.api.SupplierResponse;
import com.megabike.supplier.domain.Supplier;
import com.megabike.supplier.domain.SupplierClassification;
import com.megabike.supplier.domain.SupplierImportIssue;
import com.megabike.supplier.domain.SupplierImportIssueRepository;
import com.megabike.supplier.domain.SupplierImportRun;
import com.megabike.supplier.domain.SupplierImportRunRepository;
import com.megabike.supplier.domain.SupplierImportSourceType;
import com.megabike.supplier.domain.SupplierImportStatus;
import com.megabike.supplier.domain.SupplierPriceSnapshot;
import com.megabike.supplier.domain.SupplierPriceSnapshotRepository;
import com.megabike.supplier.domain.SupplierProduct;
import com.megabike.supplier.domain.SupplierProductRepository;
import com.megabike.supplier.domain.SupplierRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class SupplierService {

	private static final String PLANET_BIKE_CODE = "PLANET_BIKE";

	private final SupplierRepository supplierRepository;
	private final SupplierProductRepository supplierProductRepository;
	private final SupplierImportRunRepository importRunRepository;
	private final SupplierImportIssueRepository importIssueRepository;
	private final SupplierPriceSnapshotRepository priceSnapshotRepository;
	private final ProductRepository productRepository;
	private final Clock clock;

	public SupplierService(
			SupplierRepository supplierRepository,
			SupplierProductRepository supplierProductRepository,
			SupplierImportRunRepository importRunRepository,
			SupplierImportIssueRepository importIssueRepository,
			SupplierPriceSnapshotRepository priceSnapshotRepository,
			ProductRepository productRepository
	) {
		this.supplierRepository = supplierRepository;
		this.supplierProductRepository = supplierProductRepository;
		this.importRunRepository = importRunRepository;
		this.importIssueRepository = importIssueRepository;
		this.priceSnapshotRepository = priceSnapshotRepository;
		this.productRepository = productRepository;
		this.clock = Clock.systemUTC();
	}

	@Transactional
	public SupplierResponse create(SupplierRequest request) {
		String code = normalizeCode(request.code());
		if (supplierRepository.existsByCode(code)) {
			throw new ApiException(HttpStatus.CONFLICT, "SUPPLIER_CODE_EXISTS", "Supplier code already exists.");
		}
		Supplier supplier = new Supplier(
				UUID.randomUUID(),
				code,
				normalizeRequired(request.name()),
				normalizeOptional(request.registrationNumber()),
				normalizeOptional(request.vatRegistrationNumber()),
				normalizeOptional(request.publicUrl()),
				normalizeOptional(request.b2bUrl()),
				normalizeCode(request.defaultCurrency()),
				request.active(),
				Instant.now(clock)
		);
		return toSupplierResponse(supplierRepository.save(supplier));
	}

	@Transactional(readOnly = true)
	public List<SupplierResponse> listSuppliers(SupplierClassification classification) {
		List<Supplier> suppliers = classification == null
				? supplierRepository.findAllByOrderByNameAsc()
				: supplierRepository.findByClassificationOrderByNameAsc(classification);
		return suppliers.stream()
				.map(this::toSupplierResponse)
				.toList();
	}

	@Transactional(readOnly = true)
	public SupplierResponse getSupplier(UUID id) {
		return toSupplierResponse(findSupplier(id));
	}

	@Transactional
	public SupplierResponse updateSupplier(UUID id, SupplierRequest request) {
		Supplier supplier = findSupplier(id);
		String code = normalizeCode(request.code());
		if (supplierRepository.existsByCodeAndIdNot(code, id)) {
			throw new ApiException(HttpStatus.CONFLICT, "SUPPLIER_CODE_EXISTS", "Supplier code already exists.");
		}
		supplier.update(
				code,
				normalizeRequired(request.name()),
				normalizeOptional(request.registrationNumber()),
				normalizeOptional(request.vatRegistrationNumber()),
				normalizeOptional(request.publicUrl()),
				normalizeOptional(request.b2bUrl()),
				normalizeCode(request.defaultCurrency()),
				request.active(),
				Instant.now(clock)
		);
		return toSupplierResponse(supplier);
	}

	@Transactional
	public SupplierResponse updateSupplierClassification(UUID id, SupplierClassificationRequest request) {
		Supplier supplier = findSupplier(id);
		supplier.updateClassification(request.classification(), Instant.now(clock));
		return toSupplierResponse(supplier);
	}

	@Transactional
	public SupplierProductResponse createSupplierProduct(SupplierProductRequest request) {
		Supplier supplier = findSupplier(request.supplierId());
		Product product = resolveProduct(request.productId());
		String supplierCode = normalizeRequired(request.supplierCode());
		if (supplierProductRepository.existsBySupplierIdAndSupplierCode(supplier.getId(), supplierCode)) {
			throw new ApiException(HttpStatus.CONFLICT, "SUPPLIER_PRODUCT_CODE_EXISTS", "Supplier product code already exists for this supplier.");
		}
		SupplierProduct supplierProduct = new SupplierProduct(
				UUID.randomUUID(),
				supplier,
				product,
				supplierCode,
				normalizeOptional(request.supplierBarcode()),
				normalizeRequired(request.supplierProductName()),
				normalizeOptional(request.brandName()),
				normalizeOptional(request.categoryText()),
				normalizeOptional(request.description()),
				normalizeOptional(request.imageUrl()),
				request.active(),
				Instant.now(clock)
		);
		return toSupplierProductResponse(supplierProductRepository.save(supplierProduct));
	}

	@Transactional(readOnly = true)
	public SupplierProductPageResponse listSupplierProducts(UUID supplierId, String query, Boolean active, int page, int size) {
		Page<SupplierProductResponse> products = supplierProductRepository.findAll(
				supplierProductSpecification(supplierId, normalizeOptional(query), active),
				PageRequest.of(validatePage(page), validateSize(size), Sort.by("supplierProductName").ascending())
		).map(this::toSupplierProductResponse);

		return new SupplierProductPageResponse(
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
	public SupplierProductResponse getSupplierProduct(UUID id) {
		return toSupplierProductResponse(findSupplierProduct(id));
	}

	@Transactional
	public SupplierProductResponse updateSupplierProduct(UUID id, SupplierProductRequest request) {
		SupplierProduct supplierProduct = findSupplierProduct(id);
		Supplier supplier = findSupplier(request.supplierId());
		if (!supplier.getId().equals(supplierProduct.getSupplier().getId())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "SUPPLIER_PRODUCT_SUPPLIER_CHANGE_UNSUPPORTED", "Supplier cannot be changed for an existing supplier product.");
		}
		Product product = resolveProduct(request.productId());
		String supplierCode = normalizeRequired(request.supplierCode());
		if (supplierProductRepository.existsBySupplierIdAndSupplierCodeAndIdNot(supplier.getId(), supplierCode, id)) {
			throw new ApiException(HttpStatus.CONFLICT, "SUPPLIER_PRODUCT_CODE_EXISTS", "Supplier product code already exists for this supplier.");
		}
		supplierProduct.update(
				product,
				supplierCode,
				normalizeOptional(request.supplierBarcode()),
				normalizeRequired(request.supplierProductName()),
				normalizeOptional(request.brandName()),
				normalizeOptional(request.categoryText()),
				normalizeOptional(request.description()),
				normalizeOptional(request.imageUrl()),
				request.active(),
				Instant.now(clock)
		);
		return toSupplierProductResponse(supplierProduct);
	}

	@Transactional
	public SupplierImportRunResponse importPlanetBikeCsv(UUID supplierId, MultipartFile file, String uploadedBy) {
		Supplier supplier = findSupplier(supplierId);
		if (!PLANET_BIKE_CODE.equals(supplier.getCode()) && !"100116685".equals(supplier.getVatRegistrationNumber())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "SUPPLIER_IMPORT_UNSUPPORTED", "Only Planet Bike CSV import is currently supported.");
		}
		if (file.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "SUPPLIER_IMPORT_EMPTY_FILE", "Import file is empty.");
		}

		Instant startedAt = Instant.now(clock);
		byte[] bytes = readBytes(file);
		String sha256Hash = sha256(bytes);
		List<CsvRow> rows = parseCsv(bytes);
		ImportCounters counters = new ImportCounters();
		List<PendingIssue> pendingIssues = new ArrayList<>();
		List<PendingSnapshot> pendingSnapshots = new ArrayList<>();

		if (rows.isEmpty()) {
			pendingIssues.add(new PendingIssue(1, "SUPPLIER_IMPORT_NO_ROWS", "CSV file has no data rows.", null));
		}

		for (CsvRow row : rows) {
			counters.totalRows++;
			importRow(supplier, row, counters, pendingIssues, pendingSnapshots);
		}

		Instant finishedAt = Instant.now(clock);
		SupplierImportStatus status = pendingIssues.isEmpty() ? SupplierImportStatus.COMPLETED : SupplierImportStatus.COMPLETED_WITH_ISSUES;
		SupplierImportRun run = importRunRepository.save(new SupplierImportRun(
				UUID.randomUUID(),
				supplier,
				SupplierImportSourceType.CSV_UPLOAD,
				status,
				file.getOriginalFilename() == null ? "planet-bike-lager-lista.csv" : file.getOriginalFilename(),
				file.getContentType(),
				sha256Hash,
				uploadedBy,
				startedAt,
				finishedAt,
				counters.totalRows,
				counters.createdSupplierProducts,
				counters.updatedSupplierProducts,
				counters.priceSnapshots,
				pendingIssues.size()
		));

		for (PendingSnapshot pendingSnapshot : pendingSnapshots) {
			priceSnapshotRepository.save(new SupplierPriceSnapshot(
					UUID.randomUUID(),
					pendingSnapshot.supplierProduct(),
					run,
					pendingSnapshot.purchasePrice(),
					supplier.getDefaultCurrency(),
					pendingSnapshot.vatIncluded(),
					pendingSnapshot.availableQuantity(),
					finishedAt,
					"PLANET_BIKE_LAGER_LISTA"
			));
		}
		for (PendingIssue pendingIssue : pendingIssues) {
			importIssueRepository.save(new SupplierImportIssue(
					UUID.randomUUID(),
					run,
					pendingIssue.rowNumber(),
					pendingIssue.code(),
					pendingIssue.message(),
					pendingIssue.rawRow(),
					finishedAt
			));
		}

		return toImportRunResponse(run);
	}

	@Transactional(readOnly = true)
	public List<SupplierImportRunResponse> listImportRuns(UUID supplierId) {
		return importRunRepository.findAllBySupplierIdOrderByStartedAtDesc(supplierId).stream()
				.map(this::toImportRunResponse)
				.toList();
	}

	@Transactional(readOnly = true)
	public SupplierImportRunResponse getImportRun(UUID importRunId) {
		return toImportRunResponse(findImportRun(importRunId));
	}

	@Transactional(readOnly = true)
	public List<SupplierImportIssueResponse> listImportIssues(UUID importRunId) {
		findImportRun(importRunId);
		return importIssueRepository.findAllByImportRunIdOrderByRowNumberAsc(importRunId).stream()
				.map(this::toIssueResponse)
				.toList();
	}

	private void importRow(Supplier supplier, CsvRow row, ImportCounters counters, List<PendingIssue> issues, List<PendingSnapshot> snapshots) {
		String supplierCode = firstPresent(row, "supplier_code", "suppliercode", "sifra", "šifra", "code", "sku", "artikal_sifra");
		String name = firstPresent(row, "name", "naziv", "product_name", "supplier_product_name", "artikal", "opis");
		if (supplierCode == null || name == null) {
			issues.add(new PendingIssue(row.rowNumber(), "SUPPLIER_IMPORT_REQUIRED_FIELDS_MISSING", "Row must contain supplier code and product name.", row.rawRow()));
			return;
		}

		String barcode = firstPresent(row, "barcode", "bar_code", "ean", "barkod");
		String brand = firstPresent(row, "brand", "brend", "proizvodjac", "proizvođač");
		String category = firstPresent(row, "category", "kategorija", "grupa");
		String description = firstPresent(row, "description", "opis", "detaljan_opis");
		String imageUrl = firstPresent(row, "image_url", "image", "slika", "slika_url");
		String contractedPrice = firstPresent(row, "vpcena_u_rsd_sa_ugovorenim_rabatom");
		BigDecimal price = parseDecimal(contractedPrice != null ? contractedPrice : firstPresent(row, "purchase_price", "price", "cena", "cijena", "nabavna_cena", "veleprodajna_cena"));
		BigDecimal availableQuantity = parseDecimal(firstPresent(row, "available_quantity", "stock", "stanje", "lager", "kolicina", "količina", "kol_na"));

		SupplierProduct supplierProduct = supplierProductRepository
				.findWithSupplierAndProductBySupplierIdAndSupplierCode(supplier.getId(), supplierCode)
				.orElse(null);

		if (supplierProduct == null) {
			Product matchedProduct = barcode == null ? null : productRepository.findByBarcode(barcode).orElse(null);
			supplierProduct = supplierProductRepository.save(new SupplierProduct(
					UUID.randomUUID(),
					supplier,
					matchedProduct,
					supplierCode,
					barcode,
					name,
					brand,
					category,
					description,
					imageUrl,
					true,
					Instant.now(clock)
			));
			counters.createdSupplierProducts++;
		} else {
			// Daily stock files omit catalog metadata; keep the previously captured data.
			barcode = barcode != null ? barcode : supplierProduct.getSupplierBarcode();
			brand = brand != null ? brand : supplierProduct.getBrandName();
			category = category != null ? category : supplierProduct.getCategoryText();
			description = description != null ? description : supplierProduct.getDescription();
			imageUrl = imageUrl != null ? imageUrl : supplierProduct.getImageUrl();
			supplierProduct.update(
					supplierProduct.getProduct(),
					supplierCode,
					barcode,
					name,
					brand,
					category,
					description,
					imageUrl,
					true,
					Instant.now(clock)
			);
			counters.updatedSupplierProducts++;
		}

		if (price != null) {
			snapshots.add(new PendingSnapshot(supplierProduct, price, contractedPrice == null, availableQuantity));
			counters.priceSnapshots++;
		} else {
			issues.add(new PendingIssue(row.rowNumber(), "SUPPLIER_IMPORT_PRICE_MISSING", "Row imported without purchase price snapshot.", row.rawRow()));
		}
	}

	private List<CsvRow> parseCsv(byte[] bytes) {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(new java.io.ByteArrayInputStream(bytes), StandardCharsets.UTF_8))) {
			String headerLine = reader.readLine();
			if (headerLine == null) {
				return List.of();
			}
			char delimiter = detectDelimiter(headerLine);
			List<String> headers = splitCsvLine(headerLine, delimiter).stream().map(this::normalizeHeader).toList();
			List<CsvRow> rows = new ArrayList<>();
			String line;
			int rowNumber = 1;
			while ((line = reader.readLine()) != null) {
				rowNumber++;
				if (line.isBlank()) {
					continue;
				}
				List<String> values = splitCsvLine(line, delimiter);
				Map<String, String> data = new HashMap<>();
				for (int index = 0; index < headers.size(); index++) {
					String value = index < values.size() ? normalizeOptional(values.get(index)) : null;
					data.put(headers.get(index), value);
				}
				rows.add(new CsvRow(rowNumber, data, line));
			}
			return rows;
		} catch (Exception exception) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "SUPPLIER_IMPORT_PARSE_FAILED", "CSV file could not be parsed.");
		}
	}

	private char detectDelimiter(String headerLine) {
		long semicolons = headerLine.chars().filter(character -> character == ';').count();
		long commas = headerLine.chars().filter(character -> character == ',').count();
		return semicolons > commas ? ';' : ',';
	}

	private List<String> splitCsvLine(String line, char delimiter) {
		List<String> values = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < line.length(); i++) {
			char character = line.charAt(i);
			if (character == '"') {
				if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
					current.append('"');
					i++;
				} else {
					quoted = !quoted;
				}
			} else if (character == delimiter && !quoted) {
				values.add(current.toString().trim());
				current.setLength(0);
			} else {
				current.append(character);
			}
		}
		values.add(current.toString().trim());
		return values;
	}

	private byte[] readBytes(MultipartFile file) {
		try {
			return file.getBytes();
		} catch (Exception exception) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "SUPPLIER_IMPORT_READ_FAILED", "Import file could not be read.");
		}
	}

	private String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (Exception exception) {
			throw new IllegalStateException("Unable to calculate SHA-256 hash", exception);
		}
	}

	private String firstPresent(CsvRow row, String... headers) {
		for (String header : headers) {
			String value = row.values().get(normalizeHeader(header));
			if (value != null) {
				return value;
			}
		}
		return null;
	}

	private BigDecimal parseDecimal(String value) {
		if (value == null) {
			return null;
		}
		String normalized = value.contains(",")
				? value.replace(".", "").replace(",", ".")
				: value;
		try {
			return new BigDecimal(normalized);
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	private Specification<SupplierProduct> supplierProductSpecification(UUID supplierId, String query, Boolean active) {
		return (root, criteriaQuery, criteriaBuilder) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (supplierId != null) {
				predicates.add(criteriaBuilder.equal(root.get("supplier").get("id"), supplierId));
			}
			if (active != null) {
				predicates.add(criteriaBuilder.equal(root.get("active"), active));
			}
			if (query != null) {
				String pattern = "%" + query.toLowerCase() + "%";
				predicates.add(criteriaBuilder.or(
						criteriaBuilder.like(criteriaBuilder.lower(root.get("supplierCode")), pattern),
						criteriaBuilder.like(criteriaBuilder.lower(root.get("supplierProductName")), pattern),
						criteriaBuilder.like(criteriaBuilder.lower(root.get("supplierBarcode")), pattern),
						criteriaBuilder.like(criteriaBuilder.lower(root.get("brandName")), pattern)
				));
			}
			return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
		};
	}

	private Supplier findSupplier(UUID id) {
		return supplierRepository.findById(id)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SUPPLIER_NOT_FOUND", "Supplier was not found."));
	}

	private SupplierProduct findSupplierProduct(UUID id) {
		return supplierProductRepository.findWithSupplierAndProductById(id)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SUPPLIER_PRODUCT_NOT_FOUND", "Supplier product was not found."));
	}

	private SupplierImportRun findImportRun(UUID id) {
		return importRunRepository.findWithSupplierById(id)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SUPPLIER_IMPORT_NOT_FOUND", "Supplier import was not found."));
	}

	private Product resolveProduct(UUID productId) {
		if (productId == null) {
			return null;
		}
		return productRepository.findById(productId)
				.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "SUPPLIER_PRODUCT_INTERNAL_PRODUCT_NOT_FOUND", "Linked internal product was not found."));
	}

	private SupplierResponse toSupplierResponse(Supplier supplier) {
		return new SupplierResponse(
				supplier.getId(),
				supplier.getCode(),
				supplier.getName(),
				supplier.getDisplayName(),
				supplier.getRegistrationNumber(),
				supplier.getVatRegistrationNumber(),
				supplier.getClassification(),
				supplier.getPublicUrl(),
				supplier.getB2bUrl(),
				supplier.getDefaultCurrency(),
				supplier.isActive(),
				supplier.getCreatedAt(),
				supplier.getUpdatedAt()
		);
	}

	private SupplierProductResponse toSupplierProductResponse(SupplierProduct supplierProduct) {
		Product product = supplierProduct.getProduct();
		return new SupplierProductResponse(
				supplierProduct.getId(),
				supplierProduct.getSupplier().getId(),
				supplierProduct.getSupplierCode(),
				supplierProduct.getSupplier().getDisplayNameOrName(),
				product == null ? null : product.getId(),
				product == null ? null : product.getProductCode(),
				product == null ? null : product.getName(),
				supplierProduct.getSupplierBarcode(),
				supplierProduct.getSupplierProductName(),
				supplierProduct.getBrandName(),
				supplierProduct.getCategoryText(),
				supplierProduct.getDescription(),
				supplierProduct.getImageUrl(),
				supplierProduct.isActive(),
				supplierProduct.getCreatedAt(),
				supplierProduct.getUpdatedAt()
		);
	}

	private SupplierImportRunResponse toImportRunResponse(SupplierImportRun run) {
		Supplier supplier = run.getSupplier();
		return new SupplierImportRunResponse(run.getId(), supplier.getId(), supplier.getCode(), supplier.getDisplayNameOrName(), run.getSourceType(), run.getStatus(), run.getOriginalFilename(), run.getContentType(), run.getSha256Hash(), run.getUploadedBy(), run.getStartedAt(), run.getFinishedAt(), run.getTotalRows(), run.getCreatedSupplierProducts(), run.getUpdatedSupplierProducts(), run.getPriceSnapshots(), run.getIssuesCount());
	}

	private SupplierImportIssueResponse toIssueResponse(SupplierImportIssue issue) {
		return new SupplierImportIssueResponse(issue.getId(), issue.getImportRun().getId(), issue.getRowNumber(), issue.getCode(), issue.getMessage(), issue.getRawRow(), issue.getCreatedAt());
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

	private String normalizeHeader(String value) {
		return value.trim().toLowerCase(Locale.ROOT).replace(" ", "_");
	}

	private String normalizeCode(String value) {
		return value.trim().toUpperCase(Locale.ROOT);
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

	private record CsvRow(int rowNumber, Map<String, String> values, String rawRow) {
	}

	private record PendingIssue(int rowNumber, String code, String message, String rawRow) {
	}

	private record PendingSnapshot(SupplierProduct supplierProduct, BigDecimal purchasePrice, boolean vatIncluded, BigDecimal availableQuantity) {
	}

	private static class ImportCounters {
		private int totalRows;
		private int createdSupplierProducts;
		private int updatedSupplierProducts;
		private int priceSnapshots;
	}
}
