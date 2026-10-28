package com.orderpilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.orderpilot.support.Api;
import com.orderpilot.support.Api.Res;
import com.orderpilot.support.Api.Tenant;
import com.orderpilot.support.IntegrationTest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OrderLifecycleIT extends IntegrationTest {

    @Test
    void happyPathPayShipCompleteWithStockAccounting() {
        Api api = api();
        Tenant t = api.registerTenant("life");
        long p = api.createProduct(t.token(), "LIFE-1", "25.00", 20, 2);

        Res created = api.createOrder(t.token(), null, List.of(Api.line(p, 3)));
        long order = created.data().path("order").path("id").asLong();
        assertThat(created.data().path("order").path("totalAmount").decimalValue()).isEqualByComparingTo("75.00");
        assertStock(api, t, p, 17, 3);

        Res paid = api.post("/api/orders/" + order + "/payments", t.token(), Map.of("method", "ALIPAY"));
        assertThat(paid.data().path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(status(api, t, order)).isEqualTo("PAID");
        assertStock(api, t, p, 17, 0); // reserved stock committed on payment

        assertThat(api.post("/api/orders/" + order + "/complete", t.token(), Map.of()).code()).isEqualTo("INVALID_STATE");
        assertThat(api.post("/api/orders/" + order + "/ship", t.token(), Map.of()).status()).isEqualTo(200);
        assertThat(api.post("/api/orders/" + order + "/complete", t.token(), Map.of()).status()).isEqualTo(200);
        assertThat(status(api, t, order)).isEqualTo("COMPLETED");
        assertThat(api.post("/api/orders/" + order + "/refund", t.token(), Map.of("reason", "late")).code())
                .isEqualTo("INVALID_STATE");

        JsonNode moves = api.get("/api/inventory/movements?productId=" + p, t.token()).data().path("items");
        assertThat(moves.findValuesAsText("type")).containsExactly("DEDUCT", "RESERVE", "INBOUND");
    }

    @Test
    void cancelReleasesReservedStock() {
        Api api = api();
        Tenant t = api.registerTenant("cancel");
        long p = api.createProduct(t.token(), "CAN-1", "10.00", 5, 0);
        long order = api.createOrder(t.token(), null, List.of(Api.line(p, 4))).data().path("order").path("id").asLong();
        assertStock(api, t, p, 1, 4);

        assertThat(api.post("/api/orders/" + order + "/cancel", t.token(), Map.of("reason", "customer request")).status())
                .isEqualTo(200);
        assertStock(api, t, p, 5, 0);
        assertThat(api.post("/api/orders/" + order + "/payments", t.token(), Map.of("method", "CARD")).code())
                .isEqualTo("INVALID_STATE");
    }

    @Test
    void refundRestocksPaidOrder() {
        Api api = api();
        Tenant t = api.registerTenant("refund");
        long p = api.createProduct(t.token(), "REF-1", "10.00", 5, 0);
        long order = api.createOrder(t.token(), null, List.of(Api.line(p, 2))).data().path("order").path("id").asLong();
        api.post("/api/orders/" + order + "/payments", t.token(), Map.of("method", "CARD"));
        assertStock(api, t, p, 3, 0);

        Res refund = api.post("/api/orders/" + order + "/refund", t.token(), Map.of("reason", "damaged"));
        assertThat(refund.data().path("status").asText()).isEqualTo("REFUNDED");
        assertThat(status(api, t, order)).isEqualTo("REFUNDED");
        assertStock(api, t, p, 5, 0);
    }

    @Test
    void unpaidOrderIsCancelledByTheRabbitMqDelayQueue() {
        Api api = api();
        Tenant t = api.registerTenant("timeout");
        long p = api.createProduct(t.token(), "TO-1", "10.00", 3, 0);
        long order = api.createOrder(t.token(), null, List.of(Api.line(p, 3))).data().path("order").path("id").asLong();
        assertStock(api, t, p, 0, 3);

        // payment-timeout is 4s in tests and the sweeper is disabled, so only the TTL+DLX path can cancel it.
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500))
                .until(() -> "CANCELLED".equals(status(api, t, order)));
        assertStock(api, t, p, 3, 0);
        JsonNode o = api.get("/api/orders/" + order, t.token()).data().path("order");
        assertThat(o.path("cancelReason").asText()).isEqualTo("Payment timeout");

        // paying after expiry is refused
        assertThat(api.post("/api/orders/" + order + "/payments", t.token(), Map.of("method", "CARD")).code())
                .isEqualTo("INVALID_STATE");
    }

    @Test
    void domainEventsFlowThroughOutboxToNotifications() {
        Api api = api();
        Tenant t = api.registerTenant("events");
        long p = api.createProduct(t.token(), "EV-1", "10.00", 6, 3);
        long order = api.createOrder(t.token(), null, List.of(Api.line(p, 4))).data().path("order").path("id").asLong();
        api.post("/api/orders/" + order + "/payments", t.token(), Map.of("method", "CARD"));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            JsonNode feed = api.get("/api/notifications", t.token()).data();
            assertThat(feed.path("items").findValuesAsText("type"))
                    .contains("order.created", "order.paid", "inventory.low_stock");
        });
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Integer unsent = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM outbox_event WHERE tenant_id = ? AND status <> 'SENT'", Integer.class, t.tenantId());
            assertThat(unsent).isZero();
        });
        // each event produced exactly one notification despite at-least-once delivery
        Integer dupes = jdbc.queryForObject("""
                SELECT COUNT(*) FROM (SELECT type FROM notification WHERE tenant_id = ? GROUP BY type HAVING COUNT(*) > 1) d
                """, Integer.class, t.tenantId());
        assertThat(dupes).isZero();
    }

    @Test
    void dashboardAndAssistantReflectRealData() {
        Api api = api();
        Tenant t = api.registerTenant("insight");
        long p = api.createProduct(t.token(), "INS-1", "50.00", 4, 3);
        long order = api.createOrder(t.token(), null, List.of(Api.line(p, 2))).data().path("order").path("id").asLong();
        api.post("/api/orders/" + order + "/payments", t.token(), Map.of("method", "CARD"));

        JsonNode kpis = api.get("/api/dashboard?days=7", t.token()).data().path("kpis");
        assertThat(kpis.path("revenue").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(kpis.path("paidOrders").asLong()).isEqualTo(1);
        assertThat(kpis.path("lowStockCount").asLong()).isEqualTo(1);

        JsonNode reply = api.post("/api/ai/chat", t.token(), Map.of("message", "Which products need restocking?")).data();
        assertThat(reply.path("mode").asText()).isEqualTo("offline");
        assertThat(reply.path("toolsUsed").toString()).contains("getLowStockProducts");
        assertThat(reply.path("reply").asText()).contains("INS-1");
    }

    private static String status(Api api, Tenant t, long order) {
        return api.get("/api/orders/" + order, t.token()).data().path("order").path("status").asText();
    }

    private static void assertStock(Api api, Tenant t, long product, int available, int reserved) {
        JsonNode inv = api.inventory(t.token(), product);
        assertThat(inv.path("available").asInt()).as("available").isEqualTo(available);
        assertThat(inv.path("reserved").asInt()).as("reserved").isEqualTo(reserved);
    }
}
