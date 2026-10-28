package com.orderpilot;

import static org.assertj.core.api.Assertions.assertThat;

import com.orderpilot.support.Api;
import com.orderpilot.support.Api.Res;
import com.orderpilot.support.Api.Tenant;
import com.orderpilot.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SecurityAndTenancyIT extends IntegrationTest {

    @Test
    void unauthenticatedRequestsAreRejected() {
        Res r = api().get("/api/products", null);
        assertThat(r.status()).isEqualTo(401);
        assertThat(r.code()).isEqualTo("UNAUTHORIZED");

        assertThat(api().get("/api/products", "not-a-jwt").status()).isEqualTo(401);
    }

    @Test
    void wrongPasswordIsRejectedWithoutRevealingWhichFieldWasWrong() {
        Tenant t = api().registerTenant("auth");
        Res wrongPassword = api().post("/api/auth/login", null, Map.of("email", t.adminEmail(), "password", "nope-nope"));
        Res unknownUser = api().post("/api/auth/login", null, Map.of("email", "ghost@nowhere.test", "password", "nope-nope"));
        assertThat(wrongPassword.status()).isEqualTo(401);
        assertThat(unknownUser.status()).isEqualTo(401);
        assertThat(wrongPassword.body().path("message").asText()).isEqualTo(unknownUser.body().path("message").asText());
    }

    @Test
    void tenantsCannotSeeOrTouchEachOthersData() {
        Api api = api();
        Tenant a = api.registerTenant("iso-a");
        Tenant b = api.registerTenant("iso-b");
        long productA = api.createProduct(a.token(), "A-SECRET", "10.00", 5, 1);
        long orderA = api.createOrder(a.token(), null, List.of(Api.line(productA, 1))).data().path("order").path("id").asLong();

        // B sees nothing of A ...
        assertThat(api.get("/api/products", b.token()).data().path("total").asLong()).isZero();
        assertThat(api.get("/api/orders", b.token()).data().path("total").asLong()).isZero();
        // ... and gets 404 (not 403: existence is not leaked) on direct access by id.
        assertThat(api.get("/api/products/" + productA, b.token()).status()).isEqualTo(404);
        assertThat(api.get("/api/orders/" + orderA, b.token()).status()).isEqualTo(404);
        assertThat(api.get("/api/inventory/" + productA, b.token()).status()).isEqualTo(404);

        // B cannot order A's product, pay or cancel A's order.
        assertThat(api.createOrder(b.token(), null, List.of(Api.line(productA, 1))).status()).isEqualTo(404);
        assertThat(api.post("/api/orders/" + orderA + "/payments", b.token(), Map.of("method", "CARD")).status()).isEqualTo(404);
        assertThat(api.post("/api/orders/" + orderA + "/cancel", b.token(), Map.of()).status()).isEqualTo(404);

        // A's data is intact.
        assertThat(api.get("/api/orders/" + orderA, a.token()).data().path("order").path("status").asText())
                .isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void rolesAreEnforcedByPermission() {
        Api api = api();
        Tenant t = api.registerTenant("rbac");
        long product = api.createProduct(t.token(), "RBAC-1", "20.00", 10, 1);
        String viewer = api.createUser(t.token(), "viewer@" + t.code() + ".test", "VIEWER");
        String staff = api.createUser(t.token(), "staff@" + t.code() + ".test", "STAFF");

        // viewer: read-only
        assertThat(api.get("/api/products", viewer).status()).isEqualTo(200);
        assertThat(api.createOrder(viewer, null, List.of(Api.line(product, 1))).status()).isEqualTo(403);
        assertThat(api.post("/api/inventory/" + product + "/adjustments", viewer,
                Map.of("type", "INBOUND", "quantity", 5, "note", "x")).status()).isEqualTo(403);
        assertThat(api.post("/api/ai/chat", viewer, Map.of("message", "hi")).status()).isEqualTo(403);

        // staff: can sell, cannot manage catalog, users or refunds
        assertThat(api.createOrder(staff, null, List.of(Api.line(product, 1))).status()).isEqualTo(200);
        assertThat(api.post("/api/products", staff, Map.of("sku", "NOPE", "name", "n", "category", "c",
                "price", "1.00", "cost", "0.50", "lowStockThreshold", 1, "initialStock", 1)).status()).isEqualTo(403);
        assertThat(api.get("/api/users", staff).status()).isEqualTo(403);
        assertThat(api.post("/api/orders/1/refund", staff, Map.of("reason", "x")).status()).isEqualTo(403);
    }

    @Test
    void logoutRevokesTheTokenImmediately() {
        Api api = api();
        Tenant t = api.registerTenant("logout");
        assertThat(api.get("/api/auth/me", t.token()).status()).isEqualTo(200);
        assertThat(api.post("/api/auth/logout", t.token(), Map.of()).status()).isEqualTo(200);
        assertThat(api.get("/api/auth/me", t.token()).status()).isEqualTo(401);
    }

    @Test
    void workspaceMustKeepAnAdmin() {
        Api api = api();
        Tenant t = api.registerTenant("lastadmin");
        long adminId = api.get("/api/auth/me", t.token()).data().path("id").asLong();
        Res r = api.call(org.springframework.http.HttpMethod.PUT, "/api/users/" + adminId, t.token(),
                Map.of("displayName", "Me", "role", "STAFF", "status", "ACTIVE"), null);
        assertThat(r.status()).isEqualTo(409);
    }
}
