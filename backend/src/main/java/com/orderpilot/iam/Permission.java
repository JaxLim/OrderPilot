package com.orderpilot.iam;

/** Fine-grained authorities checked with {@code @PreAuthorize("hasAuthority('...')")}. */
public final class Permission {

    public static final String PRODUCT_READ = "product:read";
    public static final String PRODUCT_WRITE = "product:write";
    public static final String INVENTORY_READ = "inventory:read";
    public static final String INVENTORY_WRITE = "inventory:write";
    public static final String ORDER_READ = "order:read";
    public static final String ORDER_WRITE = "order:write";
    public static final String ORDER_FULFIL = "order:fulfil";
    public static final String PAYMENT_WRITE = "payment:write";
    public static final String PAYMENT_REFUND = "payment:refund";
    public static final String REPORT_READ = "report:read";
    public static final String USER_MANAGE = "user:manage";
    public static final String AUDIT_READ = "audit:read";
    public static final String AI_USE = "ai:use";

    private Permission() {
    }
}
