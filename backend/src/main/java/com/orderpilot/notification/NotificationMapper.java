package com.orderpilot.notification;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NotificationMapper {

    @Data
    class Notification {
        private Long id;
        private Long tenantId;
        private String type;
        private String level;
        private String title;
        private String content;
        private String params;
        private Boolean readFlag;
        private LocalDateTime createdAt;
    }

    @Insert("""
            INSERT INTO notification (tenant_id, type, level, title, content, params)
            VALUES (#{tenantId}, #{type}, #{level}, #{title}, #{content}, #{params})
            """)
    void insert(Notification n);

    @Select("SELECT * FROM notification WHERE tenant_id = #{tenantId} ORDER BY id DESC LIMIT #{limit}")
    List<Notification> recent(@Param("tenantId") long tenantId, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM notification WHERE tenant_id = #{tenantId} AND read_flag = 0")
    long unread(long tenantId);

    @Update("UPDATE notification SET read_flag = 1 WHERE tenant_id = #{tenantId} AND id = #{id}")
    int markRead(@Param("tenantId") long tenantId, @Param("id") long id);

    @Update("UPDATE notification SET read_flag = 1 WHERE tenant_id = #{tenantId} AND read_flag = 0")
    int markAllRead(long tenantId);
}
