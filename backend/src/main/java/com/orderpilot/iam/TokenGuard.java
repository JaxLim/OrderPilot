package com.orderpilot.iam;

import com.orderpilot.common.error.BizException;
import com.orderpilot.common.error.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Redis-backed JWT deny-list (logout) and login brute-force throttling. */
@Component
public class TokenGuard {

    private static final Logger log = LoggerFactory.getLogger(TokenGuard.class);
    private static final int MAX_LOGIN_ATTEMPTS = 10;
    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(5);

    private final StringRedisTemplate redis;

    public TokenGuard(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void deny(String jti, Instant expiresAt) {
        Duration remaining = Duration.between(Instant.now(), expiresAt);
        if (!remaining.isNegative() && !remaining.isZero()) {
            redis.opsForValue().set("op:jwt:deny:" + jti, "1", remaining);
        }
    }

    public boolean isDenied(String jti) {
        try {
            return Boolean.TRUE.equals(redis.hasKey("op:jwt:deny:" + jti));
        } catch (DataAccessException e) {
            log.warn("Redis unavailable while checking token deny-list: {}", e.getMessage());
            return false;
        }
    }

    /** Fixed-window counter: INCR + EXPIRE on first hit. */
    public void checkLoginRate(String email) {
        String key = "op:ratelimit:login:" + email.toLowerCase();
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, LOGIN_WINDOW);
            }
            if (count != null && count > MAX_LOGIN_ATTEMPTS) {
                throw new BizException(ErrorCode.RATE_LIMITED, "Too many login attempts, try again in a few minutes");
            }
        } catch (DataAccessException e) {
            log.warn("Redis unavailable for login rate limiting: {}", e.getMessage());
        }
    }

    public void resetLoginRate(String email) {
        try {
            redis.delete("op:ratelimit:login:" + email.toLowerCase());
        } catch (DataAccessException ignored) {
            // best effort
        }
    }
}
