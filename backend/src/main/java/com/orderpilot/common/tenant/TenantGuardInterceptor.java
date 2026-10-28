package com.orderpilot.common.tenant;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.stereotype.Component;

/**
 * Defence in depth for shared-schema multi-tenancy. Every SQL statement that touches a
 * tenant-owned table must filter or write {@code tenant_id}; a statement that forgets it fails
 * fast instead of silently reading another tenant's rows.
 *
 * <p>Statements that are intentionally cross-tenant (login by e-mail, the outbox relay, the order
 * expiry sweeper) follow the naming convention {@code *Global} and are exempt.
 */
@Component
@Intercepts({
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
})
public class TenantGuardInterceptor implements Interceptor {

    static final Set<String> TENANT_TABLES = Set.of(
            "app_user", "product", "inventory", "inventory_movement", "sales_order",
            "order_item", "payment", "notification", "audit_log");

    private static final Pattern TABLE_REF = Pattern.compile(
            "\\b(?:from|join|into|update)\\s+`?(" + String.join("|", TENANT_TABLES) + ")`?\\b");

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
        Object param = invocation.getArgs()[1];
        if (!ms.getId().endsWith("Global")) {
            BoundSql boundSql = ms.getBoundSql(param);
            check(ms.getId(), boundSql.getSql());
        }
        return invocation.proceed();
    }

    static void check(String statementId, String sql) {
        String normalized = sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (TABLE_REF.matcher(normalized).find() && !normalized.contains("tenant_id")) {
            throw new TenantIsolationViolation(
                    "Statement " + statementId + " touches a tenant table without a tenant_id predicate");
        }
    }

    public static class TenantIsolationViolation extends IllegalStateException {
        public TenantIsolationViolation(String message) {
            super(message);
        }
    }
}
