package com.orderpilot.messaging;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** The outbox is infrastructure shared by all tenants, so the relay statements are *Global. */
@Mapper
public interface OutboxMapper {

    @Insert("""
            INSERT INTO outbox_event (event_id, tenant_id, aggregate_type, aggregate_id, event_type, payload)
            VALUES (#{eventId}, #{tenantId}, #{aggregateType}, #{aggregateId}, #{eventType}, #{payload})
            """)
    void insert(OutboxEvent event);

    /** SKIP LOCKED lets several app instances relay in parallel without double-sending. */
    @Select("""
            SELECT * FROM outbox_event
            WHERE status = 'NEW' AND next_attempt_at <= NOW(3)
            ORDER BY id LIMIT #{limit}
            FOR UPDATE SKIP LOCKED
            """)
    List<OutboxEvent> lockPendingGlobal(int limit);

    @Update("UPDATE outbox_event SET status = 'SENT', sent_at = NOW(3), attempts = attempts + 1 WHERE id = #{id}")
    void markSentGlobal(long id);

    @Update("""
            UPDATE outbox_event
            SET attempts = attempts + 1, last_error = #{error}, next_attempt_at = #{nextAttemptAt},
                status = CASE WHEN attempts >= #{maxAttempts} THEN 'FAILED' ELSE 'NEW' END
            WHERE id = #{id}
            """)
    void markRetryGlobal(@Param("id") long id, @Param("error") String error,
                         @Param("nextAttemptAt") LocalDateTime nextAttemptAt, @Param("maxAttempts") int maxAttempts);

    @Update("UPDATE outbox_event SET status = 'NEW', attempts = 0, next_attempt_at = NOW(3) WHERE id = #{id} AND status = 'FAILED'")
    int requeueGlobal(long id);

    @Select("SELECT status, COUNT(*) AS cnt FROM outbox_event WHERE tenant_id = #{tenantId} GROUP BY status")
    List<Map<String, Object>> statusCounts(long tenantId);

    @Select("""
            SELECT * FROM outbox_event WHERE tenant_id = #{tenantId}
            ORDER BY id DESC LIMIT #{limit}
            """)
    List<OutboxEvent> recent(@Param("tenantId") long tenantId, @Param("limit") int limit);

    @Insert("INSERT IGNORE INTO consumed_message (message_id, consumer) VALUES (#{messageId}, #{consumer})")
    int markConsumedGlobal(@Param("messageId") String messageId, @Param("consumer") String consumer);
}
