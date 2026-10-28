package com.orderpilot.payment;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PaymentMapper {

    @Insert("""
            INSERT INTO payment (tenant_id, payment_no, order_id, amount, method, status, idempotency_key)
            VALUES (#{tenantId}, #{paymentNo}, #{orderId}, #{amount}, #{method}, #{status}, #{idempotencyKey})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Payment payment);

    String SELECT = """
            SELECT pm.*, o.order_no FROM payment pm
            JOIN sales_order o ON o.tenant_id = pm.tenant_id AND o.id = pm.order_id
            """;

    @Select(SELECT + " WHERE pm.tenant_id = #{tenantId} AND pm.id = #{id}")
    Payment findById(@Param("tenantId") long tenantId, @Param("id") long id);

    @Select(SELECT + " WHERE pm.tenant_id = #{tenantId} AND pm.order_id = #{orderId} ORDER BY pm.id DESC")
    List<Payment> findByOrder(@Param("tenantId") long tenantId, @Param("orderId") long orderId);

    @Select("""
            SELECT COUNT(*) FROM payment
            WHERE tenant_id = #{tenantId} AND order_id = #{orderId}
              AND (status = 'SUCCEEDED' OR (status = 'PENDING' AND created_at > NOW(3) - INTERVAL 2 MINUTE))
            """)
    int countActive(@Param("tenantId") long tenantId, @Param("orderId") long orderId);

    @Select("<script>" + SELECT + """
             WHERE pm.tenant_id = #{tenantId}
             <if test="status != null and status != ''"> AND pm.status = #{status} </if>
             ORDER BY pm.id DESC LIMIT #{limit} OFFSET #{offset}
            </script>""")
    List<Payment> page(@Param("tenantId") long tenantId, @Param("status") String status,
                       @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            <script>
            SELECT COUNT(*) FROM payment WHERE tenant_id = #{tenantId}
            <if test="status != null and status != ''"> AND status = #{status} </if>
            </script>
            """)
    long count(@Param("tenantId") long tenantId, @Param("status") String status);

    @Update("""
            UPDATE payment SET status = 'SUCCEEDED', provider_txn_id = #{txnId}, paid_at = NOW(3)
            WHERE tenant_id = #{tenantId} AND id = #{id} AND status = 'PENDING'
            """)
    int markSucceeded(@Param("tenantId") long tenantId, @Param("id") long id, @Param("txnId") String txnId);

    @Update("""
            UPDATE payment SET status = 'FAILED', failure_reason = #{reason}, idempotency_key = NULL
            WHERE tenant_id = #{tenantId} AND id = #{id} AND status = 'PENDING'
            """)
    int markFailed(@Param("tenantId") long tenantId, @Param("id") long id, @Param("reason") String reason);

    @Update("""
            UPDATE payment SET status = 'REFUNDED', failure_reason = #{reason}
            WHERE tenant_id = #{tenantId} AND id = #{id} AND status = 'SUCCEEDED'
            """)
    int markRefunded(@Param("tenantId") long tenantId, @Param("id") long id, @Param("reason") String reason);
}
