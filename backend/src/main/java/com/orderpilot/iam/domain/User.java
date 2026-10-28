package com.orderpilot.iam.domain;

import com.orderpilot.iam.Role;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class User {
    private Long id;
    private Long tenantId;
    private String email;
    private String passwordHash;
    private String displayName;
    private Role role;
    private String status;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
}
