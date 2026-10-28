package com.orderpilot.audit;

import com.orderpilot.common.api.ApiResponse;
import com.orderpilot.common.api.PageQuery;
import com.orderpilot.common.api.PageResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Audit")
@RestController
@RequestMapping("/api/audit-logs")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('audit:read')")
    public ApiResponse<PageResult<AuditService.AuditLog>> list(@RequestParam(required = false) Integer page,
                                                              @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(auditService.page(PageQuery.of(page, size)));
    }
}
