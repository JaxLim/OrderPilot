package com.orderpilot.notification;

import com.orderpilot.common.api.ApiResponse;
import com.orderpilot.common.tenant.TenantContext;
import com.orderpilot.messaging.OutboxEvent;
import com.orderpilot.messaging.OutboxMapper;
import com.orderpilot.notification.NotificationMapper.Notification;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notifications")
@RestController
@RequestMapping("/api")
public class NotificationController {

    public record NotificationFeed(long unread, List<Notification> items) {
    }

    private final NotificationMapper mapper;
    private final OutboxMapper outboxMapper;

    public NotificationController(NotificationMapper mapper, OutboxMapper outboxMapper) {
        this.mapper = mapper;
        this.outboxMapper = outboxMapper;
    }

    @GetMapping("/notifications")
    public ApiResponse<NotificationFeed> feed() {
        long tenantId = TenantContext.require();
        return ApiResponse.ok(new NotificationFeed(mapper.unread(tenantId), mapper.recent(tenantId, 30)));
    }

    @PostMapping("/notifications/{id}/read")
    public ApiResponse<Void> read(@PathVariable long id) {
        mapper.markRead(TenantContext.require(), id);
        return ApiResponse.ok();
    }

    @PostMapping("/notifications/read-all")
    public ApiResponse<Void> readAll() {
        mapper.markAllRead(TenantContext.require());
        return ApiResponse.ok();
    }

    /** Operational view of this tenant's outbox (event delivery pipeline health). */
    @GetMapping("/system/events")
    @PreAuthorize("hasAuthority('audit:read')")
    public ApiResponse<Map<String, Object>> events() {
        long tenantId = TenantContext.require();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Map<String, Object> row : outboxMapper.statusCounts(tenantId)) {
            counts.put(String.valueOf(row.get("status")), ((Number) row.get("cnt")).longValue());
        }
        List<OutboxEvent> recent = outboxMapper.recent(tenantId, 30);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("counts", counts);
        body.put("recent", recent);
        return ApiResponse.ok(body);
    }
}
