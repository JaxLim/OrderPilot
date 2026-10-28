package com.orderpilot;

import static org.assertj.core.api.Assertions.assertThat;

import com.orderpilot.support.Api;
import com.orderpilot.support.Api.Res;
import com.orderpilot.support.Api.Tenant;
import com.orderpilot.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class IdempotencyIT extends IntegrationTest {

    @Test
    void retriedCreateOrderReturnsTheSameOrder() {
        Api api = api();
        Tenant t = api.registerTenant("idem");
        long p = api.createProduct(t.token(), "IDEM-1", "12.50", 10, 0);

        Res first = api.createOrder(t.token(), "client-retry-1", List.of(Api.line(p, 2)));
        Res retry = api.createOrder(t.token(), "client-retry-1", List.of(Api.line(p, 2)));

        assertThat(first.status()).isEqualTo(200);
        assertThat(retry.status()).isEqualTo(200);
        assertThat(retry.data().path("order").path("id").asLong()).isEqualTo(first.data().path("order").path("id").asLong());
        assertThat(api.inventory(t.token(), p).path("reserved").asInt()).isEqualTo(2); // stock reserved once
    }

    @Test
    void concurrentDuplicatesCreateExactlyOneOrder() throws Exception {
        Api api = api();
        Tenant t = api.registerTenant("idem-race");
        long p = api.createProduct(t.token(), "IDEM-R", "3.00", 100, 0);

        List<Res> results = InventoryConcurrencyIT.runConcurrently(15,
                i -> api.createOrder(t.token(), "double-click", List.of(Api.line(p, 1))));

        Set<Long> ids = results.stream().filter(r -> r.status() == 200)
                .map(r -> r.data().path("order").path("id").asLong()).collect(Collectors.toSet());
        assertThat(ids).hasSize(1);
        assertThat(results).allSatisfy(r -> assertThat(r.status() == 200 || "REQUEST_IN_PROGRESS".equals(r.code()))
                .as(r.body().toString()).isTrue());
        Integer orders = jdbc.queryForObject("SELECT COUNT(*) FROM sales_order WHERE tenant_id = ?", Integer.class, t.tenantId());
        assertThat(orders).isEqualTo(1);
        assertThat(api.inventory(t.token(), p).path("reserved").asInt()).isEqualTo(1);
    }

    @Test
    void sameKeyIsScopedPerTenant() {
        Api api = api();
        Tenant a = api.registerTenant("idem-a");
        Tenant b = api.registerTenant("idem-b");
        long pa = api.createProduct(a.token(), "K-1", "3.00", 5, 0);
        long pb = api.createProduct(b.token(), "K-1", "3.00", 5, 0);

        long oa = api.createOrder(a.token(), "shared-key", List.of(Api.line(pa, 1))).data().path("order").path("id").asLong();
        long ob = api.createOrder(b.token(), "shared-key", List.of(Api.line(pb, 1))).data().path("order").path("id").asLong();
        assertThat(oa).isNotEqualTo(ob);
    }

    @Test
    void declinedPaymentCanBeRetriedWithTheSameKey() {
        Api api = api();
        Tenant t = api.registerTenant("pay-retry");
        long p = api.createProduct(t.token(), "PR-1", "40.00", 5, 0);
        long order = api.createOrder(t.token(), null, List.of(Api.line(p, 1))).data().path("order").path("id").asLong();

        Res declined = api.post("/api/orders/" + order + "/payments", t.token(),
                Map.of("method", "CARD", "simulateDecline", true), "pay-key");
        assertThat(declined.status()).isEqualTo(402);
        assertThat(declined.code()).isEqualTo("PAYMENT_DECLINED");

        Res ok = api.post("/api/orders/" + order + "/payments", t.token(), Map.of("method", "CARD"), "pay-key");
        assertThat(ok.status()).as(ok.body().toString()).isEqualTo(200);
        Res replay = api.post("/api/orders/" + order + "/payments", t.token(), Map.of("method", "CARD"), "pay-key");
        assertThat(replay.data().path("paymentNo").asText()).isEqualTo(ok.data().path("paymentNo").asText());

        // a different key cannot charge the order a second time
        Res second = api.post("/api/orders/" + order + "/payments", t.token(), Map.of("method", "CARD"), "other-key");
        assertThat(second.code()).isEqualTo("INVALID_STATE");
        Integer captured = jdbc.queryForObject(
                "SELECT COUNT(*) FROM payment WHERE order_id = ? AND status = 'SUCCEEDED'", Integer.class, order);
        assertThat(captured).isEqualTo(1);
    }
}
