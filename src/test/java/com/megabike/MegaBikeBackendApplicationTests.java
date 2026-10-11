package com.megabike;

import com.megabike.catalog.domain.CategoryRepository;
import com.megabike.catalog.domain.ProductRepository;
import com.megabike.identity.domain.RefreshTokenRepository;
import com.megabike.identity.domain.UserAccountRepository;
import com.megabike.inventory.domain.InventoryItemRepository;
import com.megabike.inventory.domain.StockLocationRepository;
import com.megabike.inventory.domain.StockMovementRepository;
import com.megabike.sef.domain.SefConnectionRepository;
import com.megabike.sef.domain.SefInvoiceAttachmentRepository;
import com.megabike.sef.domain.SefInvoiceDocumentRepository;
import com.megabike.sef.domain.SefInvoiceLineRepository;
import com.megabike.sef.domain.SefInvoicePaymentRepository;
import com.megabike.sef.domain.SefInvoiceRepository;
import com.megabike.sef.domain.SefInvoiceStatusEventRepository;
import com.megabike.sef.domain.SefInvoiceVatSummaryRepository;
import com.megabike.sef.domain.SefSyncRunRepository;
import com.megabike.supplier.domain.SupplierImportIssueRepository;
import com.megabike.supplier.domain.SupplierImportRunRepository;
import com.megabike.supplier.domain.SupplierPriceSnapshotRepository;
import com.megabike.supplier.domain.SupplierProductRepository;
import com.megabike.supplier.domain.SupplierRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
				+ "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
				+ "org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration"
})
class MegaBikeBackendApplicationTests {

	@Test
	void contextLoads() {
	}

	@TestConfiguration
	static class NoDatabaseTestConfiguration {

		@Bean
		org.springframework.jdbc.core.JdbcTemplate jdbcTemplate() {
			return Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
		}

		@Bean
		UserAccountRepository userAccountRepository() {
			return Mockito.mock(UserAccountRepository.class);
		}

		@Bean
		RefreshTokenRepository refreshTokenRepository() {
			return Mockito.mock(RefreshTokenRepository.class);
		}

		@Bean
		CategoryRepository categoryRepository() {
			return Mockito.mock(CategoryRepository.class);
		}

		@Bean
		ProductRepository productRepository() {
			return Mockito.mock(ProductRepository.class);
		}

		@Bean
		InventoryItemRepository inventoryItemRepository() {
			return Mockito.mock(InventoryItemRepository.class);
		}

		@Bean
		com.megabike.inventory.domain.GoodsReceiptRepository goodsReceiptRepository() {
			return Mockito.mock(com.megabike.inventory.domain.GoodsReceiptRepository.class);
		}

		@Bean
		com.megabike.inventory.domain.GoodsReceiptLineRepository goodsReceiptLineRepository() {
			return Mockito.mock(com.megabike.inventory.domain.GoodsReceiptLineRepository.class);
		}

		@Bean
		StockLocationRepository stockLocationRepository() {
			return Mockito.mock(StockLocationRepository.class);
		}

		@Bean
		StockMovementRepository stockMovementRepository() {
			return Mockito.mock(StockMovementRepository.class);
		}

		@Bean
		SupplierRepository supplierRepository() {
			return Mockito.mock(SupplierRepository.class);
		}

		@Bean
		SupplierProductRepository supplierProductRepository() {
			return Mockito.mock(SupplierProductRepository.class);
		}

		@Bean
		SupplierImportRunRepository supplierImportRunRepository() {
			return Mockito.mock(SupplierImportRunRepository.class);
		}

		@Bean
		SupplierImportIssueRepository supplierImportIssueRepository() {
			return Mockito.mock(SupplierImportIssueRepository.class);
		}

		@Bean
		SupplierPriceSnapshotRepository supplierPriceSnapshotRepository() {
			return Mockito.mock(SupplierPriceSnapshotRepository.class);
		}

		@Bean
		SefConnectionRepository sefConnectionRepository() {
			return Mockito.mock(SefConnectionRepository.class);
		}

		@Bean
		SefInvoiceRepository sefInvoiceRepository() {
			return Mockito.mock(SefInvoiceRepository.class);
		}

		@Bean
		SefInvoiceDocumentRepository sefInvoiceDocumentRepository() {
			return Mockito.mock(SefInvoiceDocumentRepository.class);
		}

		@Bean
		SefSyncRunRepository sefSyncRunRepository() {
			return Mockito.mock(SefSyncRunRepository.class);
		}

		@Bean
		SefInvoiceLineRepository sefInvoiceLineRepository() {
			return Mockito.mock(SefInvoiceLineRepository.class);
		}

		@Bean
		SefInvoiceVatSummaryRepository sefInvoiceVatSummaryRepository() {
			return Mockito.mock(SefInvoiceVatSummaryRepository.class);
		}

		@Bean
		SefInvoicePaymentRepository sefInvoicePaymentRepository() {
			return Mockito.mock(SefInvoicePaymentRepository.class);
		}

		@Bean
		SefInvoiceStatusEventRepository sefInvoiceStatusEventRepository() {
			return Mockito.mock(SefInvoiceStatusEventRepository.class);
		}

		@Bean
		SefInvoiceAttachmentRepository sefInvoiceAttachmentRepository() {
			return Mockito.mock(SefInvoiceAttachmentRepository.class);
		}
	}

}
