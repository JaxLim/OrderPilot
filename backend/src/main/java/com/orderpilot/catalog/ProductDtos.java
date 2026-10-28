package com.orderpilot.catalog;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

public final class ProductDtos {

    private ProductDtos() {
    }

    public record CreateProductRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9._-]{2,64}$", message = "letters, digits, '.', '_' or '-'") String sku,
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(max = 60) String category,
            @Size(max = 1000) String description,
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal cost,
            @NotNull @Min(0) Integer lowStockThreshold,
            @NotNull @Min(0) Integer initialStock) {
    }

    public record UpdateProductRequest(
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(max = 60) String category,
            @Size(max = 1000) String description,
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal cost,
            @NotNull @Min(0) Integer lowStockThreshold) {
    }

    /** Product joined with its live stock level (stock is never served from cache). */
    @Data
    public static class ProductView {
        private Long id;
        private String sku;
        private String name;
        private String category;
        private String description;
        private BigDecimal price;
        private BigDecimal cost;
        private Integer lowStockThreshold;
        private String status;
        private Integer available;
        private Integer reserved;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public boolean isLowStock() {
            return available != null && lowStockThreshold != null && available <= lowStockThreshold;
        }
    }
}
