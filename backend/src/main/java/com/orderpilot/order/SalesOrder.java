package com.orderpilot.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class SalesOrder {
    private Long id;
    private Long tenantId;
    private String orderNo;
    private String customerName;
    private String customerEmail;
    private String shippingAddress;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private Integer itemCount;
    private String idempotencyKey;
    private String remark;
    private Long createdBy;
    private LocalDateTime expiresAt;
    private LocalDateTime paidAt;
    private LocalDateTime shippedAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private String cancelReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
