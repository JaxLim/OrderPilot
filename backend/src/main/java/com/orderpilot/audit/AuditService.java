package com.orderpilot.audit;

import com.orderpilot.common.api.PageQuery;
import com.orderpilot.common.api.PageResult;
import com.orderpilot.common.tenant.TenantContext;
import com.orderpilot.iam.AuthUser;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Append-only activity trail of who did what in a tenant workspace. */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    @Data
    public static class AuditLog {
        private Long id;
        private Long tenantId;
        private Long userId;
        private String userEmail;
        private String action;
        private String targetType;
        private String targetId;
        private String detail;
        private LocalDateTime createdAt;
    }

    @Mapper
    public interface AuditMapper {
        @Insert("""
                INSERT INTO audit_log (tenant_id, user_id, user_email, action, target_type, target_id, detail)
                VALUES (#{tenantId}, #{userId}, #{userEmail}, #{action}, #{targetType}, #{targetId}, #{detail})
                """)
        void insert(AuditLog log);

        @Select("""
                SELECT * FROM audit_log WHERE tenant_id = #{tenantId}
                ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}
                """)
        List<AuditLog> page(@Param("tenantId") long tenantId, @Param("limit") int limit, @Param("offset") int offset);

        @Select("SELECT COUNT(*) FROM audit_log WHERE tenant_id = #{tenantId}")
        long count(long tenantId);
    }

    private final AuditMapper mapper;

    public AuditService(AuditMapper mapper) {
        this.mapper = mapper;
    }

    public void record(String action, String targetType, String targetId, String detail) {
        record(AuthUser.currentOrNull(), action, targetType, targetId, detail);
    }

    public void record(AuthUser actor, String action, String targetType, String targetId, String detail) {
        try {
            AuditLog entry = new AuditLog();
            entry.setTenantId(TenantContext.require());
            entry.setUserId(actor != null ? actor.userId() : null);
            entry.setUserEmail(actor != null ? actor.email() : "system");
            entry.setAction(action);
            entry.setTargetType(targetType);
            entry.setTargetId(targetId);
            entry.setDetail(detail != null && detail.length() > 500 ? detail.substring(0, 500) : detail);
            mapper.insert(entry);
        } catch (RuntimeException e) {
            // Auditing must never break the business operation it describes.
            log.warn("Failed to write audit log {}: {}", action, e.getMessage());
        }
    }

    public PageResult<AuditLog> page(PageQuery q) {
        long tenantId = TenantContext.require();
        return new PageResult<>(mapper.page(tenantId, q.size(), q.offset()), mapper.count(tenantId), q.page(), q.size());
    }
}
