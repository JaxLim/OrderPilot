package com.orderpilot.order;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class OrderItem {
    private Long id;
    private Long tenantId;
    private Long orderId;
    private Long productId;
    private String sku;
    private String productName;
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal lineTotal;
}
