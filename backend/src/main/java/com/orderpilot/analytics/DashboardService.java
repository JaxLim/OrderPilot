package com.orderpilot.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderpilot.analytics.AnalyticsMapper.CategorySales;
import com.orderpilot.analytics.AnalyticsMapper.DailyPoint;
import com.orderpilot.analytics.AnalyticsMapper.ProductSales;
import com.orderpilot.analytics.AnalyticsMapper.StatusCount;
import com.orderpilot.analytics.AnalyticsMapper.Totals;
import com.orderpilot.common.tenant.TenantContext;
import com.orderpilot.inventory.InventoryDtos.InventoryRow;
import com.orderpilot.inventory.InventoryService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);
    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    public record Kpis(BigDecimal revenue, BigDecimal revenueChangePct, long orders, long paidOrders,
                       BigDecimal averageOrderValue, BigDecimal paymentConversionPct, long unitsSold,
                       long pendingPayment, long awaitingShipment, long lowStockCount, BigDecimal inventoryValue) {
    }

    public record Summary(int days, Kpis kpis, List<DailyPoint> daily, List<StatusCount> statusBreakdown,
                          List<ProductSales> topProducts, List<CategorySales> categories,
                          List<InventoryRow> lowStock, LocalDateTime generatedAt) {
    }

    private final AnalyticsMapper mapper;
    private final InventoryService inventoryService;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public DashboardService(AnalyticsMapper mapper, InventoryService inventoryService, StringRedisTemplate redis,
                            ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.inventoryService = inventoryService;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    /** Aggregations are cached briefly per tenant: the dashboard is read far more than it changes. */
    public Summary summary(int days) {
        long tenantId = TenantContext.require();
        int window = Math.min(Math.max(days, 1), 90);
        String key = "op:t:" + tenantId + ":dashboard:" + window;
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                return objectMapper.readValue(cached, Summary.class);
            }
        } catch (Exception e) {
            log.debug("Dashboard cache miss/failure: {}", e.getMessage());
        }
        Summary summary = compute(tenantId, window);
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(summary), CACHE_TTL);
        } catch (Exception e) {
            log.debug("Dashboard cache write failed: {}", e.getMessage());
        }
        return summary;
    }

    Summary compute(long tenantId, int days) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = LocalDate.now().minusDays(days - 1L).atStartOfDay();
        LocalDateTime prevFrom = from.minusDays(days);

        Totals cur = mapper.totals(tenantId, from, now.plusMinutes(1));
        Totals prev = mapper.totals(tenantId, prevFrom, from);

        BigDecimal aov = cur.getPaidOrders() == 0 ? BigDecimal.ZERO
                : cur.getRevenue().divide(BigDecimal.valueOf(cur.getPaidOrders()), 2, RoundingMode.HALF_UP);
        BigDecimal conversion = cur.getTotalOrders() == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(cur.getPaidOrders() * 100.0 / cur.getTotalOrders()).setScale(1, RoundingMode.HALF_UP);
        BigDecimal change = prev.getRevenue().signum() == 0 ? null
                : cur.getRevenue().subtract(prev.getRevenue()).multiply(BigDecimal.valueOf(100))
                .divide(prev.getRevenue(), 1, RoundingMode.HALF_UP);

        Kpis kpis = new Kpis(cur.getRevenue(), change, cur.getTotalOrders(), cur.getPaidOrders(), aov, conversion,
                cur.getUnitsSold(), mapper.pendingPayment(tenantId), mapper.awaitingShipment(tenantId),
                mapper.lowStockCount(tenantId), mapper.inventoryValue(tenantId));

        return new Summary(days, kpis, fillGaps(mapper.daily(tenantId, from), from.toLocalDate(), days),
                mapper.statusBreakdown(tenantId, from), mapper.topProducts(tenantId, from, 5),
                mapper.categorySales(tenantId, from), inventoryService.lowStock(6), now);
    }

    /** Days without orders still appear on the chart as zeros. */
    private static List<DailyPoint> fillGaps(List<DailyPoint> points, LocalDate start, int days) {
        Map<LocalDate, DailyPoint> byDay = points.stream()
                .collect(Collectors.toMap(DailyPoint::getDay, Function.identity()));
        List<DailyPoint> out = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            LocalDate d = start.plusDays(i);
            DailyPoint p = byDay.get(d);
            if (p == null) {
                p = new DailyPoint();
                p.setDay(d);
                p.setRevenue(BigDecimal.ZERO);
            }
            out.add(p);
        }
        return out;
    }
}
