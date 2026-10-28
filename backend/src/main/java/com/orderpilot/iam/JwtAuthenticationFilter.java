package com.orderpilot.iam;

import com.orderpilot.common.tenant.TenantContext;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates Bearer tokens and binds the tenant for the request. The tenant comes only from
 * the signed token, so a client cannot switch tenants by editing a header or a request body.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String TOKEN_ATTR = "orderpilot.token";

    private final JwtService jwtService;
    private final TokenGuard tokenGuard;

    public JwtAuthenticationFilter(JwtService jwtService, TokenGuard tokenGuard) {
        this.jwtService = jwtService;
        this.tokenGuard = tokenGuard;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        try {
            if (header != null && header.startsWith("Bearer ")) {
                authenticate(header.substring(7), request);
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.remove("tenant");
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticate(String token, HttpServletRequest request) {
        try {
            JwtService.ParsedToken parsed = jwtService.parse(token);
            if (tokenGuard.isDenied(parsed.jti())) {
                return;
            }
            AuthUser user = parsed.user();
            var auth = new UsernamePasswordAuthenticationToken(user, null, user.authorities());
            SecurityContextHolder.getContext().setAuthentication(auth);
            TenantContext.set(user.tenantId());
            MDC.put("tenant", String.valueOf(user.tenantId()));
            request.setAttribute(TOKEN_ATTR, parsed);
        } catch (JwtException | IllegalArgumentException e) {
            // invalid token: leave the request unauthenticated; the entry point returns 401
        }
    }
}
