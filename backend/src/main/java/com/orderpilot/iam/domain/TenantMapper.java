package com.orderpilot.iam.domain;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TenantMapper {

    @Insert("INSERT INTO tenant (code, name, plan, status) VALUES (#{code}, #{name}, #{plan}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Tenant tenant);

    @Select("SELECT * FROM tenant WHERE id = #{id}")
    Tenant findById(long id);

    @Select("SELECT * FROM tenant WHERE code = #{code}")
    Tenant findByCode(String code);

    @Select("SELECT id FROM tenant WHERE status = 'ACTIVE' ORDER BY id")
    java.util.List<Long> findActiveIds();
}
