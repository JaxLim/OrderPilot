package com.orderpilot.iam;

import static com.orderpilot.iam.Permission.*;

import java.util.Set;

/**
 * Roles are bundles of permissions. Endpoints check permissions, never role names, so the
 * mapping can move to a database table without touching controllers.
 */
public enum Role {
    ADMIN(Set.of(PRODUCT_READ, PRODUCT_WRITE, INVENTORY_READ, INVENTORY_WRITE, ORDER_READ, ORDER_WRITE,
            ORDER_FULFIL, PAYMENT_WRITE, PAYMENT_REFUND, REPORT_READ, USER_MANAGE, AUDIT_READ, AI_USE)),
    MANAGER(Set.of(PRODUCT_READ, PRODUCT_WRITE, INVENTORY_READ, INVENTORY_WRITE, ORDER_READ, ORDER_WRITE,
            ORDER_FULFIL, PAYMENT_WRITE, PAYMENT_REFUND, REPORT_READ, AUDIT_READ, AI_USE)),
    STAFF(Set.of(PRODUCT_READ, INVENTORY_READ, ORDER_READ, ORDER_WRITE, ORDER_FULFIL, PAYMENT_WRITE,
            REPORT_READ, AI_USE)),
    VIEWER(Set.of(PRODUCT_READ, INVENTORY_READ, ORDER_READ, REPORT_READ));

    private final Set<String> permissions;

    Role(Set<String> permissions) {
        this.permissions = permissions;
    }

    public Set<String> permissions() {
        return permissions;
    }
}
