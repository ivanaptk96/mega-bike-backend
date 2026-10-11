package com.megabike.inventory.api;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
public record GoodsReceiptRequest(@NotNull UUID stockLocationId) {
	public record NewProduct(@NotBlank @Size(max = 240) String name, @NotNull UUID categoryId) {}
	public record Approval(@NotEmpty @Size(max = 100) List<@Valid Line> lines, @Min(0) long version) {}
	public record Line(@NotNull UUID lineId, UUID productId, @Valid NewProduct newProduct,
			@NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 9, fraction = 0) BigDecimal quantity,
			@NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal retailPrice) {}
}
