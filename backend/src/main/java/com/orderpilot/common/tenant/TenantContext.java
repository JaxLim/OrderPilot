package com.orderpilot.common.tenant;

import com.orderpilot.common.error.BizException;
import com.orderpilot.common.error.ErrorCode;
import java.util.function.Supplier;

/**
 * Holds the tenant of the current unit of work (HTTP request, MQ message, scheduled job).
 * Set by the JWT filter from the signed token — never from a client-supplied header — and
 * always cleared in a finally block so pooled threads cannot leak a tenant.
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(Long tenantId) {
        CURRENT.set(tenantId);
    }

    public static Long get() {
        return CURRENT.get();
    }

    public static long require() {
        Long id = CURRENT.get();
        if (id == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "No tenant bound to the current request");
        }
        return id;
    }

    public static void clear() {
        CURRENT.remove();
    }

    /** Runs work on behalf of a tenant (used by consumers and jobs), restoring the previous value. */
    public static <T> T callAs(long tenantId, Supplier<T> work) {
        Long previous = CURRENT.get();
        CURRENT.set(tenantId);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static void runAs(long tenantId, Runnable work) {
        callAs(tenantId, () -> {
            work.run();
            return null;
        });
    }
}
