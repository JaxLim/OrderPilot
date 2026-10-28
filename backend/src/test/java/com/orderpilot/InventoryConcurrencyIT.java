package com.orderpilot;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.orderpilot.support.Api;
import com.orderpilot.support.Api.Res;
import com.orderpilot.support.Api.Tenant;
import com.orderpilot.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** Proves the conditional-UPDATE stock deduction under real contention against MySQL. */
class InventoryConcurrencyIT extends IntegrationTest {

    @Test
    void concurrentBuyersNeverOversell() throws Exception {
        Api api = api();
        Tenant t = api.registerTenant("flash");
        int stock = 10;
        int buyers = 40;
        long product = api.createProduct(t.token(), "FLASH-1", "99.00", stock, 0);

        List<Res> results = runConcurrently(buyers, i -> api.createOrder(t.token(), null, List.of(Api.line(product, 1))));

        long ok = results.stream().filter(r -> r.status() == 200).count();
        long soldOut = results.stream().filter(r -> "INSUFFICIENT_STOCK".equals(r.code())).count();
        assertThat(ok).isEqualTo(stock);
        assertThat(soldOut).isEqualTo(buyers - stock);

        JsonNode inv = api.inventory(t.token(), product);
        assertThat(inv.path("available").asInt()).isZero();
        assertThat(inv.path("reserved").asInt()).isEqualTo(stock);
        Integer orders = jdbc.queryForObject("SELECT COUNT(*) FROM sales_order WHERE tenant_id = ?", Integer.class, t.tenantId());
        assertThat(orders).isEqualTo(stock);
    }

    @Test
    void multiLineOrdersInOppositeOrderDoNotDeadlock() throws Exception {
        Api api = api();
        Tenant t = api.registerTenant("deadlock");
        long a = api.createProduct(t.token(), "DL-A", "5.00", 1000, 0);
        long b = api.createProduct(t.token(), "DL-B", "5.00", 1000, 0);

        // Half the orders list A then B, the other half B then A. Without sorted locking this pattern
        // produces InnoDB deadlocks; with it every order succeeds.
        List<Res> results = runConcurrently(30, i -> api.createOrder(t.token(), null, i % 2 == 0
                ? List.of(Api.line(a, 2), Api.line(b, 3))
                : List.of(Api.line(b, 3), Api.line(a, 2))));

        assertThat(results).allSatisfy(r -> assertThat(r.status()).as(r.body().toString()).isEqualTo(200));
        assertThat(api.inventory(t.token(), a).path("available").asInt()).isEqualTo(1000 - 60);
        assertThat(api.inventory(t.token(), b).path("available").asInt()).isEqualTo(1000 - 90);
    }

    @Test
    void failedLineRollsBackTheWholeOrder() {
        Api api = api();
        Tenant t = api.registerTenant("atomic");
        long plenty = api.createProduct(t.token(), "AT-PLENTY", "5.00", 100, 0);
        long scarce = api.createProduct(t.token(), "AT-SCARCE", "5.00", 1, 0);

        Res r = api.createOrder(t.token(), null, List.of(Api.line(plenty, 5), Api.line(scarce, 2)));
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.code()).isEqualTo("INSUFFICIENT_STOCK");
        // the reservation made for the first line was rolled back
        assertThat(api.inventory(t.token(), plenty).path("available").asInt()).isEqualTo(100);
        assertThat(api.inventory(t.token(), plenty).path("reserved").asInt()).isZero();
    }

    @Test
    void manualAdjustmentCannotMakeStockNegative() {
        Api api = api();
        Tenant t = api.registerTenant("adjust");
        long p = api.createProduct(t.token(), "ADJ-1", "5.00", 3, 0);
        assertThat(api.post("/api/inventory/" + p + "/adjustments", t.token(),
                Map.of("type", "ADJUST", "quantity", -4, "note", "count")).code()).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(api.post("/api/inventory/" + p + "/adjustments", t.token(),
                Map.of("type", "INBOUND", "quantity", 7, "note", "PO-1")).data().path("available").asInt()).isEqualTo(10);
    }

    interface Call {
        Res run(int i) throws Exception;
    }

    static List<Res> runConcurrently(int n, Call call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Res>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                int idx = i;
                Callable<Res> task = () -> {
                    start.await();
                    return call.run(idx);
                };
                futures.add(pool.submit(task));
            }
            start.countDown();
            List<Res> out = new ArrayList<>();
            for (Future<Res> f : futures) {
                out.add(f.get());
            }
            return out;
        } finally {
            pool.shutdownNow();
        }
    }
}
