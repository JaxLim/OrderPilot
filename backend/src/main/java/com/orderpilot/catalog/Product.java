package com.orderpilot.catalog;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class Product {
    private Long id;
    private Long tenantId;
    private String sku;
    private String name;
    private String category;
    private String description;
    private BigDecimal price;
    private BigDecimal cost;
    private Integer lowStockThreshold;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
}
