package com.orderpilot.ai;

import com.orderpilot.ai.BusinessTools.LowStockItem;
import com.orderpilot.ai.BusinessTools.OrderSummary;
import com.orderpilot.ai.BusinessTools.SalesSummary;
import com.orderpilot.ai.BusinessTools.StockLevel;
import com.orderpilot.analytics.AnalyticsMapper.DailyPoint;
import com.orderpilot.analytics.AnalyticsMapper.ProductSales;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic fallback used when no LLM is configured (or the provider is down). It routes the
 * question to the same tools the LLM would call and renders a Markdown answer, so the assistant
 * stays useful — and every number it shows is still real data.
 */
class OfflineAnalyst {

    private static final Pattern ORDER_NO = Pattern.compile("\\bSO\\d{10,}\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DAYS = Pattern.compile("(\\d{1,2})\\s*(?:days?|d)\\b");
    private static final NumberFormat MONEY = NumberFormat.getCurrencyInstance(Locale.US);

    String answer(String question, BusinessTools tools) {
        String q = question.toLowerCase(Locale.ROOT);
        int days = days(q);

        Matcher order = ORDER_NO.matcher(question);
        if (order.find()) {
            return order(tools.findOrder(order.group().toUpperCase(Locale.ROOT)), order.group());
        }
        if (containsAny(q, "low stock", "restock", "reorder", "running out", "out of stock", "replenish")) {
            return lowStock(tools.getLowStockProducts());
        }
        if (containsAny(q, "top", "best", "popular", "best-selling", "bestseller")) {
            return topProducts(tools.getTopProducts(days, 5), days);
        }
        if (containsAny(q, "status", "pending", "cancel", "breakdown", "unpaid", "funnel")) {
            return statuses(tools.getOrderStatusBreakdown(days), days);
        }
        if (containsAny(q, "trend", "daily", "best day", "worst day", "per day")) {
            return trend(tools.getDailyRevenue(days), days);
        }
        if (containsAny(q, "stock of", "how many", "price of", "inventory of", "sku")) {
            String keyword = q.replaceAll(".*(?:stock of|how many|price of|inventory of|sku)\\s*", "")
                    .replaceAll("[?.!]", "").trim();
            if (!keyword.isBlank()) {
                return products(tools.searchProducts(keyword), keyword);
            }
        }
        if (containsAny(q, "revenue", "sales", "sold", "how are we", "how is", "doing", "performance", "summary",
                "overview", "report", "kpi")) {
            return overview(tools.getSalesSummary(days), tools.getLowStockProducts(), tools.getOrderStatusBreakdown(days));
        }
        return help();
    }

    private static int days(String q) {
        Matcher m = DAYS.matcher(q);
        if (m.find()) {
            return Math.min(Math.max(Integer.parseInt(m.group(1)), 1), 90);
        }
        if (q.contains("today")) return 1;
        if (q.contains("week")) return 7;
        if (q.contains("quarter")) return 90;
        return 30;
    }

    private static String overview(SalesSummary s, List<LowStockItem> low, Map<String, Long> statuses) {
        StringBuilder sb = new StringBuilder();
        sb.append("### Business overview — last ").append(s.days()).append(" days\n\n");
        sb.append("| Metric | Value |\n|---|---|\n");
        sb.append("| Revenue | ").append(money(s.revenue())).append(change(s.revenueChangePct())).append(" |\n");
        sb.append("| Orders (paid / total) | ").append(s.paidOrders()).append(" / ").append(s.totalOrders()).append(" |\n");
        sb.append("| Average order value | ").append(money(s.averageOrderValue())).append(" |\n");
        sb.append("| Payment conversion | ").append(s.paymentConversionPct()).append("% |\n");
        sb.append("| Units sold | ").append(s.unitsSold()).append(" |\n\n");
        sb.append("**Things to look at**\n\n");
        long pending = statuses.getOrDefault("PENDING_PAYMENT", 0L);
        if (pending > 0) {
            sb.append("- ").append(pending).append(" order(s) are waiting for payment; unpaid orders auto-cancel ")
                    .append("when the payment window closes.\n");
        }
        if (!low.isEmpty()) {
            sb.append("- ").append(low.size()).append(" product(s) are at or below their low-stock threshold, e.g. **")
                    .append(low.get(0).productName()).append("** (").append(low.get(0).available()).append(" left).\n");
        }
        if (s.cancelledOrders() > 0) {
            sb.append("- ").append(s.cancelledOrders()).append(" order(s) were cancelled in this period.\n");
        }
        if (pending == 0 && low.isEmpty() && s.cancelledOrders() == 0) {
            sb.append("- Nothing urgent. Stock levels and payments look healthy.\n");
        }
        return sb.toString();
    }

    private static String lowStock(List<LowStockItem> items) {
        if (items.isEmpty()) {
            return "All active products are above their low-stock thresholds. No reorder needed right now.";
        }
        StringBuilder sb = new StringBuilder("### Low-stock products\n\n| SKU | Product | Available | Threshold | Suggested reorder |\n|---|---|---|---|---|\n");
        for (LowStockItem i : items) {
            sb.append("| ").append(i.sku()).append(" | ").append(i.productName()).append(" | ").append(i.available())
                    .append(" | ").append(i.threshold()).append(" | ").append(i.suggestedReorderQty()).append(" |\n");
        }
        sb.append("\nSuggested reorder brings each item to roughly 3× its threshold.");
        return sb.toString();
    }

    private static String topProducts(List<ProductSales> items, int days) {
        if (items.isEmpty()) {
            return "No paid orders in the last " + days + " days yet.";
        }
        StringBuilder sb = new StringBuilder("### Top products — last " + days + " days\n\n| # | Product | Units | Revenue |\n|---|---|---|---|\n");
        int rank = 1;
        for (ProductSales p : items) {
            sb.append("| ").append(rank++).append(" | ").append(p.getProductName()).append(" (").append(p.getSku())
                    .append(") | ").append(p.getUnits()).append(" | ").append(money(p.getRevenue())).append(" |\n");
        }
        return sb.toString();
    }

    private static String statuses(Map<String, Long> counts, int days) {
        if (counts.isEmpty()) {
            return "No orders in the last " + days + " days.";
        }
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        StringBuilder sb = new StringBuilder("### Orders by status — last " + days + " days\n\n| Status | Orders | Share |\n|---|---|---|\n");
        counts.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).forEach(e ->
                sb.append("| ").append(e.getKey()).append(" | ").append(e.getValue()).append(" | ")
                        .append(Math.round(e.getValue() * 100.0 / total)).append("% |\n"));
        return sb.toString();
    }

