package com.orderpilot.iam.domain;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper {

    @Insert("""
            INSERT INTO app_user (tenant_id, email, password_hash, display_name, role, status)
            VALUES (#{tenantId}, #{email}, #{passwordHash}, #{displayName}, #{role}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(User user);

    /** Login happens before a tenant is known, so this lookup is deliberately cross-tenant. */
    @Select("SELECT * FROM app_user WHERE email = #{email}")
    User findByEmailGlobal(String email);

    @Select("SELECT * FROM app_user WHERE tenant_id = #{tenantId} AND id = #{id}")
    User findById(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("SELECT * FROM app_user WHERE tenant_id = #{tenantId} ORDER BY id")
    List<User> findAll(long tenantId);

    @Update("UPDATE app_user SET last_login_at = NOW(3) WHERE tenant_id = #{tenantId} AND id = #{id}")
    void touchLogin(@Param("tenantId") long tenantId, @Param("id") long id);

    @Update("""
            UPDATE app_user SET display_name = #{displayName}, role = #{role}, status = #{status}
            WHERE tenant_id = #{tenantId} AND id = #{id}
            """)
    int update(User user);

    @Select("SELECT COUNT(*) FROM app_user WHERE tenant_id = #{tenantId} AND role = 'ADMIN' AND status = 'ACTIVE'")
    int countActiveAdmins(long tenantId);
}
