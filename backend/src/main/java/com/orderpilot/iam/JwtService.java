package com.orderpilot.iam;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    @ConfigurationProperties("orderpilot.jwt")
    public record JwtProperties(String secret, Duration ttl) {
    }

    public record IssuedToken(String token, String jti, Instant expiresAt) {
    }

    public record ParsedToken(AuthUser user, String jti, Instant expiresAt) {
    }

    private final SecretKey key;
    private final Duration ttl;

    public JwtService(JwtProperties props) {
        this.key = signingKey(props.secret());
        this.ttl = props.ttl();
    }

    /** A blank secret gets a random per-process key, so no build ever signs with a key known from the source. */
    private static SecretKey signingKey(String secret) {
        if (secret == null || secret.isBlank()) {
            log.warn("JWT_SECRET is not set: using a random key. Tokens end at restart and do not work across instances.");
            return Jwts.SIG.HS384.key().build();
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("orderpilot.jwt.secret must be at least 32 bytes");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    public IssuedToken issue(AuthUser user) {
        Instant now = Instant.now();
        Instant exp = now.plus(ttl);
        String jti = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .id(jti)
                .subject(String.valueOf(user.userId()))
                .claim("tid", user.tenantId())
                .claim("email", user.email())
                .claim("name", user.displayName())
                .claim("role", user.role().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
        return new IssuedToken(token, jti, exp);
    }

    /** @throws JwtException when the token is malformed, tampered with or expired. */
    public ParsedToken parse(String token) {
        Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        AuthUser user = new AuthUser(
                Long.parseLong(c.getSubject()),
                c.get("tid", Long.class),
                c.get("email", String.class),
                c.get("name", String.class),
                Role.valueOf(c.get("role", String.class)));
        return new ParsedToken(user, c.getId(), c.getExpiration().toInstant());
    }
}
