package com.megabike;

import com.megabike.catalog.domain.CategoryRepository;
import com.megabike.catalog.domain.ProductRepository;
import com.megabike.identity.domain.RefreshTokenRepository;
import com.megabike.identity.domain.UserAccountRepository;
import com.megabike.inventory.domain.InventoryItemRepository;
import com.megabike.inventory.domain.StockLocationRepository;
import com.megabike.inventory.domain.StockMovementRepository;
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
	}

}
