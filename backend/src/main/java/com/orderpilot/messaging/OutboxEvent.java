package com.orderpilot.messaging;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class OutboxEvent {
    private Long id;
    private String eventId;
    private Long tenantId;
    private String aggregateType;
    private String aggregateId;
    private String eventType;
    private String payload;
    private String status;
    private Integer attempts;
    private LocalDateTime nextAttemptAt;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;
}
