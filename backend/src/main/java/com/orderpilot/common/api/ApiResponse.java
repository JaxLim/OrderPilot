package com.orderpilot.common.api;

/**
 * Uniform response envelope. HTTP status codes still carry the outcome; {@code code} gives
 * clients a stable machine-readable reason (e.g. INSUFFICIENT_STOCK) to branch on.
 */
public record ApiResponse<T>(String code, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", "success", data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>("OK", "success", null);
    }

    public static <T> ApiResponse<T> error(String code, String message, T data) {
        return new ApiResponse<>(code, message, data);
    }
}
