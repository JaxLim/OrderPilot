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
 * question (English or Chinese) to the same tools the LLM would call and renders a Markdown answer,
 * so the assistant stays useful — and every number it shows is still real data.
 */
class OfflineAnalyst {

    private static final Pattern ORDER_NO = Pattern.compile("SO\\d{10,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern DAYS = Pattern.compile("(\\d{1,2})\\s*(?:days?|d\\b|天|日)");
    private static final NumberFormat MONEY = NumberFormat.getCurrencyInstance(Locale.US);
    private static final Map<String, String> STATUS_ZH = Map.of(
            "PENDING_PAYMENT", "待支付", "PAID", "已支付", "SHIPPED", "已发货",
            "COMPLETED", "已完成", "CANCELLED", "已取消", "REFUNDED", "已退款");

    /** Language-bound renderer for one answer. */
    private record T(boolean zh) {
        String s(String en, String cn) {
            return zh ? cn : en;
        }

        String status(String code) {
            return zh ? STATUS_ZH.getOrDefault(code, code) : code;
        }
    }

    String answer(String question, BusinessTools tools) {
        return answer(question, tools, false);
    }

    String answer(String question, BusinessTools tools, boolean chinese) {
        T t = new T(chinese || containsCjk(question));
        String q = question.toLowerCase(Locale.ROOT);
        int days = days(q);

        Matcher order = ORDER_NO.matcher(question);
        if (order.find()) {
            return order(t, tools.findOrder(order.group().toUpperCase(Locale.ROOT)), order.group());
        }
        if (containsAny(q, "low stock", "restock", "reorder", "running out", "out of stock", "replenish",
                "补货", "库存不足", "低库存", "缺货", "进货", "库存预警")) {
            return lowStock(t, tools.getLowStockProducts());
        }
        if (containsAny(q, "top", "best", "popular", "best-selling", "bestseller", "热销", "畅销", "最好卖", "排行", "前5", "前五")) {
            return topProducts(t, tools.getTopProducts(days, 5), days);
        }
        if (containsAny(q, "status", "pending", "cancel", "breakdown", "unpaid", "funnel", "状态", "待支付", "取消", "分布", "未支付")) {
            return statuses(t, tools.getOrderStatusBreakdown(days), days);
        }
        if (containsAny(q, "trend", "daily", "best day", "worst day", "per day", "趋势", "每天", "每日", "哪天", "最好的一天")) {
            return trend(t, tools.getDailyRevenue(days), days);
        }
        if (containsAny(q, "stock of", "how many", "price of", "inventory of", "sku", "库存", "价格", "多少件")) {
            String keyword = q.replaceAll(".*(?:stock of|how many|price of|inventory of|sku|库存|价格|多少件)\\s*", "")
                    .replaceAll("[?.!？。！的]", "").trim();
            if (!keyword.isBlank()) {
                return products(t, tools.searchProducts(keyword), keyword);
            }
        }
        if (containsAny(q, "revenue", "sales", "sold", "how are we", "how is", "doing", "performance", "summary",
                "overview", "report", "kpi", "营收", "收入", "销售", "销量", "业绩", "经营", "概况", "怎么样", "总结", "报告")) {
            return overview(t, tools.getSalesSummary(days), tools.getLowStockProducts(), tools.getOrderStatusBreakdown(days));
        }
        return help(t);
    }

    private static int days(String q) {
        Matcher m = DAYS.matcher(q);
        if (m.find()) {
            return Math.min(Math.max(Integer.parseInt(m.group(1)), 1), 90);
        }
        if (q.contains("today") || q.contains("今天") || q.contains("今日")) return 1;
        if (q.contains("week") || q.contains("周") || q.contains("星期")) return 7;
        if (q.contains("quarter") || q.contains("季度")) return 90;
        return 30;
    }

    private static String overview(T t, SalesSummary s, List<LowStockItem> low, Map<String, Long> statuses) {
        StringBuilder sb = new StringBuilder();
        sb.append(t.s("### Business overview — last " + s.days() + " days\n\n", "### 经营概况 — 最近 " + s.days() + " 天\n\n"));
        sb.append(t.s("| Metric | Value |\n|---|---|\n", "| 指标 | 数值 |\n|---|---|\n"));
        sb.append("| ").append(t.s("Revenue", "营收")).append(" | ").append(money(s.revenue())).append(change(t, s.revenueChangePct())).append(" |\n");
        sb.append("| ").append(t.s("Orders (paid / total)", "订单（已支付 / 全部）")).append(" | ").append(s.paidOrders()).append(" / ").append(s.totalOrders()).append(" |\n");
        sb.append("| ").append(t.s("Average order value", "客单价")).append(" | ").append(money(s.averageOrderValue())).append(" |\n");
        sb.append("| ").append(t.s("Payment conversion", "支付转化率")).append(" | ").append(s.paymentConversionPct()).append("% |\n");
        sb.append("| ").append(t.s("Units sold", "销售件数")).append(" | ").append(s.unitsSold()).append(" |\n\n");
        sb.append(t.s("**Things to look at**\n\n", "**需要关注**\n\n"));
        long pending = statuses.getOrDefault("PENDING_PAYMENT", 0L);
        if (pending > 0) {
            sb.append(t.s("- " + pending + " order(s) are waiting for payment; unpaid orders auto-cancel when the payment window closes.\n",
                    "- 有 " + pending + " 笔订单待支付，超过支付时限将自动取消并释放库存。\n"));
        }
        if (!low.isEmpty()) {
            LowStockItem first = low.get(0);
            sb.append(t.s("- " + low.size() + " product(s) are at or below their low-stock threshold, e.g. **" + first.productName()
                            + "** (" + first.available() + " left).\n",
                    "- 有 " + low.size() + " 个商品库存低于预警线，例如 **" + first.productName() + "**（剩余 " + first.available() + " 件）。\n"));
        }
        if (s.cancelledOrders() > 0) {
            sb.append(t.s("- " + s.cancelledOrders() + " order(s) were cancelled in this period.\n",
                    "- 本期共有 " + s.cancelledOrders() + " 笔订单被取消。\n"));
        }
        if (pending == 0 && low.isEmpty() && s.cancelledOrders() == 0) {
            sb.append(t.s("- Nothing urgent. Stock levels and payments look healthy.\n", "- 暂无紧急事项，库存和支付情况良好。\n"));
        }
        return sb.toString();
    }

    private static String lowStock(T t, List<LowStockItem> items) {
        if (items.isEmpty()) {
            return t.s("All active products are above their low-stock thresholds. No reorder needed right now.",
                    "所有在售商品的库存都高于预警线，目前无需补货。");
        }
        StringBuilder sb = new StringBuilder(t.s(
                "### Low-stock products\n\n| SKU | Product | Available | Threshold | Suggested reorder |\n|---|---|---|---|---|\n",
                "### 低库存商品\n\n| SKU | 商品 | 可售 | 预警线 | 建议补货 |\n|---|---|---|---|---|\n"));
        for (LowStockItem i : items) {
            sb.append("| ").append(i.sku()).append(" | ").append(i.productName()).append(" | ").append(i.available())
                    .append(" | ").append(i.threshold()).append(" | ").append(i.suggestedReorderQty()).append(" |\n");
        }
        sb.append(t.s("\nSuggested reorder brings each item to roughly 3× its threshold.", "\n建议补货量可将库存恢复到预警线的约 3 倍。"));
        return sb.toString();
    }

    private static String topProducts(T t, List<ProductSales> items, int days) {
        if (items.isEmpty()) {
            return t.s("No paid orders in the last " + days + " days yet.", "最近 " + days + " 天还没有已支付的订单。");
        }
        StringBuilder sb = new StringBuilder(t.s(
                "### Top products — last " + days + " days\n\n| # | Product | Units | Revenue |\n|---|---|---|---|\n",
                "### 热销商品 — 最近 " + days + " 天\n\n| # | 商品 | 件数 | 销售额 |\n|---|---|---|---|\n"));
        int rank = 1;
        for (ProductSales p : items) {
            sb.append("| ").append(rank++).append(" | ").append(p.getProductName()).append(" (").append(p.getSku())
                    .append(") | ").append(p.getUnits()).append(" | ").append(money(p.getRevenue())).append(" |\n");
        }
        return sb.toString();
    }

    private static String statuses(T t, Map<String, Long> counts, int days) {
        if (counts.isEmpty()) {
            return t.s("No orders in the last " + days + " days.", "最近 " + days + " 天没有订单。");
        }
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        StringBuilder sb = new StringBuilder(t.s(
                "### Orders by status — last " + days + " days\n\n| Status | Orders | Share |\n|---|---|---|\n",
                "### 订单状态分布 — 最近 " + days + " 天\n\n| 状态 | 订单数 | 占比 |\n|---|---|---|\n"));
        counts.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).forEach(e ->
                sb.append("| ").append(t.status(e.getKey())).append(" | ").append(e.getValue()).append(" | ")
                        .append(Math.round(e.getValue() * 100.0 / total)).append("% |\n"));
        return sb.toString();
    }

