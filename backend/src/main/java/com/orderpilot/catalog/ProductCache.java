package com.orderpilot.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Cache-aside for catalog data (name, price, status) — the hot read on the order path.
 * <ul>
 *   <li>TTL jitter spreads expirations to avoid an avalanche of simultaneous misses.</li>
 *   <li>Missing ids are cached as a short-lived sentinel to absorb cache-penetration traffic.</li>
 *   <li>Writes delete the key <em>after</em> the DB commit, so a concurrent reader cannot
 *       re-populate the cache with the pre-commit value.</li>
 *   <li>Redis failures degrade to direct DB reads.</li>
 * </ul>
 * Stock levels are intentionally NOT cached; they are read and changed only through atomic SQL.
 */
@Component
public class ProductCache {

    private static final Logger log = LoggerFactory.getLogger(ProductCache.class);
    private static final String NULL_SENTINEL = "__NULL__";
    private static final Duration BASE_TTL = Duration.ofMinutes(30);
    private static final Duration NULL_TTL = Duration.ofMinutes(2);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Counter hits;
    private final Counter misses;

    public ProductCache(StringRedisTemplate redis, ObjectMapper objectMapper, MeterRegistry meters) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.hits = Counter.builder("orderpilot.cache.product").tag("result", "hit").register(meters);
        this.misses = Counter.builder("orderpilot.cache.product").tag("result", "miss").register(meters);
    }

    static String key(long tenantId, long productId) {
        return "op:t:" + tenantId + ":product:" + productId;
    }

    public Optional<Product> get(long tenantId, long productId, Supplier<Product> loader) {
        String key = key(tenantId, productId);
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                hits.increment();
                return NULL_SENTINEL.equals(cached) ? Optional.empty()
                        : Optional.of(objectMapper.readValue(cached, Product.class));
            }
        } catch (DataAccessException | JsonProcessingException e) {
            log.warn("Product cache read failed, falling back to DB: {}", e.getMessage());
            return Optional.ofNullable(loader.get());
        }

        misses.increment();
        Product product = loader.get();
        try {
            if (product == null) {
                redis.opsForValue().set(key, NULL_SENTINEL, NULL_TTL);
            } else {
                Duration ttl = BASE_TTL.plusSeconds(ThreadLocalRandom.current().nextInt(0, 300));
                redis.opsForValue().set(key, objectMapper.writeValueAsString(product), ttl);
            }
        } catch (DataAccessException | JsonProcessingException e) {
            log.warn("Product cache write failed: {}", e.getMessage());
        }
        return Optional.ofNullable(product);
    }

    public void evictAfterCommit(long tenantId, long productId) {
        String key = key(tenantId, productId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict(key);
                }
            });
        } else {
            evict(key);
        }
    }

    private void evict(String key) {
        try {
            redis.delete(key);
        } catch (DataAccessException e) {
            log.warn("Product cache evict failed for {} (TTL will reconcile): {}", key, e.getMessage());
        }
    }
}
