package com.orderpilot.inventory;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Data;

public final class InventoryDtos {

    private InventoryDtos() {
    }

    /** One stock line of an order. */
    public record StockLine(long productId, int quantity) {
    }

    @Data
    public static class InventoryRow {
        private Long productId;
        private String sku;
        private String productName;
        private String category;
        private String productStatus;
        private Integer available;
        private Integer reserved;
        private Integer lowStockThreshold;
        private Long version;
        private LocalDateTime updatedAt;

        public boolean isLowStock() {
            return available <= lowStockThreshold;
        }
    }

    @Data
    public static class Movement {
        private Long id;
        private Long tenantId;
        private Long productId;
        private String sku;
        private String productName;
        private String type;
        private Integer quantity;
        private String refType;
        private String refId;
        private String note;
        private Long operatorId;
        private LocalDateTime createdAt;
    }

    /** INBOUND adds received goods; ADJUST applies a signed correction (stock count, damage, ...). */
    public record AdjustRequest(
            @NotNull @Pattern(regexp = "INBOUND|ADJUST") String type,
            @NotNull @Min(-100000) @Max(100000) Integer quantity,
            @NotBlank @Size(max = 255) String note) {
    }
}
