package com.orderpilot.order;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OrderMapper {

    @Insert("""
            INSERT INTO sales_order (tenant_id, order_no, customer_name, customer_email, shipping_address, status,
                                     total_amount, item_count, idempotency_key, remark, created_by, expires_at)
            VALUES (#{tenantId}, #{orderNo}, #{customerName}, #{customerEmail}, #{shippingAddress}, #{status},
                    #{totalAmount}, #{itemCount}, #{idempotencyKey}, #{remark}, #{createdBy}, #{expiresAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(SalesOrder order);

    @Insert("""
            <script>
            INSERT INTO order_item (tenant_id, order_id, product_id, sku, product_name, unit_price, quantity, line_total)
            VALUES
            <foreach collection="items" item="i" separator=",">
              (#{i.tenantId}, #{i.orderId}, #{i.productId}, #{i.sku}, #{i.productName}, #{i.unitPrice},
               #{i.quantity}, #{i.lineTotal})
            </foreach>
            </script>
            """)
    void insertItems(@Param("items") List<OrderItem> items);

    @Select("SELECT * FROM sales_order WHERE tenant_id = #{tenantId} AND id = #{id}")
    SalesOrder findById(@Param("tenantId") long tenantId, @Param("id") long id);

    /** Pessimistic lock used by the payment flow to serialise concurrent pay attempts on one order. */
    @Select("SELECT * FROM sales_order WHERE tenant_id = #{tenantId} AND id = #{id} FOR UPDATE")
    SalesOrder lockById(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select("SELECT * FROM sales_order WHERE tenant_id = #{tenantId} AND order_no = #{orderNo}")
    SalesOrder findByNo(@Param("tenantId") long tenantId, @Param("orderNo") String orderNo);

    @Select("SELECT * FROM sales_order WHERE tenant_id = #{tenantId} AND idempotency_key = #{key}")
    SalesOrder findByIdempotencyKey(@Param("tenantId") long tenantId, @Param("key") String key);

    @Select("SELECT * FROM order_item WHERE tenant_id = #{tenantId} AND order_id = #{orderId} ORDER BY id")
    List<OrderItem> items(@Param("tenantId") long tenantId, @Param("orderId") long orderId);

    String SEARCH_WHERE = """
            WHERE tenant_id = #{tenantId}
            <if test="status != null"> AND status = #{status} </if>
            <if test="keyword != null and keyword != ''">
              AND (order_no LIKE CONCAT('%', #{keyword}, '%') OR customer_name LIKE CONCAT('%', #{keyword}, '%')
                   OR customer_email LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="from != null"> AND created_at &gt;= #{from} </if>
            <if test="to != null"> AND created_at &lt; #{to} </if>
            """;

    @Select("<script>SELECT * FROM sales_order " + SEARCH_WHERE
            + " ORDER BY created_at DESC, id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<SalesOrder> search(@Param("tenantId") long tenantId, @Param("status") OrderStatus status,
                            @Param("keyword") String keyword, @Param("from") LocalDateTime from,
                            @Param("to") LocalDateTime to, @Param("limit") int limit, @Param("offset") int offset);

    @Select("<script>SELECT COUNT(*) FROM sales_order " + SEARCH_WHERE + "</script>")
    long count(@Param("tenantId") long tenantId, @Param("status") OrderStatus status, @Param("keyword") String keyword,
               @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // ---- state transitions: each is a compare-and-set on status ----

    @Update("""
            UPDATE sales_order SET status = 'PAID', paid_at = NOW(3)
            WHERE tenant_id = #{tenantId} AND id = #{id} AND status = 'PENDING_PAYMENT'
            """)
    int markPaid(@Param("tenantId") long tenantId, @Param("id") long id);

    @Update("""
            UPDATE sales_order SET status = 'CANCELLED', cancelled_at = NOW(3), cancel_reason = #{reason}
            WHERE tenant_id = #{tenantId} AND id = #{id} AND status = 'PENDING_PAYMENT'
            """)
    int markCancelled(@Param("tenantId") long tenantId, @Param("id") long id, @Param("reason") String reason);

    @Update("""
            UPDATE sales_order SET status = 'SHIPPED', shipped_at = NOW(3)
            WHERE tenant_id = #{tenantId} AND id = #{id} AND status = 'PAID'
            """)
    int markShipped(@Param("tenantId") long tenantId, @Param("id") long id);

    @Update("""
            UPDATE sales_order SET status = 'COMPLETED', completed_at = NOW(3)
            WHERE tenant_id = #{tenantId} AND id = #{id} AND status = 'SHIPPED'
            """)
    int markCompleted(@Param("tenantId") long tenantId, @Param("id") long id);

    @Update("""
            UPDATE sales_order SET status = 'REFUNDED', cancel_reason = #{reason}
            WHERE tenant_id = #{tenantId} AND id = #{id} AND status = 'PAID'
            """)
    int markRefunded(@Param("tenantId") long tenantId, @Param("id") long id, @Param("reason") String reason);

    /** Expiry sweeper runs across tenants. */
    @Select("""
            SELECT id, tenant_id FROM sales_order
            WHERE status = 'PENDING_PAYMENT' AND expires_at < NOW(3)
            ORDER BY expires_at LIMIT #{limit}
            """)
    List<SalesOrder> findExpiredGlobal(int limit);
}
