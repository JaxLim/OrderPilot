package com.orderpilot.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class Payment {
    private Long id;
    private Long tenantId;
    private String paymentNo;
    private Long orderId;
    private String orderNo;
    private BigDecimal amount;
    private String method;
    private String status;
    private String providerTxnId;
    private String idempotencyKey;
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
}
