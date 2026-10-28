package com.orderpilot.iam.domain;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class Tenant {
    private Long id;
    private String code;
    private String name;
    private String plan;
    private String status;
    private LocalDateTime createdAt;
}
