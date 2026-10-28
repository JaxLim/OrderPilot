package com.orderpilot.ai;

import com.orderpilot.analytics.AnalyticsMapper;
import com.orderpilot.analytics.AnalyticsMapper.DailyPoint;
import com.orderpilot.analytics.AnalyticsMapper.ProductSales;
import com.orderpilot.analytics.AnalyticsMapper.StatusCount;
import com.orderpilot.analytics.AnalyticsMapper.Totals;
import com.orderpilot.catalog.ProductDtos.ProductView;
import com.orderpilot.catalog.ProductService;
import com.orderpilot.common.api.PageQuery;
import com.orderpilot.common.tenant.TenantContext;
import com.orderpilot.inventory.InventoryDtos.InventoryRow;
import com.orderpilot.inventory.InventoryService;
import com.orderpilot.order.OrderDtos.OrderDetail;
import com.orderpilot.order.OrderService;
import com.orderpilot.order.SalesOrder;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Read-only business tools exposed to the LLM (function calling) and reused by the offline
 * analyst. An instance is created per request and bound to the caller's tenant on the server side:
 * the model never supplies a tenant id, so a prompt-injection cannot reach another tenant's data,
 * and no tool can mutate state.
 */
public class BusinessTools {

    public record SalesSummary(int days, BigDecimal revenue, BigDecimal previousPeriodRevenue,
                               BigDecimal revenueChangePct, long totalOrders, long paidOrders, long cancelledOrders,
                               long unitsSold, BigDecimal averageOrderValue, BigDecimal paymentConversionPct) {
    }

    public record LowStockItem(String sku, String productName, int available, int reserved, int threshold,
                               int suggestedReorderQty) {
    }

    public record OrderSummary(String orderNo, String status, String customerName, BigDecimal totalAmount,
                               LocalDateTime createdAt, List<String> items) {
    }

    public record StockLevel(String sku, String name, String status, BigDecimal price, int available, int reserved,
                             boolean lowStock) {
    }

    private final long tenantId;
    private final AnalyticsMapper analytics;
    private final InventoryService inventoryService;
    private final OrderService orderService;
    private final ProductService productService;
    private final Set<String> invoked = Collections.synchronizedSet(new LinkedHashSet<>());

    public BusinessTools(long tenantId, AnalyticsMapper analytics, InventoryService inventoryService,
                         OrderService orderService, ProductService productService) {
        this.tenantId = tenantId;
        this.analytics = analytics;
        this.inventoryService = inventoryService;
        this.orderService = orderService;
        this.productService = productService;
    }

    public List<String> invokedTools() {
        return new ArrayList<>(invoked);
    }

    @Tool(description = "Revenue, order counts, units sold, average order value and payment conversion for the "
            + "last N days, compared with the previous N days. Revenue counts paid, shipped and completed orders.")
    public SalesSummary getSalesSummary(@ToolParam(description = "Look-back window in days, 1-90") int days) {
        return run("getSalesSummary", () -> {
            int d = clamp(days);
            LocalDateTime from = LocalDate.now().minusDays(d - 1L).atStartOfDay();
            Totals cur = analytics.totals(tenantId, from, LocalDateTime.now().plusMinutes(1));
            Totals prev = analytics.totals(tenantId, from.minusDays(d), from);
            BigDecimal aov = cur.getPaidOrders() == 0 ? BigDecimal.ZERO
                    : cur.getRevenue().divide(BigDecimal.valueOf(cur.getPaidOrders()), 2, RoundingMode.HALF_UP);
            BigDecimal conv = cur.getTotalOrders() == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(cur.getPaidOrders() * 100.0 / cur.getTotalOrders()).setScale(1, RoundingMode.HALF_UP);
            BigDecimal change = prev.getRevenue().signum() == 0 ? null
                    : cur.getRevenue().subtract(prev.getRevenue()).multiply(BigDecimal.valueOf(100))
                    .divide(prev.getRevenue(), 1, RoundingMode.HALF_UP);
            return new SalesSummary(d, cur.getRevenue(), prev.getRevenue(), change, cur.getTotalOrders(),
                    cur.getPaidOrders(), cur.getCancelledOrders(), cur.getUnitsSold(), aov, conv);
        });
    }

