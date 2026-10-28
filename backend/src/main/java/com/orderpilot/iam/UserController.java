package com.orderpilot.iam;

import com.orderpilot.audit.AuditService;
import com.orderpilot.common.api.ApiResponse;
import com.orderpilot.common.error.BizException;
import com.orderpilot.common.error.ErrorCode;
import com.orderpilot.common.tenant.TenantContext;
import com.orderpilot.iam.AuthDtos.CreateUserRequest;
import com.orderpilot.iam.AuthDtos.UpdateUserRequest;
import com.orderpilot.iam.AuthDtos.UserView;
import com.orderpilot.iam.domain.User;
import com.orderpilot.iam.domain.UserMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Users")
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasAuthority('user:manage')")
public class UserController {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserController(UserMapper userMapper, PasswordEncoder passwordEncoder, AuditService auditService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @GetMapping
    public ApiResponse<List<UserView>> list() {
        return ApiResponse.ok(userMapper.findAll(TenantContext.require()).stream().map(UserView::of).toList());
    }

    @PostMapping
    public ApiResponse<UserView> create(@Valid @RequestBody CreateUserRequest req) {
        String email = req.email().trim().toLowerCase();
        if (userMapper.findByEmailGlobal(email) != null) {
            throw new BizException(ErrorCode.CONFLICT, "Email is already registered");
        }
        User u = new User();
        u.setTenantId(TenantContext.require());
        u.setEmail(email);
        u.setDisplayName(req.displayName());
        u.setRole(req.role());
        u.setStatus("ACTIVE");
        u.setPasswordHash(passwordEncoder.encode(req.password()));
        userMapper.insert(u);
        auditService.record("user.create", "user", String.valueOf(u.getId()), email + " as " + req.role());
        return ApiResponse.ok(UserView.of(userMapper.findById(u.getTenantId(), u.getId())));
    }

    @PutMapping("/{id}")
    @Transactional
    public ApiResponse<UserView> update(@PathVariable long id, @Valid @RequestBody UpdateUserRequest req) {
        long tenantId = TenantContext.require();
        User u = userMapper.findById(tenantId, id);
        if (u == null) {
            throw BizException.notFound("User", id);
        }
        boolean demotingAdmin = u.getRole() == Role.ADMIN
                && (req.role() != Role.ADMIN || !"ACTIVE".equals(req.status()));
        if (demotingAdmin && userMapper.countActiveAdmins(tenantId) <= 1) {
            throw BizException.invalidState("A workspace must keep at least one active admin");
        }
        u.setDisplayName(req.displayName());
        u.setRole(req.role());
        u.setStatus(req.status());
        userMapper.update(u);
        auditService.record("user.update", "user", String.valueOf(id), req.role() + "/" + req.status());
        return ApiResponse.ok(UserView.of(userMapper.findById(tenantId, id)));
    }
}
