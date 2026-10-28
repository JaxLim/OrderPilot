package com.orderpilot.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Read-model queries for the dashboard and the AI assistant tools. */
@Mapper
public interface AnalyticsMapper {

    @Data
    class Totals {
        private BigDecimal revenue;
        private long paidOrders;
        private long totalOrders;
        private long cancelledOrders;
        private long unitsSold;
    }

    @Data
    class DailyPoint {
        private LocalDate day;
        private BigDecimal revenue;
        private long orders;
    }

    @Data
    class StatusCount {
        private String status;
        private long count;
    }

    @Data
    class ProductSales {
        private Long productId;
        private String sku;
        private String productName;
        private long units;
        private BigDecimal revenue;
    }

    @Data
    class CategorySales {
        private String category;
        private BigDecimal revenue;
    }

    @Select("""
            SELECT COALESCE(SUM(CASE WHEN status IN ('PAID','SHIPPED','COMPLETED') THEN total_amount END), 0) AS revenue,
                   COUNT(CASE WHEN status IN ('PAID','SHIPPED','COMPLETED') THEN 1 END) AS paid_orders,
                   COUNT(*) AS total_orders,
                   COUNT(CASE WHEN status = 'CANCELLED' THEN 1 END) AS cancelled_orders,
                   COALESCE(SUM(CASE WHEN status IN ('PAID','SHIPPED','COMPLETED') THEN item_count END), 0) AS units_sold
            FROM sales_order
            WHERE tenant_id = #{tenantId} AND created_at >= #{from} AND created_at < #{to}
            """)
    Totals totals(@Param("tenantId") long tenantId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Select("""
            SELECT DATE(created_at) AS day,
                   COALESCE(SUM(CASE WHEN status IN ('PAID','SHIPPED','COMPLETED') THEN total_amount END), 0) AS revenue,
                   COUNT(*) AS orders
            FROM sales_order
            WHERE tenant_id = #{tenantId} AND created_at >= #{from}
            GROUP BY DATE(created_at) ORDER BY day
            """)
    List<DailyPoint> daily(@Param("tenantId") long tenantId, @Param("from") LocalDateTime from);

    @Select("""
            SELECT status, COUNT(*) AS count FROM sales_order
            WHERE tenant_id = #{tenantId} AND created_at >= #{from}
            GROUP BY status
            """)
    List<StatusCount> statusBreakdown(@Param("tenantId") long tenantId, @Param("from") LocalDateTime from);

    @Select("""
            SELECT i.product_id, i.sku, i.product_name, SUM(i.quantity) AS units, SUM(i.line_total) AS revenue
            FROM order_item i
            JOIN sales_order o ON o.tenant_id = i.tenant_id AND o.id = i.order_id
            WHERE i.tenant_id = #{tenantId} AND o.created_at >= #{from}
              AND o.status IN ('PAID','SHIPPED','COMPLETED')
            GROUP BY i.product_id, i.sku, i.product_name
            ORDER BY revenue DESC LIMIT #{limit}
            """)
    List<ProductSales> topProducts(@Param("tenantId") long tenantId, @Param("from") LocalDateTime from,
                                   @Param("limit") int limit);

    @Select("""
            SELECT p.category, SUM(i.line_total) AS revenue
            FROM order_item i
            JOIN sales_order o ON o.tenant_id = i.tenant_id AND o.id = i.order_id
            JOIN product p ON p.tenant_id = i.tenant_id AND p.id = i.product_id
            WHERE i.tenant_id = #{tenantId} AND o.created_at >= #{from}
              AND o.status IN ('PAID','SHIPPED','COMPLETED')
            GROUP BY p.category ORDER BY revenue DESC
            """)
    List<CategorySales> categorySales(@Param("tenantId") long tenantId, @Param("from") LocalDateTime from);

    @Select("""
            SELECT COUNT(*) FROM inventory i JOIN product p ON p.tenant_id = i.tenant_id AND p.id = i.product_id
            WHERE i.tenant_id = #{tenantId} AND p.status = 'ACTIVE' AND i.available <= p.low_stock_threshold
            """)
    long lowStockCount(long tenantId);

    @Select("SELECT COUNT(*) FROM sales_order WHERE tenant_id = #{tenantId} AND status = 'PENDING_PAYMENT'")
    long pendingPayment(long tenantId);

    @Select("SELECT COUNT(*) FROM sales_order WHERE tenant_id = #{tenantId} AND status = 'PAID'")
    long awaitingShipment(long tenantId);

    @Select("""
            SELECT COALESCE(SUM(i.available * p.cost), 0) FROM inventory i
            JOIN product p ON p.tenant_id = i.tenant_id AND p.id = i.product_id
            WHERE i.tenant_id = #{tenantId}
            """)
    BigDecimal inventoryValue(long tenantId);
}
