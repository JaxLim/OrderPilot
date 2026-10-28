package com.orderpilot.iam;

import com.orderpilot.audit.AuditService;
import com.orderpilot.common.error.BizException;
import com.orderpilot.common.error.ErrorCode;
import com.orderpilot.common.tenant.TenantContext;
import com.orderpilot.iam.AuthDtos.LoginRequest;
import com.orderpilot.iam.AuthDtos.LoginResponse;
import com.orderpilot.iam.AuthDtos.Me;
import com.orderpilot.iam.AuthDtos.RegisterRequest;
import com.orderpilot.iam.AuthDtos.TenantInfo;
import com.orderpilot.iam.domain.Tenant;
import com.orderpilot.iam.domain.TenantMapper;
import com.orderpilot.iam.domain.User;
import com.orderpilot.iam.domain.UserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {


    private final UserMapper userMapper;
    private final TenantMapper tenantMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenGuard tokenGuard;
    private final AuditService auditService;
    // Defence against user enumeration by timing: unknown e-mails still cost one BCrypt check.
    private final String dummyHash;

    public AuthService(UserMapper userMapper, TenantMapper tenantMapper, PasswordEncoder passwordEncoder,
                       JwtService jwtService, TokenGuard tokenGuard, AuditService auditService) {
        this.userMapper = userMapper;
        this.tenantMapper = tenantMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenGuard = tokenGuard;
        this.auditService = auditService;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    public LoginResponse login(LoginRequest req) {
        String email = req.email().trim().toLowerCase();
        tokenGuard.checkLoginRate(email);

        User user = userMapper.findByEmailGlobal(email);
        boolean ok = passwordEncoder.matches(req.password(), user != null ? user.getPasswordHash() : dummyHash);
        if (user == null || !ok) {
            throw new BizException(ErrorCode.BAD_CREDENTIALS, "Invalid email or password");
        }
        Tenant tenant = tenantMapper.findById(user.getTenantId());
        if (!"ACTIVE".equals(user.getStatus()) || tenant == null || !"ACTIVE".equals(tenant.getStatus())) {
            throw new BizException(ErrorCode.FORBIDDEN, "This account is disabled");
        }
        tokenGuard.resetLoginRate(email);
        userMapper.touchLogin(user.getTenantId(), user.getId());

        AuthUser principal = new AuthUser(user.getId(), user.getTenantId(), user.getEmail(), user.getDisplayName(),
                user.getRole());
        JwtService.IssuedToken issued = jwtService.issue(principal);
        TenantContext.runAs(tenant.getId(), () ->
                auditService.record(principal, "auth.login", "user", String.valueOf(user.getId()), null));
        return new LoginResponse(issued.token(), issued.expiresAt(), toMe(principal, tenant));
    }

    @Transactional
    public LoginResponse register(RegisterRequest req) {
        if (tenantMapper.findByCode(req.tenantCode()) != null) {
            throw new BizException(ErrorCode.CONFLICT, "Workspace code is already taken");
        }
        String email = req.email().trim().toLowerCase();
        if (userMapper.findByEmailGlobal(email) != null) {
            throw new BizException(ErrorCode.CONFLICT, "Email is already registered");
        }
        Tenant tenant = new Tenant();
        tenant.setCode(req.tenantCode());
        tenant.setName(req.companyName());
        tenant.setPlan("STARTER");
        tenant.setStatus("ACTIVE");
        tenantMapper.insert(tenant);

        User admin = new User();
        admin.setTenantId(tenant.getId());
        admin.setEmail(email);
        admin.setDisplayName(req.adminName());
        admin.setRole(Role.ADMIN);
        admin.setStatus("ACTIVE");
        admin.setPasswordHash(passwordEncoder.encode(req.password()));
        userMapper.insert(admin);

        AuthUser principal = new AuthUser(admin.getId(), tenant.getId(), email, admin.getDisplayName(), Role.ADMIN);
        JwtService.IssuedToken issued = jwtService.issue(principal);
        return new LoginResponse(issued.token(), issued.expiresAt(), toMe(principal, tenant));
    }

    public Me me(AuthUser user) {
        return toMe(user, tenantMapper.findById(user.tenantId()));
    }

    public void logout(JwtService.ParsedToken token) {
        if (token != null) {
            tokenGuard.deny(token.jti(), token.expiresAt());
        }
    }

    private Me toMe(AuthUser u, Tenant t) {
        return new Me(u.userId(), u.email(), u.displayName(), u.role(), u.role().permissions(),
                new TenantInfo(t.getId(), t.getCode(), t.getName(), t.getPlan()));
    }
}