    @Tool(description = "Best-selling products by revenue over the last N days.")
    public List<ProductSales> getTopProducts(@ToolParam(description = "Look-back window in days, 1-90") int days,
                                             @ToolParam(description = "How many products, 1-20") int limit) {
        return run("getTopProducts", () -> analytics.topProducts(tenantId,
                LocalDate.now().minusDays(clamp(days) - 1L).atStartOfDay(), Math.min(Math.max(limit, 1), 20)));
    }

    @Tool(description = "Active products whose available stock is at or below their low-stock threshold, with a "
            + "suggested reorder quantity.")
    public List<LowStockItem> getLowStockProducts() {
        return run("getLowStockProducts", () -> inventoryService.lowStock(20).stream()
                .map(BusinessTools::toLowStock).toList());
    }

    @Tool(description = "Number of orders per status (PENDING_PAYMENT, PAID, SHIPPED, COMPLETED, CANCELLED, "
            + "REFUNDED) created in the last N days.")
    public Map<String, Long> getOrderStatusBreakdown(@ToolParam(description = "Look-back window in days, 1-90") int days) {
        return run("getOrderStatusBreakdown", () -> {
            Map<String, Long> out = new LinkedHashMap<>();
            for (StatusCount c : analytics.statusBreakdown(tenantId,
                    LocalDate.now().minusDays(clamp(days) - 1L).atStartOfDay())) {
                out.put(c.getStatus(), c.getCount());
            }
            return out;
        });
    }

    @Tool(description = "Daily revenue and order count for the last N days, useful for trends and best/worst days.")
    public List<DailyPoint> getDailyRevenue(@ToolParam(description = "Look-back window in days, 1-90") int days) {
        return run("getDailyRevenue", () -> analytics.daily(tenantId,
                LocalDate.now().minusDays(clamp(days) - 1L).atStartOfDay()));
    }

    @Tool(description = "Look up a single order by its order number (e.g. SO2610281432051234).")
    public OrderSummary findOrder(@ToolParam(description = "The order number") String orderNo) {
        return run("findOrder", () -> {
            SalesOrder o = orderService.findByNo(orderNo == null ? "" : orderNo.trim());
            if (o == null) {
                return null;
            }
            OrderDetail d = orderService.detail(o.getId());
            List<String> lines = d.items().stream()
                    .map(i -> i.getQuantity() + " x " + i.getProductName() + " (" + i.getSku() + ")").toList();
            return new OrderSummary(o.getOrderNo(), o.getStatus().name(), o.getCustomerName(), o.getTotalAmount(),
                    o.getCreatedAt(), lines);
        });
    }

    @Tool(description = "Search products by name or SKU and return price and live stock levels.")
    public List<StockLevel> searchProducts(@ToolParam(description = "Part of a product name or SKU") String keyword) {
        return run("searchProducts", () -> productService.search(keyword, null, null, PageQuery.of(1, 10)).items()
                .stream().map(BusinessTools::toStockLevel).toList());
    }

    private <T> T run(String name, Supplier<T> body) {
        invoked.add(name);
        return TenantContext.callAs(tenantId, body);
    }

    private static int clamp(int days) {
        return Math.min(Math.max(days, 1), 90);
    }

    static LowStockItem toLowStock(InventoryRow r) {
        int suggested = Math.max(r.getLowStockThreshold() * 3 - r.getAvailable(), r.getLowStockThreshold());
        return new LowStockItem(r.getSku(), r.getProductName(), r.getAvailable(), r.getReserved(),
                r.getLowStockThreshold(), suggested);
    }

    private static StockLevel toStockLevel(ProductView p) {
        return new StockLevel(p.getSku(), p.getName(), p.getStatus(), p.getPrice(), p.getAvailable(), p.getReserved(),
                p.isLowStock());
    }
}
