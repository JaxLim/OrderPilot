package com.orderpilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orderpilot.common.config.ProductionConfigGuard;
import com.orderpilot.common.tenant.TenantContext;
import com.orderpilot.common.tenant.TenantGuardInterceptor;
import com.orderpilot.iam.AuthUser;
import com.orderpilot.iam.JwtService;
import com.orderpilot.iam.Permission;
import com.orderpilot.iam.Role;
import com.orderpilot.inventory.InventoryDtos.StockLine;
import com.orderpilot.order.OrderStatus;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** Fast tests without containers. */
class UnitTests {

    @Nested
    class OrderStateMachine {
        @Test
        void allowsOnlyDocumentedTransitions() {
            assertThat(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.PAID)).isTrue();
            assertThat(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
            assertThat(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.SHIPPED)).isFalse();
            assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.REFUNDED)).isTrue();
            assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
            assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.REFUNDED)).isFalse();
            for (OrderStatus terminal : List.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED, OrderStatus.REFUNDED)) {
                assertThat(terminal.next()).isEmpty();
            }
        }
    }

    @Nested
    class TenantGuard {
        private void check(String id, String sql) throws Exception {
            Method m = TenantGuardInterceptor.class.getDeclaredMethod("check", String.class, String.class);
            m.setAccessible(true);
            try {
                m.invoke(null, id, sql);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw (Exception) e.getCause();
            }
        }

        @Test
        void rejectsTenantTableQueriesWithoutTenantPredicate() {
            assertThatThrownBy(() -> check("x.findAll", "SELECT * FROM sales_order WHERE id = ?"))
                    .isInstanceOf(TenantGuardInterceptor.TenantIsolationViolation.class);
            assertThatThrownBy(() -> check("x.bump", "UPDATE   inventory SET available = 0"))
                    .isInstanceOf(TenantGuardInterceptor.TenantIsolationViolation.class);
            assertThatThrownBy(() -> check("x.join", "SELECT 1 FROM tenant t JOIN `product` p ON p.id = t.id"))
                    .isInstanceOf(TenantGuardInterceptor.TenantIsolationViolation.class);
        }

        @Test
        void acceptsScopedAndNonTenantQueries() throws Exception {
            check("x.ok", "SELECT * FROM sales_order WHERE tenant_id = ? AND id = ?");
            check("x.tenant", "SELECT * FROM tenant WHERE code = ?");
            check("x.outbox", "SELECT * FROM outbox_event WHERE status = 'NEW'");
            check("x.alias", "SELECT * FROM product_alias WHERE id = 1"); // not a tenant table
        }
    }

    @Nested
    class StockLineMerging {
        @Test
        void mergesDuplicatesAndSortsByProductIdToAvoidDeadlocks() throws Exception {
            Method merge = com.orderpilot.inventory.InventoryService.class.getDeclaredMethod("merge", List.class);
            merge.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<StockLine> out = (List<StockLine>) merge.invoke(null, List.of(
                    new StockLine(9, 1), new StockLine(3, 2), new StockLine(9, 4)));
            assertThat(out).containsExactly(new StockLine(3, 2), new StockLine(9, 5));
        }
    }

    @Nested
    class Jwt {
        private final JwtService jwt = new JwtService(
                new JwtService.JwtProperties("unit-test-secret-0123456789abcdef0123456789", Duration.ofMinutes(5)));

        @Test
        void roundTripsClaimsAndRejectsTampering() {
            AuthUser user = new AuthUser(7, 42, "a@b.test", "Ann", Role.MANAGER);
            String token = jwt.issue(user).token();
            assertThat(jwt.parse(token).user()).isEqualTo(user);

            String tampered = token.substring(0, token.length() - 3) + (token.endsWith("A") ? "BBB" : "AAA");
            assertThatThrownBy(() -> jwt.parse(tampered)).isInstanceOf(io.jsonwebtoken.JwtException.class);
        }

        @Test
        void rejectsShortSecrets() {
            assertThatThrownBy(() -> new JwtService(new JwtService.JwtProperties("short", Duration.ofMinutes(1))))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    class Roles {
        @Test
        void permissionsAreCumulativeAndViewerIsReadOnly() {
            assertThat(Role.ADMIN.permissions()).containsAll(Role.MANAGER.permissions());
            assertThat(Role.MANAGER.permissions()).containsAll(Role.STAFF.permissions());
            assertThat(Role.STAFF.permissions()).containsAll(Role.VIEWER.permissions());
            assertThat(Role.VIEWER.permissions()).allMatch(p -> p.endsWith(":read"));
            assertThat(Role.MANAGER.permissions()).doesNotContain(Permission.USER_MANAGE);
        }
    }

    @Nested
    class AiConfiguration {
        private boolean enabledFor(String key) {
            var env = new org.springframework.mock.env.MockEnvironment();
            if (key != null) {
                env.setProperty("AI_API_KEY", key);
            }
            new com.orderpilot.ai.AiKeyEnvironmentPostProcessor().postProcessEnvironment(env, null);
            return env.getProperty("orderpilot.ai.enabled", Boolean.class);
        }

        @Test
        void llmModeOnlyWithANonBlankKey() {
            assertThat(enabledFor(null)).isFalse();
            assertThat(enabledFor("   ")).isFalse();
            assertThat(enabledFor("sk-test-not-real")).isTrue();
        }

        @Test
        void blankKeyGetsAPlaceholderSoSpringAiCanStart() {
            var env = new org.springframework.mock.env.MockEnvironment().withProperty("AI_API_KEY", "");
            new com.orderpilot.ai.AiKeyEnvironmentPostProcessor().postProcessEnvironment(env, null);
            assertThat(env.getProperty("spring.ai.openai.api-key")).isEqualTo("not-configured");
        }

        @Test
        void errorLogsNeverContainTheKey() throws Exception {
            Method redact = com.orderpilot.ai.AssistantService.class.getDeclaredMethod("redact", Throwable.class);
            redact.setAccessible(true);
            String out = (String) redact.invoke(null,
                    new IllegalStateException("401 Unauthorized: invalid key sk-abc123DEF456ghi789 provided"));
            assertThat(out).doesNotContain("sk-abc123DEF456ghi789").contains("sk-***");
        }
    }

    @Nested
    class TenantContextScoping {
        @Test
        void callAsRestoresPreviousTenant() {
            TenantContext.set(1L);
            try {
                long inner = TenantContext.callAs(2L, TenantContext::require);
                assertThat(inner).isEqualTo(2L);
                assertThat(TenantContext.get()).isEqualTo(1L);
            } finally {
                TenantContext.clear();
            }
            assertThat(TenantContext.get()).isNull();
        }
    }

    @Nested
    class ProductionGuard {
        private static final String GOOD_SECRET = "9f3c1e7a5b2d4680ac17e93b6d08f25c41a7be90";

        private MockEnvironment prod() {
            MockEnvironment env = new MockEnvironment()
                    .withProperty("orderpilot.jwt.secret", GOOD_SECRET)
                    .withProperty("orderpilot.demo.seed", "false")
                    .withProperty("spring.datasource.password", "db-password-from-secrets-manager")
                    .withProperty("spring.rabbitmq.password", "mq-password-from-secrets-manager");
            env.setActiveProfiles("prod");
            return env;
        }

        private void guard(MockEnvironment env) {
            new ProductionConfigGuard().postProcessEnvironment(env, null);
        }

        @Test
        void acceptsRealConfiguration() {
            guard(prod());
        }

        @Test
        void ignoresOtherProfiles() {
            MockEnvironment env = new MockEnvironment().withProperty("orderpilot.demo.seed", "true");
            env.setActiveProfiles("demo");
            guard(env);
        }

        @Test
        void rejectsMissingWeakAndPlaceholderSecrets() {
            for (String secret : List.of("", "short", "replace-with-at-least-32-random-characters",
                    "change-me-in-production-0123456789abcdef0123456789abcdef",
                    "docker-compose-demo-secret-change-me-0123456789")) {
                assertThatThrownBy(() -> guard(prod().withProperty("orderpilot.jwt.secret", secret)))
                        .isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_SECRET");
            }
        }

        @Test
        void rejectsDemoSeedAndDemoProfile() {
            assertThatThrownBy(() -> guard(prod().withProperty("orderpilot.demo.seed", "true")))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("DEMO_SEED");
            MockEnvironment both = prod();
            both.setActiveProfiles("prod", "demo");
            assertThatThrownBy(() -> guard(both))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("demo profile");
        }

        @Test
        void rejectsDefaultInfrastructureCredentials() {
            assertThatThrownBy(() -> guard(prod().withProperty("spring.datasource.password", "orderpilot")))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("MYSQL_PASSWORD");
            assertThatThrownBy(() -> guard(prod().withProperty("spring.rabbitmq.password", "guest")))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("RABBITMQ_PASSWORD");
        }

        @Test
        void neverPrintsTheRejectedValue() {
            assertThatThrownBy(() -> guard(prod().withProperty("orderpilot.jwt.secret", "short-secret-value")))
                    .hasMessageNotContaining("short-secret-value");
        }
    }

    @Nested
    class SigningKey {
        @Test
        void blankSecretGetsAKeyNobodyElseHolds() {
            JwtService first = new JwtService(new JwtService.JwtProperties("", Duration.ofMinutes(5)));
            JwtService second = new JwtService(new JwtService.JwtProperties(" ", Duration.ofMinutes(5)));
            AuthUser user = new AuthUser(1L, 1L, "a@b.test", "A", Role.ADMIN);
            String token = first.issue(user).token();
            assertThat(first.parse(token).user().userId()).isEqualTo(1L);
            assertThatThrownBy(() -> second.parse(token)).isInstanceOf(io.jsonwebtoken.JwtException.class);
        }
    }
}
