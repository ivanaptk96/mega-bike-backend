package com.megabike.catalog.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.Arrays;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(authorities = {"ROLE_ADMIN", "PRODUCT_READ", "PRODUCT_WRITE"})
class CategoryTaxonomyIntegrationTests {
	@Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");
	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry r) {
		r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		r.add("spring.datasource.username", POSTGRES::getUsername);
		r.add("spring.datasource.password", POSTGRES::getPassword);
		r.add("spring.liquibase.change-log", () -> "classpath:/db/changelog/db.changelog-dev.xml");
		r.add("spring.docker.compose.enabled", () -> "false");
		r.add("megabike.security.jwt.secret", () -> "category-taxonomy-test-secret-that-is-long-enough");
	}
	@Autowired MockMvc mvc;
	@Autowired ObjectMapper mapper;
	@Autowired JdbcTemplate jdbc;
	CategoryResponse[] categories() throws Exception {
		return mapper.readValue(mvc.perform(get("/api/internal/categories")).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(), CategoryResponse[].class);
	}
	UUID id(String slug) { return jdbc.queryForObject("select id from category where slug = ?", UUID.class, slug); }
	@Test
	void storesCompleteTaxonomyButShowsOnlyEnabledBranches() throws Exception {
		var categories = categories();
		assertThat(Arrays.stream(categories).filter(c -> c.parentId() == null).map(CategoryResponse::name))
				.containsExactlyInAnyOrder("Biciklizam", "Roleri i skate", "Trotineti", "Fitnes");
		assertThat(categories).hasSize(115);
		assertThat(jdbc.queryForObject("select count(*) from category", Integer.class)).isEqualTo(172);
		assertThat(jdbc.queryForObject("select count(*) from category where web_visible = false and active = true", Integer.class)).isEqualTo(57);
	}
	@Test
	void preservesLegacyIdsAndReclassifiesKnownDevelopmentProducts() {
		assertThat(id("biciklizam-bicikli")).isEqualTo(UUID.fromString("40000000-0000-0000-0000-000000000001"));
		assertThat(id("biciklizam-delovi")).isEqualTo(UUID.fromString("40000000-0000-0000-0000-000000000002"));
		assertThat(id("biciklizam-bicikli-mtb")).isEqualTo(UUID.fromString("40000000-0000-0000-0000-000000000003"));
		assertThat(jdbc.queryForObject("select category_id from product where product_code = 'MB-P-0001'", UUID.class))
				.isEqualTo(id("biciklizam-oprema-kacige"));
	}
	@Test
	void rootCategoryFilterIncludesAllDescendantProducts() throws Exception {
		var result = mapper.readValue(mvc.perform(get("/api/internal/products").param("categoryId", id("biciklizam").toString()))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), ProductPageResponse.class);
		assertThat(result.content()).extracting(ProductResponse::productCode).contains("MB-P-0001", "MB-P-0002");
	}
	@Test
	void hiddenParentHidesItsChildrenAndProducts() throws Exception {
		UUID parent = id("biciklizam-oprema");
		jdbc.update("update category set web_visible = false where id = ?", parent);
		assertThat(categories()).extracting(CategoryResponse::slug).doesNotContain("biciklizam-oprema", "biciklizam-oprema-kacige");
		var result = mapper.readValue(mvc.perform(get("/api/internal/products")).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(), ProductPageResponse.class);
		assertThat(result.content()).extracting(ProductResponse::productCode).doesNotContain("MB-P-0001");
	}
	@Test
	void productCannotBeAssignedToAParentCategory() throws Exception {
		var request = new ProductRequest("TEST-" + UUID.randomUUID(), null, null, "Test product", null, null, id("biciklizam"),
				com.megabike.catalog.domain.ProductUnit.PIECE, java.math.BigDecimal.ONE, java.math.BigDecimal.TEN,
				new java.math.BigDecimal("20"), true, true);
		mvc.perform(post("/api/internal/products").contentType("application/json").content(mapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PRODUCT_CATEGORY_INVALID"));
	}

	@Test
	void categoryCannotBeMovedUnderItsOwnDescendant() throws Exception {
		UUID category = id("biciklizam-delovi");
		var request = new CategoryRequest("Delovi", "biciklizam-delovi", id("biciklizam-delovi-lanci"), true);
		mvc.perform(put("/api/internal/categories/" + category).contentType("application/json").content(mapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CATEGORY_PARENT_INVALID"));
	}
}
