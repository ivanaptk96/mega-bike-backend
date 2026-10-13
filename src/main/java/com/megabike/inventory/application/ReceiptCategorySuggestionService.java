package com.megabike.inventory.application;

import com.megabike.catalog.domain.*;
import com.megabike.inventory.api.GoodsReceiptResponse.CategorySuggestion;
import com.megabike.inventory.infrastructure.ReceiptCategoryMappingRepository;
import com.megabike.supplier.domain.SupplierProduct;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class ReceiptCategorySuggestionService {
	private final CategoryRepository categories;
	private final ReceiptCategoryMappingRepository mappings;
	private final ReceiptCategoryRules rules;

	public ReceiptCategorySuggestionService(CategoryRepository categories, ReceiptCategoryMappingRepository mappings, ReceiptCategoryRules rules) {
		this.categories = categories; this.mappings = mappings; this.rules = rules;
	}

	public Catalog catalog() { return new Catalog(categories.findAllByOrderByNameAsc()); }

	public CategorySuggestion suggest(Catalog catalog, UUID supplierId, String supplierCode, String itemName, SupplierProduct supplierProduct, Product product) {
		if (product != null)
			return catalog.suggestion(product.getCategory().getId(), "EXISTING_PRODUCT", "Kategorija povezanog artikla; prijem je ne menja.");
		UUID saved = mappings.find(supplierId, "SUPPLIER_CODE", supplierCode).orElse(null);
		if (catalog.selectable(saved))
			return catalog.suggestion(saved, "SAVED_SUPPLIER_CODE", "Sačuvan potvrđeni izbor za ovu šifru dobavljača.");
		String barcode = supplierProduct == null ? null : supplierProduct.getSupplierBarcode();
		saved = mappings.find(supplierId, "BARCODE", barcode).orElse(null);
		if (catalog.selectable(saved))
			return catalog.suggestion(saved, "SAVED_BARCODE", "Sačuvan potvrđeni izbor za ovaj barkod kod dobavljača.");
		String supplierCategory = supplierProduct == null ? null : supplierProduct.getCategoryText();
		saved = mappings.find(supplierId, "SUPPLIER_CATEGORY", ReceiptCategoryRules.normalize(supplierCategory)).orElse(null);
		if (catalog.selectable(saved))
			return catalog.suggestion(saved, "SAVED_SUPPLIER_CATEGORY", "Sačuvano mapiranje kategorije dobavljača: " + supplierCategory + ".");
		var rule = rules.suggest(itemName);
		var byName = rule == null ? null : catalog.bySlug(rule.slug(), "NAME_RULE", rule.reason());
		if (byName != null && !byName.requiresSelection()) return byName;
		// Supplier category is evidence only; broad supplier groups are never learned from a single SKU.
		if (supplierCategory != null && !supplierCategory.isBlank()) {
			String normalized = ReceiptCategoryRules.normalize(supplierCategory);
			var exact = catalog.all.stream().filter(c -> catalog.selectable(c.getId()))
					.filter(c -> ReceiptCategoryRules.normalize(c.getName()).equals(normalized)
							|| ReceiptCategoryRules.normalize(catalog.path(c)).equals(normalized)).toList();
			if (exact.size() == 1 && (byName == null || catalog.descendsFrom(exact.getFirst(), byName.categoryId())))
				return catalog.suggestion(exact.getFirst().getId(), "SUPPLIER_CATEGORY", "Prepoznata kategorija dobavljača: " + supplierCategory + ".");
		}
		return byName;
	}

	public void remember(Catalog catalog, UUID supplierId, String supplierCode, SupplierProduct supplierProduct, Product product, String actor,
			String supplierCategory, boolean rememberSupplierCategory) {
		// Older articles may still be assigned to broad groups; do not learn unusable categories.
		if (!catalog.selectable(product.getCategory().getId())) return;
		mappings.remember(supplierId, "SUPPLIER_CODE", supplierCode, product.getCategory().getId(), actor);
		String barcode = supplierProduct == null ? product.getBarcode() : supplierProduct.getSupplierBarcode();
		mappings.remember(supplierId, "BARCODE", barcode, product.getCategory().getId(), actor);
		if (rememberSupplierCategory)
			mappings.remember(supplierId, "SUPPLIER_CATEGORY", ReceiptCategoryRules.normalize(supplierCategory), product.getCategory().getId(), actor);
	}

	public static class Catalog {
		private final List<Category> all;
		private final Map<UUID, Category> byId = new HashMap<>();
		private final Map<String, Category> bySlug = new HashMap<>();
		private final Set<UUID> parents = new HashSet<>();
		Catalog(List<Category> all) {
			this.all = all;
			all.forEach(c -> { byId.put(c.getId(), c); bySlug.put(c.getSlug(), c); if (c.getParent() != null) parents.add(c.getParent().getId()); });
		}
		boolean enabled(Category category) {
			Set<UUID> seen = new HashSet<>();
			for (Category current = category; current != null; current = current.getParent())
				if (!current.isActive() || !current.isWebVisible() || !seen.add(current.getId())) return false;
			return category != null;
		}
		boolean selectable(UUID id) { return enabled(byId.get(id)) && !parents.contains(id); }
		boolean descendsFrom(Category category, UUID ancestor) {
			Set<UUID> seen = new HashSet<>();
			for (Category current = category; current != null && seen.add(current.getId()); current = current.getParent())
				if (current.getId().equals(ancestor)) return true;
			return false;
		}
		String path(Category category) {
			List<String> names = new ArrayList<>(); Set<UUID> seen = new HashSet<>();
			for (Category current = category; current != null && seen.add(current.getId()); current = current.getParent()) names.addFirst(current.getName());
			return String.join(" / ", names);
		}
		CategorySuggestion bySlug(String slug, String source, String reason) {
			Category category = bySlug.get(slug);
			return category == null ? null : suggestion(category.getId(), source, reason);
		}
		CategorySuggestion suggestion(UUID id, String source, String reason) {
			Category category = byId.get(id);
			return !enabled(category) ? null : new CategorySuggestion(id, path(category), source, reason, !selectable(id));
		}
	}
}
