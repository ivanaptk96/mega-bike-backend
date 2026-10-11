package com.megabike.inventory.application;

import com.megabike.catalog.application.ProductService;
import com.megabike.catalog.api.ProductRequest;
import com.megabike.catalog.domain.*;
import com.megabike.inventory.api.*;
import com.megabike.inventory.domain.*;
import com.megabike.sef.domain.*;
import com.megabike.shared.api.ApiException;
import com.megabike.supplier.domain.SupplierClassification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

@Service
public class GoodsReceiptService {
	private final SefInvoiceRepository invoices;
	private final SefInvoiceLineRepository invoiceLines;
	private final GoodsReceiptRepository receipts;
	private final GoodsReceiptLineRepository lines;
	private final StockLocationRepository locations;
	private final ProductRepository products;
	private final ProductService productService;
	private final InventoryItemRepository inventory;
	private final StockMovementRepository movements;
	private final JdbcTemplate jdbc;
	public GoodsReceiptService(SefInvoiceRepository invoices, SefInvoiceLineRepository invoiceLines,
			GoodsReceiptRepository receipts, GoodsReceiptLineRepository lines, StockLocationRepository locations,
			ProductRepository products, ProductService productService, InventoryItemRepository inventory, StockMovementRepository movements, JdbcTemplate jdbc) {
		this.invoices = invoices; this.invoiceLines = invoiceLines; this.receipts = receipts; this.lines = lines;
		this.locations = locations; this.products = products; this.productService = productService; this.inventory = inventory; this.movements = movements; this.jdbc = jdbc;
	}
	@Transactional(readOnly = true)
	public Page<GoodsReceiptResponse.Summary> list(int page, int size) {
		if (page < 0 || size < 1 || size > 100) throw error("INVALID_PAGE_REQUEST", "Neispravna stranica.");
		return invoices.findAll((root, query, cb) -> cb.and(
				cb.equal(root.get("direction"), SefInvoiceDirection.PURCHASE),
				cb.equal(root.get("supplier").get("classification"), SupplierClassification.MERCHANDISE)),
				PageRequest.of(page, size, Sort.by("importedAt").descending())).map(invoice -> {
			var receipt = receipts.findByInvoiceId(invoice.getId());
			return new GoodsReceiptResponse.Summary(invoice.getId(), invoice.getInvoiceNumber(), supplierName(invoice),
					receipt.map(GoodsReceipt::getStatus).orElse(ReceiptStatus.PENDING));
		});
	}
	@Transactional(readOnly = true)
	public List<GoodsReceiptResponse.Location> locations() {
		return locations.findAll().stream().filter(StockLocation::isActive)
				.map(l -> new GoodsReceiptResponse.Location(l.getId(), l.getCode(), l.getName())).toList();
	}
	@Transactional(readOnly = true)
	public GoodsReceiptResponse get(UUID invoiceId) {
		var invoice = invoices.findById(invoiceId).orElseThrow(() -> error("INVOICE_NOT_FOUND", "Faktura nije pronađena."));
		validateInvoice(invoice);
		return response(invoice, receipts.findByInvoiceId(invoiceId).orElse(null));
	}
	@Transactional
	public GoodsReceiptResponse start(UUID invoiceId, GoodsReceiptRequest request) {
		var invoice = lockedInvoice(invoiceId);
		var existing = receipts.findByInvoiceId(invoiceId);
		if (existing.isPresent()) return response(invoice, existing.get());
		var location = locations.findById(request.stockLocationId()).filter(StockLocation::isActive)
				.orElseThrow(() -> error("STOCK_LOCATION_NOT_FOUND", "Izaberite aktivnu lokaciju."));
		var source = invoiceLines.findByInvoiceIdOrderByLineNumberAsc(invoiceId);
		if (source.isEmpty()) throw error("RECEIPT_NO_LINES", "Sinhronizujte XML fakture pre prijema robe.");
		for (var line : source) {
			if (line.getQuantity() == null || line.getQuantity().signum() <= 0 || line.getQuantity().stripTrailingZeros().scale() > 0)
				throw error("RECEIPT_QUANTITY_INVALID", "Stavka " + line.getLineNumber() + " (" + line.getItemName()
						+ "): količina za prijem mora biti pozitivan ceo broj jedinica sa fakture.");
			// XKI is one complete kit, counted as one sellable catalog item. Never expand its contents.
			String unitCode = Objects.toString(line.getUnitCode(), "").trim().toUpperCase(Locale.ROOT);
			if (!unitCode.isEmpty() && !Set.of("H87", "C62", "EA", "PCE", "XKI").contains(unitCode))
				throw error("RECEIPT_UNIT_INVALID", "Stavka " + line.getLineNumber() + " (" + line.getItemName()
						+ "): jedinica " + unitCode + " zahteva definisano pretvaranje u lager. Prijem podržava komade i cele komplete.");
		}
		var receipt = receipts.save(new GoodsReceipt(invoice, location, Instant.now()));
		lines.saveAll(source.stream().map(l -> new GoodsReceiptLine(receipt, l, nextProductCode())).toList());
		return response(invoice, receipt);
	}
	@Transactional
	public GoodsReceiptResponse approve(UUID invoiceId, GoodsReceiptRequest.Approval request, String actor) {
		var invoice = lockedInvoice(invoiceId);
		var receipt = receipts.findByInvoiceId(invoiceId).orElseThrow(() -> error("RECEIPT_NOT_STARTED", "Prvo započnite prijem."));
		if (receipt.getVersion() != request.version()) throw conflict("Prijem je izmenjen. Osvežite prikaz.");
		var all = lines.findByReceiptIdOrderByLineNumberAsc(receipt.getId());
		var byId = new HashMap<UUID, GoodsReceiptLine>(); all.forEach(l -> byId.put(l.getId(), l));
		var seen = new HashSet<UUID>();
		var prices = new HashMap<UUID, BigDecimal>();
		// Consistent product lock order avoids deadlocks when two invoices contain the same products.
		var lockedProducts = new HashMap<UUID, Product>();
		request.lines().stream().map(GoodsReceiptRequest.Line::productId).filter(Objects::nonNull).distinct().sorted().forEach(id ->
				lockedProducts.put(id, products.findLockedById(id).orElseThrow(() -> error("PRODUCT_NOT_FOUND", "Proizvod nije pronađen."))));
		for (var approval : request.lines()) {
			var line = byId.get(approval.lineId());
			if (line == null || !seen.add(approval.lineId())) throw error("RECEIPT_LINE_INVALID", "Neispravna ili ponovljena stavka prijema.");
			var remaining = line.getExpectedQuantity().subtract(line.getReceivedQuantity());
			if (approval.quantity().signum() <= 0 || approval.quantity().compareTo(remaining) > 0)
				throw conflict("Količina prelazi preostalu količinu za prijem.");
			if ((approval.productId() == null) == (approval.newProduct() == null))
				throw error("RECEIPT_PRODUCT_REQUIRED", "Izaberite postojeći ili novi proizvod.");
			Product product;
			if (approval.productId() != null) {
				product = lockedProducts.get(approval.productId());
			} else {
				var n = approval.newProduct();
				if (line.getPurchasePrice() == null || line.getPurchasePrice().signum() < 0 || line.getVatRate() == null)
					throw error("RECEIPT_INVOICE_PRICE_MISSING", "Na fakturi nedostaje nabavna cena ili PDV. Proverite sinhronizaciju fakture.");
				var created = productService.create(new ProductRequest(line.getReservedProductCode(), null, null, n.name(), null,
						null, n.categoryId(), ProductUnit.PIECE, line.getPurchasePrice().setScale(2, RoundingMode.HALF_UP),
						approval.retailPrice(), line.getVatRate(), true, true));
				product = products.findById(created.id()).orElseThrow();
			}
			if (!product.isActive()) throw error("PRODUCT_INACTIVE", "Proizvod mora biti aktivan.");
			if (line.getProduct() != null && !line.getProduct().getId().equals(product.getId()))
				throw conflict("Delimično primljena stavka mora ostati vezana za isti proizvod.");
			var previousPrice = prices.putIfAbsent(product.getId(), approval.retailPrice());
			if (previousPrice != null && previousPrice.compareTo(approval.retailPrice()) != 0)
				throw error("RECEIPT_PRICE_CONFLICT", "Isti proizvod mora imati istu prodajnu cenu u potvrdi.");
			var now = Instant.now();
			product.setReceiptRetailPrice(approval.retailPrice(), now);
			var item = inventory.findWithProductAndStockLocationByProductIdAndStockLocationId(product.getId(), receipt.getStockLocation().getId())
					.orElseGet(() -> new InventoryItem(UUID.randomUUID(), product, receipt.getStockLocation(), BigDecimal.ZERO, now));
			item.adjust(approval.quantity(), now); inventory.save(item);
			var movement = new StockMovement(UUID.randomUUID(), product, receipt.getStockLocation(), MovementType.GOODS_RECEIPT,
					approval.quantity(), item.getQuantityOnHand(), "Ulaz robe po eFakturi", "Prijem " + receipt.getId() + " / stavka " + line.getLineNumber(), actor, now);
			movement.recordReceipt(line.getId(), approval.retailPrice());
			movements.save(movement);
			line.receive(product, approval.quantity(), approval.retailPrice(), actor, now);
		}
		receipt.updateStatus(all.stream().allMatch(l -> l.getStatus() == ReceiptStatus.RECEIVED) ? ReceiptStatus.RECEIVED : ReceiptStatus.PARTIAL, Instant.now());
		receipts.flush();
		return response(invoice, receipt);
	}
	private SefInvoice lockedInvoice(UUID id) {
		var invoice = invoices.findLockedById(id).orElseThrow(() -> error("INVOICE_NOT_FOUND", "Faktura nije pronađena."));
		validateInvoice(invoice); return invoice;
	}
	private void validateInvoice(SefInvoice invoice) {
		String documentType = Objects.toString(invoice.getDocumentType(), "").toLowerCase(Locale.ROOT);
		if (Set.of("381", "383", "386").contains(documentType) || documentType.contains("credit")
				|| documentType.contains("debit") || documentType.contains("prepayment") || documentType.contains("advance"))
			throw error("RECEIPT_DOCUMENT_INVALID", "Prijem robe nije dostupan za avansne fakture i knjižna odobrenja/zaduženja.");
		if (Set.of("cancelled", "canceled", "rejected", "storno").contains(invoice.getStatus().toLowerCase(Locale.ROOT)))
			throw error("RECEIPT_STATUS_INVALID", "Otkazana ili odbijena faktura ne može da se primi na lager.");
		if (invoice.getDirection() != SefInvoiceDirection.PURCHASE || invoice.getSupplier() == null
				|| invoice.getSupplier().getClassification() != SupplierClassification.MERCHANDISE)
			throw error("RECEIPT_INVOICE_INVALID", "Ulaz robe je dostupan samo za fakture robnih dobavljača.");
		if (invoice.getCurrency() == null || !invoice.getCurrency().equals("RSD"))
			throw error("RECEIPT_CURRENCY_INVALID", "Prijem trenutno podržava fakture u RSD.");
	}
	private GoodsReceiptResponse response(SefInvoice invoice, GoodsReceipt receipt) {
		var rows = receipt == null ? List.<GoodsReceiptResponse.Line>of() : lines.findByReceiptIdOrderByLineNumberAsc(receipt.getId()).stream()
				.map(l -> new GoodsReceiptResponse.Line(l.getId(), l.getLineNumber(), l.getItemName(), l.getSellerItemId(), l.getUnitCode(),
						l.getExpectedQuantity(), l.getReceivedQuantity(), l.getPurchasePrice(), l.getVatRate(), l.getRetailPrice(),
						l.getProduct() == null ? null : l.getProduct().getId(), l.getProduct() == null ? null : l.getProduct().getName(), l.getStatus(), l.getApprovedBy(), l.getApprovedAt(),
						l.getProduct() == null ? l.getReservedProductCode() : l.getProduct().getProductCode(), imageUrl(invoice, l))).toList();
		return new GoodsReceiptResponse(invoice.getId(), invoice.getConnection().getId(), invoice.getInvoiceNumber(), supplierName(invoice),
				receipt == null ? ReceiptStatus.PENDING : receipt.getStatus(), receipt == null ? null : receipt.getStockLocation().getId(),
				receipt == null ? null : receipt.getStockLocation().getName(), receipt == null ? 0 : receipt.getVersion(), rows);
	}
	private String nextProductCode() {
		String code;
		do {
			Long number = jdbc.queryForObject("select nextval('product_code_seq')", Long.class);
			code = "MB-" + String.format(Locale.ROOT, "%06d", number);
		} while (products.existsByProductCode(code));
		return code;
	}
	private String imageUrl(SefInvoice invoice, GoodsReceiptLine line) {
		if (invoice.getSupplier() == null) return null;
		var urls = jdbc.queryForList("""
				select image_url from supplier_product
				where supplier_id = ? and active = true and image_url is not null and image_url != ''
				and (supplier_code = ? or product_id = ?)
				order by case when supplier_code = ? then 0 else 1 end, id limit 1
				""", String.class, invoice.getSupplier().getId(), line.getSellerItemId(),
				line.getProduct() == null ? null : line.getProduct().getId(), line.getSellerItemId());
		return urls.isEmpty() ? null : urls.getFirst();
	}
	private String supplierName(SefInvoice invoice) {
		return invoice.getSupplier() == null ? invoice.getCounterpartyName() :
				invoice.getSupplier().getDisplayName() == null ? invoice.getSupplier().getName() : invoice.getSupplier().getDisplayName();
	}
	private ApiException error(String code, String message) { return new ApiException(HttpStatus.BAD_REQUEST, code, message); }
	private ApiException conflict(String message) { return new ApiException(HttpStatus.CONFLICT, "RECEIPT_CONFLICT", message); }
}
