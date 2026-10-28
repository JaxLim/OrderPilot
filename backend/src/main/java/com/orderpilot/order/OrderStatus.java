package com.orderpilot.order;

import java.util.EnumSet;
import java.util.Set;

/**
 * Order lifecycle:
 * <pre>
 *  PENDING_PAYMENT ──pay──▶ PAID ──ship──▶ SHIPPED ──complete──▶ COMPLETED
 *        │                   │
 *        └─cancel/timeout──▶ CANCELLED      └─refund──▶ REFUNDED
 * </pre>
 * Transitions are enforced twice: here (fast, readable error) and in SQL with
 * {@code WHERE status = #{from}} (race-proof, e.g. "pay" vs "timeout" on the same order).
 */
public enum OrderStatus {
    PENDING_PAYMENT, PAID, SHIPPED, COMPLETED, CANCELLED, REFUNDED;

    public Set<OrderStatus> next() {
        return switch (this) {
            case PENDING_PAYMENT -> EnumSet.of(PAID, CANCELLED);
            case PAID -> EnumSet.of(SHIPPED, REFUNDED);
            case SHIPPED -> EnumSet.of(COMPLETED);
            case COMPLETED, CANCELLED, REFUNDED -> EnumSet.noneOf(OrderStatus.class);
        };
    }

    public boolean canTransitionTo(OrderStatus target) {
        return next().contains(target);
    }

    /** Statuses that count as realised revenue. */
    public static final Set<OrderStatus> REVENUE = EnumSet.of(PAID, SHIPPED, COMPLETED);
}
