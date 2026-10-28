package com.orderpilot.inventory;

import com.orderpilot.inventory.InventoryDtos.InventoryRow;
import com.orderpilot.inventory.InventoryDtos.Movement;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * All stock mutations are single conditional UPDATEs. InnoDB takes a row lock for the UPDATE and
 * re-evaluates the WHERE clause against the latest committed row, so "check and decrement" is
 * atomic: two concurrent buyers of the last unit cannot both succeed, and stock never goes negative.
 * No SELECT ... FOR UPDATE, no distributed lock, no lost updates.
 */
@Mapper
public interface InventoryMapper {

    @Insert("INSERT INTO inventory (tenant_id, product_id, available, reserved) VALUES (#{tenantId}, #{productId}, #{available}, 0)")
    void insert(@Param("tenantId") long tenantId, @Param("productId") long productId, @Param("available") int available);

    /** available → reserved. Returns 0 when stock is insufficient. */
    @Update("""
            UPDATE inventory SET available = available - #{qty}, reserved = reserved + #{qty}, version = version + 1
            WHERE tenant_id = #{tenantId} AND product_id = #{productId} AND available >= #{qty}
            """)
    int reserve(@Param("tenantId") long tenantId, @Param("productId") long productId, @Param("qty") int qty);

    /** reserved → available (order cancelled or timed out). */
    @Update("""
            UPDATE inventory SET available = available + #{qty}, reserved = reserved - #{qty}, version = version + 1
            WHERE tenant_id = #{tenantId} AND product_id = #{productId} AND reserved >= #{qty}
            """)
    int release(@Param("tenantId") long tenantId, @Param("productId") long productId, @Param("qty") int qty);

    /** reserved → gone (order paid; goods are committed to the customer). */
    @Update("""
            UPDATE inventory SET reserved = reserved - #{qty}, version = version + 1
            WHERE tenant_id = #{tenantId} AND product_id = #{productId} AND reserved >= #{qty}
            """)
    int commitReserved(@Param("tenantId") long tenantId, @Param("productId") long productId, @Param("qty") int qty);

    /** Signed change to available (inbound receipt, refund restock, manual adjustment). */
    @Update("""
            UPDATE inventory SET available = available + #{delta}, version = version + 1
            WHERE tenant_id = #{tenantId} AND product_id = #{productId} AND available + #{delta} >= 0
            """)
    int adjustAvailable(@Param("tenantId") long tenantId, @Param("productId") long productId, @Param("delta") int delta);

    String ROW_SELECT = """
            SELECT i.product_id, p.sku, p.name AS product_name, p.category, p.status AS product_status,
                   i.available, i.reserved, p.low_stock_threshold, i.version, i.updated_at
            FROM inventory i JOIN product p ON p.tenant_id = i.tenant_id AND p.id = i.product_id
            """;

    @Select(ROW_SELECT + " WHERE i.tenant_id = #{tenantId} AND i.product_id = #{productId}")
    InventoryRow findRow(@Param("tenantId") long tenantId, @Param("productId") long productId);

    String LIST_WHERE = """
            WHERE i.tenant_id = #{tenantId}
            <if test="keyword != null and keyword != ''">
              AND (p.name LIKE CONCAT('%', #{keyword}, '%') OR p.sku LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="lowOnly"> AND i.available &lt;= p.low_stock_threshold AND p.status = 'ACTIVE' </if>
            """;

    @Select("<script>" + ROW_SELECT + LIST_WHERE
            + " ORDER BY (i.available - p.low_stock_threshold) ASC, i.product_id LIMIT #{limit} OFFSET #{offset}</script>")
    List<InventoryRow> list(@Param("tenantId") long tenantId, @Param("keyword") String keyword,
                            @Param("lowOnly") boolean lowOnly, @Param("limit") int limit, @Param("offset") int offset);

    @Select("<script>SELECT COUNT(*) FROM inventory i JOIN product p ON p.tenant_id = i.tenant_id AND p.id = i.product_id "
            + LIST_WHERE + "</script>")
    long count(@Param("tenantId") long tenantId, @Param("keyword") String keyword, @Param("lowOnly") boolean lowOnly);

    @Insert("""
            INSERT INTO inventory_movement (tenant_id, product_id, type, quantity, ref_type, ref_id, note, operator_id)
            VALUES (#{tenantId}, #{productId}, #{type}, #{quantity}, #{refType}, #{refId}, #{note}, #{operatorId})
            """)
    void insertMovement(Movement movement);

    @Select("""
            <script>
            SELECT m.*, p.sku, p.name AS product_name FROM inventory_movement m
            JOIN product p ON p.tenant_id = m.tenant_id AND p.id = m.product_id
            WHERE m.tenant_id = #{tenantId}
            <if test="productId != null"> AND m.product_id = #{productId} </if>
            ORDER BY m.id DESC LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Movement> movements(@Param("tenantId") long tenantId, @Param("productId") Long productId,
                             @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            <script>
            SELECT COUNT(*) FROM inventory_movement WHERE tenant_id = #{tenantId}
            <if test="productId != null"> AND product_id = #{productId} </if>
            </script>
            """)
    long countMovements(@Param("tenantId") long tenantId, @Param("productId") Long productId);
}