    private static String trend(T t, List<DailyPoint> points, int days) {
        if (points.isEmpty()) {
            return t.s("No orders in the last " + days + " days.", "最近 " + days + " 天没有订单。");
        }
        DailyPoint best = points.stream().max(Comparator.comparing(DailyPoint::getRevenue)).orElseThrow();
        DailyPoint worst = points.stream().min(Comparator.comparing(DailyPoint::getRevenue)).orElseThrow();
        BigDecimal total = points.stream().map(DailyPoint::getRevenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        return t.s("### Revenue trend — last " + days + " days\n\n"
                        + "- Total: **" + money(total) + "** across " + points.size() + " trading days\n"
                        + "- Best day: **" + best.getDay() + "** with " + money(best.getRevenue()) + " (" + best.getOrders() + " orders)\n"
                        + "- Slowest day: **" + worst.getDay() + "** with " + money(worst.getRevenue()) + "\n",
                "### 营收趋势 — 最近 " + days + " 天\n\n"
                        + "- 合计：**" + money(total) + "**，共 " + points.size() + " 个营业日\n"
                        + "- 最佳一天：**" + best.getDay() + "**，营收 " + money(best.getRevenue()) + "（" + best.getOrders() + " 笔订单）\n"
                        + "- 最淡一天：**" + worst.getDay() + "**，营收 " + money(worst.getRevenue()) + "\n");
    }

    private static String order(T t, OrderSummary o, String orderNo) {
        if (o == null) {
            return t.s("I couldn't find order **" + orderNo + "** in this workspace.", "在当前工作区中未找到订单 **" + orderNo + "**。");
        }
        String items = String.join("\n", o.items().stream().map(i -> "  - " + i).toList());
        return t.s("### Order " + o.orderNo() + "\n\n- Status: **" + o.status() + "**\n- Customer: " + o.customerName()
                        + "\n- Total: " + money(o.totalAmount()) + "\n- Placed: " + o.createdAt() + " UTC\n- Items:\n" + items,
                "### 订单 " + o.orderNo() + "\n\n- 状态：**" + t.status(o.status()) + "**\n- 客户：" + o.customerName()
                        + "\n- 金额：" + money(o.totalAmount()) + "\n- 下单时间：" + o.createdAt() + " UTC\n- 商品：\n" + items);
    }

    private static String products(T t, List<StockLevel> items, String keyword) {
        if (items.isEmpty()) {
            return t.s("No products match \"" + keyword + "\".", "没有找到与“" + keyword + "”匹配的商品。");
        }
        StringBuilder sb = new StringBuilder(t.s("| SKU | Product | Price | Available | Reserved |\n|---|---|---|---|---|\n",
                "| SKU | 商品 | 价格 | 可售 | 已预留 |\n|---|---|---|---|---|\n"));
        for (StockLevel s : items) {
            sb.append("| ").append(s.sku()).append(" | ").append(s.name()).append(s.lowStock() ? " ⚠️" : "")
                    .append(" | ").append(money(s.price())).append(" | ").append(s.available()).append(" | ")
                    .append(s.reserved()).append(" |\n");
        }
        return sb.toString();
    }

    private static String help(T t) {
        return t.s("""
                I can answer questions about this workspace's live data. Try:

                - "How is the business doing this week?"
                - "What are my top products in the last 30 days?"
                - "Which products need restocking?"
                - "Show the order status breakdown"
                - "What was our best day this month?"
                - "Look up order SO…"
                """, """
                我可以基于当前工作区的实时数据回答问题，例如：

                - “本周经营情况怎么样？”
                - “最近 30 天的热销商品有哪些？”
                - “哪些商品需要补货？”
                - “订单状态分布如何？”
                - “这个月哪天营收最好？”
                - “查询订单 SO…”
                """);
    }

    private static boolean containsAny(String q, String... words) {
        for (String w : words) {
            if (q.contains(w)) return true;
        }
        return false;
    }

    private static boolean containsCjk(String s) {
        return s.codePoints().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN);
    }

    private static String money(BigDecimal v) {
        return MONEY.format(v == null ? BigDecimal.ZERO : v);
    }

    private static String change(T t, BigDecimal pct) {
        if (pct == null) return "";
        String arrow = pct.signum() >= 0 ? "▲ +" : "▼ ";
        return t.s(" (" + arrow + pct + "% vs previous period)", "（较上期 " + arrow + pct + "%）");
    }
}
