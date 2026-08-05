package com.megabike.supplier.api;

import com.megabike.supplier.application.SupplierService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/internal")
public class SupplierController {

	private final SupplierService supplierService;

	public SupplierController(SupplierService supplierService) {
		this.supplierService = supplierService;
	}

	@PostMapping("/suppliers")
	@PreAuthorize("hasAuthority('SUPPLIER_MANAGE')")
	public SupplierResponse createSupplier(@Valid @RequestBody SupplierRequest request) {
		return supplierService.create(request);
	}

	@GetMapping("/suppliers")
	@PreAuthorize("hasAuthority('SUPPLIER_MANAGE')")
	public List<SupplierResponse> listSuppliers() {
		return supplierService.listSuppliers();
	}

	@GetMapping("/suppliers/{id}")
	@PreAuthorize("hasAuthority('SUPPLIER_MANAGE')")
	public SupplierResponse getSupplier(@PathVariable UUID id) {
		return supplierService.getSupplier(id);
	}

	@PutMapping("/suppliers/{id}")
	@PreAuthorize("hasAuthority('SUPPLIER_MANAGE')")
	public SupplierResponse updateSupplier(@PathVariable UUID id, @Valid @RequestBody SupplierRequest request) {
		return supplierService.updateSupplier(id, request);
	}

	@PostMapping("/supplier-products")
	@PreAuthorize("hasAuthority('SUPPLIER_MANAGE')")
	public SupplierProductResponse createSupplierProduct(@Valid @RequestBody SupplierProductRequest request) {
		return supplierService.createSupplierProduct(request);
	}

	@GetMapping("/supplier-products")
	@PreAuthorize("hasAuthority('SUPPLIER_MANAGE')")
	public SupplierProductPageResponse listSupplierProducts(
			@RequestParam(required = false) UUID supplierId,
			@RequestParam(required = false) String query,
			@RequestParam(required = false) Boolean active,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size
	) {
		return supplierService.listSupplierProducts(supplierId, query, active, page, size);
	}

	@GetMapping("/supplier-products/{id}")
	@PreAuthorize("hasAuthority('SUPPLIER_MANAGE')")
	public SupplierProductResponse getSupplierProduct(@PathVariable UUID id) {
		return supplierService.getSupplierProduct(id);
	}

	@PutMapping("/supplier-products/{id}")
	@PreAuthorize("hasAuthority('SUPPLIER_MANAGE')")
	public SupplierProductResponse updateSupplierProduct(@PathVariable UUID id, @Valid @RequestBody SupplierProductRequest request) {
		return supplierService.updateSupplierProduct(id, request);
	}

	@PostMapping(path = "/suppliers/{supplierId}/imports", consumes = "multipart/form-data")
	@PreAuthorize("hasAuthority('SUPPLIER_MANAGE')")
	public SupplierImportRunResponse importSupplierCsv(
			@PathVariable UUID supplierId,
			@RequestPart("file") MultipartFile file,
			Authentication authentication
	) {
		return supplierService.importPlanetBikeCsv(supplierId, file, authentication.getName());
	}

	@GetMapping("/suppliers/{supplierId}/imports")
	@PreAuthorize("hasAuthority('SUPPLIER_MANAGE')")
	public List<SupplierImportRunResponse> listSupplierImports(@PathVariable UUID supplierId) {
		return supplierService.listImportRuns(supplierId);
	}

	@GetMapping("/supplier-imports/{importRunId}")
	@PreAuthorize("hasAuthority('SUPPLIER_MANAGE')")
	public SupplierImportRunResponse getSupplierImport(@PathVariable UUID importRunId) {
		return supplierService.getImportRun(importRunId);
	}

	@GetMapping("/supplier-imports/{importRunId}/issues")
	@PreAuthorize("hasAuthority('SUPPLIER_MANAGE')")
	public List<SupplierImportIssueResponse> listSupplierImportIssues(@PathVariable UUID importRunId) {
		return supplierService.listImportIssues(importRunId);
	}
}