    private static String trend(List<DailyPoint> points, int days) {
        if (points.isEmpty()) {
            return "No orders in the last " + days + " days.";
        }
        DailyPoint best = points.stream().max(Comparator.comparing(DailyPoint::getRevenue)).orElseThrow();
        DailyPoint worst = points.stream().min(Comparator.comparing(DailyPoint::getRevenue)).orElseThrow();
        BigDecimal total = points.stream().map(DailyPoint::getRevenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        return "### Revenue trend — last " + days + " days\n\n"
                + "- Total: **" + money(total) + "** across " + points.size() + " trading days\n"
                + "- Best day: **" + best.getDay() + "** with " + money(best.getRevenue()) + " (" + best.getOrders() + " orders)\n"
                + "- Slowest day: **" + worst.getDay() + "** with " + money(worst.getRevenue()) + "\n";
    }

    private static String order(OrderSummary o, String orderNo) {
        if (o == null) {
            return "I couldn't find order **" + orderNo + "** in this workspace.";
        }
        return "### Order " + o.orderNo() + "\n\n- Status: **" + o.status() + "**\n- Customer: " + o.customerName()
                + "\n- Total: " + money(o.totalAmount()) + "\n- Placed: " + o.createdAt() + " UTC\n- Items:\n"
                + String.join("\n", o.items().stream().map(i -> "  - " + i).toList());
    }

    private static String products(List<StockLevel> items, String keyword) {
        if (items.isEmpty()) {
            return "No products match \"" + keyword + "\".";
        }
        StringBuilder sb = new StringBuilder("| SKU | Product | Price | Available | Reserved |\n|---|---|---|---|---|\n");
        for (StockLevel s : items) {
            sb.append("| ").append(s.sku()).append(" | ").append(s.name()).append(s.lowStock() ? " ⚠️" : "")
                    .append(" | ").append(money(s.price())).append(" | ").append(s.available()).append(" | ")
                    .append(s.reserved()).append(" |\n");
        }
        return sb.toString();
    }

    private static String help() {
        return """
                I can answer questions about this workspace's live data. Try:

                - "How is the business doing this week?"
                - "What are my top products in the last 30 days?"
                - "Which products need restocking?"
                - "Show the order status breakdown"
                - "What was our best day this month?"
                - "Look up order SO…"
                """;
    }

    private static boolean containsAny(String q, String... words) {
        for (String w : words) {
            if (q.contains(w)) return true;
        }
        return false;
    }

    private static String money(BigDecimal v) {
        return MONEY.format(v == null ? BigDecimal.ZERO : v);
    }

    private static String change(BigDecimal pct) {
        if (pct == null) return "";
        return " (" + (pct.signum() >= 0 ? "▲ +" : "▼ ") + pct + "% vs previous period)";
    }
}
