package com.orderpilot.iam;

import com.orderpilot.iam.domain.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 120) String companyName,
            @NotBlank @Pattern(regexp = "^[a-z0-9-]{3,40}$",
                    message = "must be 3-40 lowercase letters, digits or dashes") String tenantCode,
            @NotBlank @Size(max = 80) String adminName,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 72) String password) {
    }

    public record TenantInfo(long id, String code, String name, String plan) {
    }

    public record Me(long id, String email, String displayName, Role role, Set<String> permissions,
                     TenantInfo tenant) {
    }

    public record LoginResponse(String token, Instant expiresAt, Me user) {
    }

    public record UserView(long id, String email, String displayName, Role role, String status,
                           LocalDateTime lastLoginAt, LocalDateTime createdAt) {
        public static UserView of(User u) {
            return new UserView(u.getId(), u.getEmail(), u.getDisplayName(), u.getRole(), u.getStatus(),
                    u.getLastLoginAt(), u.getCreatedAt());
        }
    }

    public record CreateUserRequest(@NotBlank @Email String email, @NotBlank @Size(max = 80) String displayName,
                                    @NotNull Role role, @NotBlank @Size(min = 8, max = 72) String password) {
    }

    public record UpdateUserRequest(@NotBlank @Size(max = 80) String displayName, @NotNull Role role,
                                    @NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status) {
    }
}
