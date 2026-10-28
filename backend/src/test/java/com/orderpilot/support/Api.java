package com.orderpilot.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Thin JSON client over the real HTTP API used by integration tests. */
public class Api {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final AtomicInteger SEQ = new AtomicInteger();

    public record Res(int status, JsonNode body) {
        public JsonNode data() {
            return body.path("data");
        }

        public String code() {
            return body.path("code").asText();
        }
    }

    /** A registered tenant with its admin token. */
    public record Tenant(String code, String adminEmail, String token, long tenantId) {
    }

    private final TestRestTemplate rest;
    private final String base;

    Api(TestRestTemplate rest, String base) {
        this.rest = rest;
        this.base = base;
    }

    public Res call(HttpMethod method, String path, String token, Object body, Map<String, String> headers) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            h.setBearerAuth(token);
        }
        if (headers != null) {
            headers.forEach(h::set);
        }
        ResponseEntity<String> r = rest.exchange(base + path, method, new HttpEntity<>(body, h), String.class);
        try {
            JsonNode node = r.getBody() == null ? JSON.createObjectNode() : JSON.readTree(r.getBody());
            return new Res(r.getStatusCode().value(), node);
        } catch (Exception e) {
            throw new IllegalStateException("Non-JSON response " + r.getStatusCode() + ": " + r.getBody(), e);
        }
    }

    public Res get(String path, String token) {
        return call(HttpMethod.GET, path, token, null, null);
    }

    public Res post(String path, String token, Object body) {
        return call(HttpMethod.POST, path, token, body, null);
    }

    public Res post(String path, String token, Object body, String idempotencyKey) {
        return call(HttpMethod.POST, path, token, body, Map.of("Idempotency-Key", idempotencyKey));
    }

    public Tenant registerTenant(String prefix) {
        int n = SEQ.incrementAndGet();
        String code = prefix + "-" + n + "-" + UUID.randomUUID().toString().substring(0, 6);
        String email = "admin@" + code + ".test";
        Res r = post("/api/auth/register", null, Map.of("companyName", prefix + " Inc " + n, "tenantCode", code,
                "adminName", "Admin " + n, "email", email, "password", "Passw0rd!"));
        if (r.status() != 200) {
            throw new IllegalStateException("register failed: " + r.body());
        }
        return new Tenant(code, email, r.data().path("token").asText(), r.data().path("user").path("tenant").path("id").asLong());
    }

    public String login(String email, String password) {
        Res r = post("/api/auth/login", null, Map.of("email", email, "password", password));
        if (r.status() != 200) {
            throw new IllegalStateException("login failed: " + r.body());
        }
        return r.data().path("token").asText();
    }

    public String createUser(String adminToken, String email, String role) {
        Res r = post("/api/users", adminToken, Map.of("email", email, "displayName", role + " user", "role", role,
                "password", "Passw0rd!"));
        if (r.status() != 200) {
            throw new IllegalStateException("create user failed: " + r.body());
        }
        return login(email, "Passw0rd!");
    }

    public long createProduct(String token, String sku, String price, int stock, int threshold) {
        Res r = post("/api/products", token, Map.of("sku", sku, "name", "Product " + sku, "category", "Test",
                "price", price, "cost", "1.00", "lowStockThreshold", threshold, "initialStock", stock));
        if (r.status() != 200) {
            throw new IllegalStateException("create product failed: " + r.body());
        }
        return r.data().path("id").asLong();
    }

    public Res createOrder(String token, String key, List<Map<String, Object>> items) {
        Map<String, Object> body = Map.of("customerName", "Jane Buyer", "customerEmail", "jane@example.com",
                "items", items);
        return key == null ? post("/api/orders", token, body) : post("/api/orders", token, body, key);
    }

    public static Map<String, Object> line(long productId, int qty) {
        return Map.of("productId", productId, "quantity", qty);
    }

    public JsonNode inventory(String token, long productId) {
        return get("/api/inventory/" + productId, token).data();
    }
}
