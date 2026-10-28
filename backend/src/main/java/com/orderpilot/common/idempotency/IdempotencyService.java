package com.orderpilot.common.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderpilot.common.error.BizException;
import com.orderpilot.common.error.ErrorCode;
import com.orderpilot.common.tenant.TenantContext;
import java.time.Duration;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Idempotency-Key handling for unsafe POSTs (create order, pay).
 *
 * <ol>
 *   <li>{@code SET key PROCESSING NX EX ttl} claims the key; a concurrent duplicate gets 409.</li>
 *   <li>On success the serialized response replaces the marker, so retries replay the same result.</li>
 *   <li>On failure the marker is removed so the client may retry with the same key.</li>
 * </ol>
 * Redis is the fast path; a unique (tenant_id, idempotency_key) index in MySQL is the final
 * guarantee, so the system stays correct if Redis is unavailable.
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final String PROCESSING = "__PROCESSING__";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public IdempotencyService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public <T> T execute(String scope, String idempotencyKey, Class<T> resultType, Supplier<T> action) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return action.get();
        }
        if (idempotencyKey.length() > 64) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "Idempotency-Key must be at most 64 characters");
        }
        String redisKey = "op:idem:" + TenantContext.require() + ":" + scope + ":" + idempotencyKey;

        Boolean claimed;
        try {
            claimed = redis.opsForValue().setIfAbsent(redisKey, PROCESSING, TTL);
        } catch (DataAccessException e) {
            log.warn("Redis unavailable for idempotency check, relying on DB unique key: {}", e.getMessage());
            return action.get();
        }

        if (!Boolean.TRUE.equals(claimed)) {
            String stored = redis.opsForValue().get(redisKey);
            if (stored == null || PROCESSING.equals(stored)) {
                throw new BizException(ErrorCode.REQUEST_IN_PROGRESS,
                        "A request with this Idempotency-Key is already being processed");
            }
            return deserialize(stored, resultType);
        }

        try {
            T result = action.get();
            redis.opsForValue().set(redisKey, serialize(result), TTL);
            return result;
        } catch (RuntimeException e) {
            safeDelete(redisKey);
            throw e;
        }
    }

    private void safeDelete(String key) {
        try {
            redis.delete(key);
        } catch (DataAccessException e) {
            log.warn("Failed to release idempotency key {}", key);
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private <T> T deserialize(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
