package com.orderpilot.catalog;

import com.orderpilot.catalog.ProductDtos.ProductView;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProductMapper {

    @Insert("""
            INSERT INTO product (tenant_id, sku, name, category, description, price, cost, low_stock_threshold, status)
            VALUES (#{tenantId}, #{sku}, #{name}, #{category}, #{description}, #{price}, #{cost},
                    #{lowStockThreshold}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Product product);

    @Update("""
            UPDATE product SET name = #{name}, category = #{category}, description = #{description},
                   price = #{price}, cost = #{cost}, low_stock_threshold = #{lowStockThreshold}
            WHERE tenant_id = #{tenantId} AND id = #{id}
            """)
    int update(Product product);

    @Update("UPDATE product SET status = #{status} WHERE tenant_id = #{tenantId} AND id = #{id}")
    int updateStatus(@Param("tenantId") long tenantId, @Param("id") long id, @Param("status") String status);

    @Select("SELECT * FROM product WHERE tenant_id = #{tenantId} AND id = #{id}")
    Product findById(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("SELECT * FROM product WHERE tenant_id = #{tenantId} AND sku = #{sku}")
    Product findBySku(@Param("tenantId") long tenantId, @Param("sku") String sku);

    String SEARCH_WHERE = """
            WHERE p.tenant_id = #{tenantId}
            <if test="keyword != null and keyword != ''">
              AND (p.name LIKE CONCAT('%', #{keyword}, '%') OR p.sku LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="category != null and category != ''"> AND p.category = #{category} </if>
            <if test="status != null and status != ''"> AND p.status = #{status} </if>
            """;

    @Select("<script>"
            + "SELECT p.*, COALESCE(i.available, 0) AS available, COALESCE(i.reserved, 0) AS reserved "
            + "FROM product p LEFT JOIN inventory i ON i.tenant_id = p.tenant_id AND i.product_id = p.id "
            + SEARCH_WHERE
            + "ORDER BY p.id DESC LIMIT #{limit} OFFSET #{offset}"
            + "</script>")
    List<ProductView> search(@Param("tenantId") long tenantId, @Param("keyword") String keyword,
                             @Param("category") String category, @Param("status") String status,
                             @Param("limit") int limit, @Param("offset") int offset);

    @Select("<script>SELECT COUNT(*) FROM product p " + SEARCH_WHERE + "</script>")
    long count(@Param("tenantId") long tenantId, @Param("keyword") String keyword,
               @Param("category") String category, @Param("status") String status);

    @Select("""
            SELECT p.*, COALESCE(i.available, 0) AS available, COALESCE(i.reserved, 0) AS reserved
            FROM product p LEFT JOIN inventory i ON i.tenant_id = p.tenant_id AND i.product_id = p.id
            WHERE p.tenant_id = #{tenantId} AND p.id = #{id}
            """)
    ProductView findView(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("SELECT DISTINCT category FROM product WHERE tenant_id = #{tenantId} ORDER BY category")
    List<String> categories(long tenantId);
}
