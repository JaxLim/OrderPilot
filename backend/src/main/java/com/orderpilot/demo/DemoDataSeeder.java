package com.orderpilot.demo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Seeds two realistic tenants on an empty database: 45 days of order history, payments, stock
 * levels (some deliberately low), notifications and audit entries. Runs once — it is skipped when
 * the {@code acme} tenant already exists. Disable with {@code DEMO_SEED=false}.
 */
@Component
@ConditionalOnProperty(name = "orderpilot.demo.seed", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    public static final String DEMO_PASSWORD = "Passw0rd!";

    private record P(String sku, String name, String category, String price, String cost, int threshold, int stock,
                     String description) {
    }

    private record Item(long productId, String sku, String name, BigDecimal price, int qty) {
    }

    private static final List<P> ACME = List.of(
            new P("TNT-ALP2", "Alpine 2P Ultralight Tent", "Tents", "329.00", "168.00", 8, 42, "2-person, 1.4 kg, double-wall freestanding tent."),
            new P("TNT-BASE4", "Basecamp 4P Family Tent", "Tents", "449.00", "230.00", 5, 18, "Spacious 4-person tent with vestibule and blackout fly."),
            new P("TNT-BIVY", "Storm Bivy Shelter", "Tents", "189.00", "84.00", 6, 4, "Waterproof single-person emergency bivy."),
            new P("BPK-TRL45", "Trailhead 45L Backpack", "Backpacks", "179.00", "79.00", 10, 64, "Ventilated back panel, hip-belt pockets, rain cover."),
            new P("BPK-SUM28", "Summit 28L Daypack", "Backpacks", "119.00", "48.00", 12, 96, "Lightweight daypack with hydration sleeve."),
            new P("BPK-EXP70", "Expedition 70L Pack", "Backpacks", "289.00", "131.00", 5, 21, "Load-lifter frame for multi-day treks."),
            new P("APP-SHL-M", "Stormline Shell Jacket (M)", "Apparel", "249.00", "102.00", 10, 37, "3-layer waterproof breathable shell."),
            new P("APP-SHL-L", "Stormline Shell Jacket (L)", "Apparel", "249.00", "102.00", 10, 7, "3-layer waterproof breathable shell."),
            new P("APP-FLC", "Ridge Fleece Pullover", "Apparel", "89.00", "31.00", 15, 120, "Recycled 200-weight fleece."),
            new P("APP-DWN", "Cirrus Down Vest", "Apparel", "159.00", "63.00", 8, 45, "800-fill responsibly sourced down."),
            new P("APP-BAS", "Merino Base Layer Top", "Apparel", "79.00", "29.00", 20, 150, "150 g/m² merino wool."),
            new P("FTW-HKR", "Granite Mid Hiking Boot", "Footwear", "199.00", "86.00", 10, 52, "Waterproof membrane, Vibram outsole."),
            new P("FTW-TRL", "Switchback Trail Runner", "Footwear", "149.00", "58.00", 12, 9, "Low-drop trail runner with rock plate."),
            new P("FTW-SCK", "Trek Wool Socks (3-pack)", "Footwear", "29.00", "8.50", 30, 260, "Cushioned merino blend."),
            new P("CMP-STV", "Flashpoint Backpacking Stove", "Camp Kitchen", "69.00", "24.00", 10, 75, "Canister stove, 1 L boil in 3 min."),
            new P("CMP-POT", "Titanium Cook Pot 900ml", "Camp Kitchen", "59.00", "19.00", 10, 88, "Ultralight titanium pot with lid."),
            new P("CMP-FLT", "ClearFlow Water Filter", "Camp Kitchen", "45.00", "14.00", 15, 12, "0.1 micron hollow-fiber filter."),
            new P("CMP-MUG", "Insulated Camp Mug", "Camp Kitchen", "24.00", "7.00", 20, 140, "Double-wall stainless steel, 350 ml."),
            new P("ACC-HDL", "Lumen 400 Headlamp", "Accessories", "49.00", "16.00", 15, 110, "400 lumens, USB-C rechargeable."),
            new P("ACC-PLS", "Carbon Trekking Poles", "Accessories", "129.00", "52.00", 8, 33, "Pair, flick-lock, 220 g each."),
            new P("ACC-MAT", "AirLite Sleeping Pad", "Accessories", "139.00", "55.00", 8, 40, "R-value 4.2 insulated air pad."),
            new P("ACC-BAG", "Nimbus 20°F Sleeping Bag", "Accessories", "259.00", "110.00", 6, 26, "Synthetic fill, compressible."),
            new P("ACC-KNF", "Trail Multi-Tool", "Accessories", "39.00", "12.00", 15, 2, "12 functions, 120 g."),
            new P("ACC-BTL", "Stainless Bottle 1L", "Accessories", "32.00", "9.00", 25, 180, "Vacuum-insulated, 24 h cold."));

    private static final List<P> NOVA = List.of(
            new P("AUD-ANC1", "Pulse ANC Headphones", "Audio", "299.00", "140.00", 8, 34, "Hybrid active noise cancelling, 40 h battery."),
            new P("AUD-BUD", "Pulse Buds Pro", "Audio", "179.00", "70.00", 12, 58, "True wireless earbuds with spatial audio."),
            new P("AUD-SPK", "Boom Mini Speaker", "Audio", "89.00", "33.00", 10, 6, "IP67 portable speaker."),
            new P("SMH-HUB", "Nova Home Hub", "Smart Home", "129.00", "51.00", 6, 22, "Matter & Thread smart home controller."),
            new P("SMH-PLG", "Smart Plug (2-pack)", "Smart Home", "35.00", "11.00", 20, 140, "Energy monitoring smart plugs."),
            new P("SMH-CAM", "Indoor Cam 2K", "Smart Home", "79.00", "28.00", 10, 47, "2K camera with local storage."),
            new P("ACC-CHG", "GaN 65W Charger", "Accessories", "49.00", "17.00", 20, 95, "3-port USB-C fast charger."),
            new P("ACC-CBL", "Braided USB-C Cable 2m", "Accessories", "19.00", "4.00", 40, 300, "240 W rated cable."),
            new P("ACC-PWR", "PowerCore 20K Bank", "Accessories", "69.00", "26.00", 12, 8, "20,000 mAh power bank."),
            new P("ACC-STD", "Aluminium Laptop Stand", "Accessories", "59.00", "18.00", 10, 41, "Adjustable ergonomic stand."));

    private static final String[] FIRST = {"Olivia", "Liam", "Emma", "Noah", "Ava", "Ethan", "Sophia", "Mason", "Mia",
            "Lucas", "Chloe", "James", "Grace", "Daniel", "Zoe", "Henry", "Lily", "Owen", "Hannah", "Leo", "Wei",
            "Aarav", "Yuki", "Mateo", "Fatima", "Kenji", "Priya", "Diego", "Amara", "Jonas"};
    private static final String[] LAST = {"Smith", "Chen", "Garcia", "Johnson", "Tanaka", "Müller", "Brown", "Patel",
            "Kim", "Nguyen", "Lopez", "Wilson", "Novak", "Rossi", "Silva", "Okafor", "Larsen", "Cohen", "Khan", "Martin"};
    private static final String[] CITIES = {"Seattle, WA", "Denver, CO", "Austin, TX", "Portland, OR", "Boston, MA",
            "Chicago, IL", "Salt Lake City, UT", "San Diego, CA", "Minneapolis, MN", "Boulder, CO", "Brooklyn, NY"};
    private static final String[] STREETS = {"Maple Ave", "Pine St", "Cedar Rd", "Lakeview Dr", "Summit Blvd",
            "River Rd", "Oak Ln", "Granite Way", "Aspen Ct"};
    private static final String[] METHODS = {"CARD", "CARD", "CARD", "ALIPAY", "WECHAT_PAY", "BANK_TRANSFER"};

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate tx;
    private final Random rnd = new Random(42);
    private int seq;

    public DemoDataSeeder(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM tenant WHERE code = 'acme'", Integer.class);
        if (exists != null && exists > 0) {
            return;
        }
        long start = System.currentTimeMillis();
        tx.executeWithoutResult(s -> {
            seedTenant("acme", "Acme Outdoor Co.", "GROWTH", "acme.test", ACME, 45, 9, 15);
            seedTenant("nova", "Nova Electronics", "STARTER", "nova.test", NOVA, 45, 2, 5);
        });
        log.info("Demo data seeded in {} ms (login: admin@acme.test / {})", System.currentTimeMillis() - start,
                DEMO_PASSWORD);
    }

    private void seedTenant(String code, String name, String plan, String domain, List<P> catalog, int days,
                            int minDaily, int maxDaily) {
        long tenantId = insertKey("INSERT INTO tenant (code, name, plan, status, created_at) VALUES (?, ?, ?, 'ACTIVE', ?)",
                code, name, plan, ts(LocalDateTime.now().minusDays(days + 30L)));

        String hash = passwordEncoder.encode(DEMO_PASSWORD);
        String[][] users = {{"admin", "ADMIN", "Alex Morgan"}, {"manager", "MANAGER", "Jordan Lee"},
                {"staff", "STAFF", "Sam Rivera"}, {"viewer", "VIEWER", "Casey Kim"}};
        long adminId = 0;
        for (String[] u : users) {
            long id = insertKey("""
                    INSERT INTO app_user (tenant_id, email, password_hash, display_name, role, status, created_at)
                    VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?)""",
                    tenantId, u[0] + "@" + domain, hash, u[2], u[1], ts(LocalDateTime.now().minusDays(days + 20L)));
            if ("ADMIN".equals(u[1])) adminId = id;
        }

        List<Long> productIds = new ArrayList<>();
        Map<Long, P> byId = new HashMap<>();
        LocalDateTime catalogDate = LocalDateTime.now().minusDays(days + 5L);
        for (P p : catalog) {
            long id = insertKey("""
                    INSERT INTO product (tenant_id, sku, name, category, description, price, cost, low_stock_threshold,
                                         status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?)""",
                    tenantId, p.sku(), p.name(), p.category(), p.description(), new BigDecimal(p.price()),
                    new BigDecimal(p.cost()), p.threshold(), ts(catalogDate));
            productIds.add(id);
            byId.put(id, p);
        }

        Map<Long, Integer> reserved = new HashMap<>();
        List<Object[]> paymentRows = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int d = days - 1; d >= 0; d--) {
            LocalDate day = today.minusDays(d);
            // gentle growth trend + weekend bump
            double growth = 1.0 + (days - d) / (double) days * 0.45;
            int weekend = day.getDayOfWeek().getValue() >= 6 ? 3 : 0;
            int count = (int) Math.round((minDaily + rnd.nextInt(maxDaily - minDaily + 1)) * growth) + weekend;
            if (d == 0) count = Math.max(4, count / 2); // today is only partially over
            for (int n = 0; n < count; n++) {
                LocalDateTime created = day.atTime(7 + rnd.nextInt(15), rnd.nextInt(60), rnd.nextInt(60));
                if (created.isAfter(LocalDateTime.now())) created = LocalDateTime.now().minusMinutes(1 + rnd.nextInt(90));
                seedOrder(tenantId, adminId, productIds, byId, created, d, reserved, paymentRows);
            }
        }
        if (!paymentRows.isEmpty()) {
            jdbc.batchUpdate("""
                    INSERT INTO payment (tenant_id, payment_no, order_id, amount, method, status, provider_txn_id,
                                         failure_reason, created_at, paid_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    paymentRows);
        }

        List<Object[]> inv = new ArrayList<>();
        List<Object[]> moves = new ArrayList<>();
        for (Long pid : productIds) {
            P p = byId.get(pid);
            int res = reserved.getOrDefault(pid, 0);
            inv.add(new Object[]{tenantId, pid, p.stock(), res});
            moves.add(new Object[]{tenantId, pid, "INBOUND", p.stock() + res + 40 + rnd.nextInt(120), "MANUAL", null,
                    "Opening stock", adminId, ts(catalogDate)});
            if (rnd.nextInt(4) == 0) {
                moves.add(new Object[]{tenantId, pid, "ADJUST", -(1 + rnd.nextInt(3)), "MANUAL", null,
                        "Cycle count correction", adminId, ts(LocalDateTime.now().minusDays(3 + rnd.nextInt(20)))});
            }
        }
        jdbc.batchUpdate("INSERT INTO inventory (tenant_id, product_id, available, reserved) VALUES (?, ?, ?, ?)", inv);
        jdbc.batchUpdate("""
                INSERT INTO inventory_movement (tenant_id, product_id, type, quantity, ref_type, ref_id, note, operator_id,
                                                created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""", moves);

        for (Long pid : productIds) {
            P p = byId.get(pid);
            if (p.stock() <= p.threshold()) {
                jdbc.update("""
                        INSERT INTO notification (tenant_id, type, level, title, content, created_at)
                        VALUES (?, 'inventory.low_stock', 'WARNING', ?, ?, ?)""",
                        tenantId, "Low stock: " + p.name(), p.sku() + " has " + p.stock() + " units left (threshold "
                                + p.threshold() + ")", ts(LocalDateTime.now().minusHours(1 + rnd.nextInt(30))));
            }
        }
        jdbc.update("""
                INSERT INTO audit_log (tenant_id, user_id, user_email, action, target_type, target_id, detail, created_at)
                VALUES (?, ?, ?, 'tenant.seed', 'tenant', ?, 'Demo workspace created', ?)""",
                tenantId, adminId, "admin@" + domain, String.valueOf(tenantId), ts(catalogDate));
    }

    private void seedOrder(long tenantId, long userId, List<Long> productIds, Map<Long, P> byId, LocalDateTime created,
                           int daysAgo, Map<Long, Integer> reserved, List<Object[]> paymentRows) {
        int lines = 1 + (rnd.nextInt(10) < 6 ? 0 : rnd.nextInt(3));
        List<Item> items = new ArrayList<>();
        List<Long> pool = new ArrayList<>(productIds);
        for (int i = 0; i < lines; i++) {
            // skew demand towards the first products of each category (realistic best-sellers)
            int idx = Math.min(pool.size() - 1, (int) Math.floor(Math.pow(rnd.nextDouble(), 1.6) * pool.size()));
            long pid = pool.remove(idx);
            P p = byId.get(pid);
            int qty = 1 + (rnd.nextInt(10) < 7 ? 0 : rnd.nextInt(3));
            items.add(new Item(pid, p.sku(), p.name(), new BigDecimal(p.price()), qty));
        }
        BigDecimal total = items.stream().map(i -> i.price().multiply(BigDecimal.valueOf(i.qty())))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        int units = items.stream().mapToInt(Item::qty).sum();

        String status = pickStatus(daysAgo);
        LocalDateTime paidAt = null, shippedAt = null, completedAt = null, cancelledAt = null;
        String cancelReason = null;
        LocalDateTime expiresAt = created.plusMinutes(15);
        switch (status) {
            case "PENDING_PAYMENT" -> expiresAt = LocalDateTime.now().plusHours(6); // seeded orders get a longer window
            case "CANCELLED" -> {
                cancelledAt = created.plusMinutes(15);
                cancelReason = rnd.nextBoolean() ? "Payment timeout" : "Customer changed their mind";
            }
            case "REFUNDED" -> {
                paidAt = created.plusMinutes(2 + rnd.nextInt(8));
                cancelReason = "Customer return";
            }
            default -> {
                paidAt = created.plusMinutes(1 + rnd.nextInt(10));
                if (!"PAID".equals(status)) shippedAt = paidAt.plusHours(4 + rnd.nextInt(30));
                if ("COMPLETED".equals(status)) completedAt = shippedAt.plusDays(2 + rnd.nextInt(3));
            }
        }
        String first = FIRST[rnd.nextInt(FIRST.length)];
        String last = LAST[rnd.nextInt(LAST.length)];
        String orderNo = "SO" + created.format(java.time.format.DateTimeFormatter.ofPattern("yyMMddHHmmss"))
                + String.format("%06d", ++seq);
        long orderId = insertKey("""
                INSERT INTO sales_order (tenant_id, order_no, customer_name, customer_email, shipping_address, status,
                    total_amount, item_count, created_by, expires_at, paid_at, shipped_at, completed_at, cancelled_at,
                    cancel_reason, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                tenantId, orderNo, first + " " + last,
                (first + "." + last).toLowerCase().replace("ü", "u") + "@example.com",
                (100 + rnd.nextInt(8900)) + " " + STREETS[rnd.nextInt(STREETS.length)] + ", " + CITIES[rnd.nextInt(CITIES.length)],
                status, total, units, userId, ts(expiresAt), ts(paidAt), ts(shippedAt), ts(completedAt), ts(cancelledAt),
                cancelReason, ts(created), ts(created));

        List<Object[]> rows = new ArrayList<>();
        for (Item i : items) {
            rows.add(new Object[]{tenantId, orderId, i.productId(), i.sku(), i.name(), i.price(), i.qty(),
                    i.price().multiply(BigDecimal.valueOf(i.qty()))});
            if ("PENDING_PAYMENT".equals(status)) {
                reserved.merge(i.productId(), i.qty(), Integer::sum);
            }
        }
        jdbc.batchUpdate("""
                INSERT INTO order_item (tenant_id, order_id, product_id, sku, product_name, unit_price, quantity, line_total)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)""", rows);

        String method = METHODS[rnd.nextInt(METHODS.length)];
        if (paidAt != null) {
            if (rnd.nextInt(14) == 0) { // a declined attempt before the successful one
                paymentRows.add(new Object[]{tenantId, "PY" + orderNo.substring(2) + "F", orderId, total, "CARD",
                        "FAILED", null, "Card declined by issuer (simulated)", ts(paidAt.minusMinutes(1)), null});
            }
            paymentRows.add(new Object[]{tenantId, "PY" + orderNo.substring(2), orderId, total, method,
                    "REFUNDED".equals(status) ? "REFUNDED" : "SUCCEEDED",
                    "SIM-" + Long.toHexString(rnd.nextLong()).toUpperCase().substring(0, 10),
                    "REFUNDED".equals(status) ? "Customer return" : null, ts(paidAt), ts(paidAt)});
        }
    }

    private String pickStatus(int daysAgo) {
        int r = rnd.nextInt(100);
        if (daysAgo == 0) {
            if (r < 30) return "PENDING_PAYMENT";
            if (r < 85) return "PAID";
            return "CANCELLED";
        }
        if (daysAgo <= 2) {
            if (r < 35) return "PAID";
            if (r < 80) return "SHIPPED";
            if (r < 90) return "COMPLETED";
            return "CANCELLED";
        }
        if (r < 78) return "COMPLETED";
        if (r < 82) return "SHIPPED";
        if (r < 94) return "CANCELLED";
        return "REFUNDED";
    }

    private long insertKey(String sql, Object... args) {
        GeneratedKeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, kh);
        return kh.getKey().longValue();
    }

    private static Timestamp ts(LocalDateTime t) {
        return t == null ? null : Timestamp.valueOf(t);
    }
}
