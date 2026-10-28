package com.orderpilot.iam;

import com.orderpilot.common.error.BizException;
import com.orderpilot.common.error.ErrorCode;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** Authenticated principal reconstructed from the JWT on every request. */
public record AuthUser(long userId, long tenantId, String email, String displayName, Role role) {

    public Collection<GrantedAuthority> authorities() {
        List<GrantedAuthority> list = new java.util.ArrayList<>();
        list.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        role.permissions().forEach(p -> list.add(new SimpleGrantedAuthority(p)));
        return list;
    }

    public static AuthUser current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser user) {
            return user;
        }
        throw new BizException(ErrorCode.UNAUTHORIZED, "Not authenticated");
    }

    /** Current user if any (null for system work such as MQ consumers and schedulers). */
    public static AuthUser currentOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AuthUser user ? user : null;
    }
}
