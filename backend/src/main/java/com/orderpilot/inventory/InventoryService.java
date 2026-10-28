package com.orderpilot.inventory;

import com.orderpilot.audit.AuditService;
import com.orderpilot.common.api.PageQuery;
import com.orderpilot.common.api.PageResult;
import com.orderpilot.common.error.BizException;
import com.orderpilot.common.error.ErrorCode;
import com.orderpilot.common.tenant.TenantContext;
import com.orderpilot.iam.AuthUser;
import com.orderpilot.inventory.InventoryDtos.AdjustRequest;
import com.orderpilot.inventory.InventoryDtos.InventoryRow;
import com.orderpilot.inventory.InventoryDtos.Movement;
import com.orderpilot.inventory.InventoryDtos.StockLine;
import com.orderpilot.messaging.EventTypes;
import com.orderpilot.messaging.OutboxService;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public API of the inventory module. Order/payment modules call these methods inside their own
 * transaction (MANDATORY), so stock changes commit or roll back atomically with the order.
 */
@Service
public class InventoryService {

    private final InventoryMapper mapper;
    private final OutboxService outbox;
    private final AuditService auditService;

    public InventoryService(InventoryMapper mapper, OutboxService outbox, AuditService auditService) {
        this.mapper = mapper;
        this.outbox = outbox;
        this.auditService = auditService;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void initialize(long productId, int initialStock) {
        long tenantId = TenantContext.require();
        mapper.insert(tenantId, productId, initialStock);
        if (initialStock > 0) {
            movement(tenantId, productId, "INBOUND", initialStock, "PRODUCT", String.valueOf(productId), "Initial stock");
        }
    }

    /**
     * Reserves every line or none. Lines are processed in product-id order so that two orders
     * touching the same products always lock rows in the same order — no deadlock cycles.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(List<StockLine> lines, String orderNo) {
        long tenantId = TenantContext.require();
        for (StockLine line : merge(lines)) {
            if (mapper.reserve(tenantId, line.productId(), line.quantity()) == 0) {
                InventoryRow row = mapper.findRow(tenantId, line.productId());
                String name = row == null ? "product " + line.productId() : row.getSku();
                int available = row == null ? 0 : row.getAvailable();
                // Throwing rolls back reservations already made for earlier lines.
                throw new BizException(ErrorCode.INSUFFICIENT_STOCK,
                        "Insufficient stock for " + name + ": requested " + line.quantity() + ", available " + available);
            }
            movement(tenantId, line.productId(), "RESERVE", -line.quantity(), "ORDER", orderNo, null);
            checkLowStock(tenantId, line.productId(), line.quantity());
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void release(List<StockLine> lines, String orderNo, String reason) {
        long tenantId = TenantContext.require();
        for (StockLine line : merge(lines)) {
            if (mapper.release(tenantId, line.productId(), line.quantity()) == 0) {
                throw new IllegalStateException("Reserved stock mismatch for product " + line.productId());
            }
            movement(tenantId, line.productId(), "RELEASE", line.quantity(), "ORDER", orderNo, reason);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void commitReserved(List<StockLine> lines, String orderNo) {
        long tenantId = TenantContext.require();
        for (StockLine line : merge(lines)) {
            if (mapper.commitReserved(tenantId, line.productId(), line.quantity()) == 0) {
                throw new IllegalStateException("Reserved stock mismatch for product " + line.productId());
            }
            movement(tenantId, line.productId(), "DEDUCT", -line.quantity(), "ORDER", orderNo, "Payment captured");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void restock(List<StockLine> lines, String orderNo, String reason) {
        long tenantId = TenantContext.require();
        for (StockLine line : merge(lines)) {
            mapper.adjustAvailable(tenantId, line.productId(), line.quantity());
            movement(tenantId, line.productId(), "RESTOCK", line.quantity(), "ORDER", orderNo, reason);
        }
    }

    @Transactional
    public InventoryRow adjust(long productId, AdjustRequest req) {
        long tenantId = TenantContext.require();
        if (mapper.findRow(tenantId, productId) == null) {
            throw BizException.notFound("Inventory for product", productId);
        }
        int delta = req.quantity();
        if ("INBOUND".equals(req.type()) && delta <= 0) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "Inbound quantity must be positive");
        }
        if (delta == 0) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "Quantity must not be zero");
        }
        if (mapper.adjustAvailable(tenantId, productId, delta) == 0) {
            throw new BizException(ErrorCode.INSUFFICIENT_STOCK, "Adjustment would make available stock negative");
        }
        movement(tenantId, productId, req.type(), delta, "MANUAL", null, req.note());
        if (delta < 0) {
            checkLowStock(tenantId, productId, -delta);
        }
        auditService.record("inventory." + req.type().toLowerCase(), "product", String.valueOf(productId),
                (delta > 0 ? "+" : "") + delta + " " + req.note());
        return mapper.findRow(tenantId, productId);
    }

    public PageResult<InventoryRow> list(String keyword, boolean lowOnly, PageQuery q) {
        long tenantId = TenantContext.require();
        return new PageResult<>(mapper.list(tenantId, keyword, lowOnly, q.size(), q.offset()),
                mapper.count(tenantId, keyword, lowOnly), q.page(), q.size());
    }

    public InventoryRow get(long productId) {
        InventoryRow row = mapper.findRow(TenantContext.require(), productId);
        if (row == null) {
            throw BizException.notFound("Inventory for product", productId);
        }
        return row;
    }

    public List<InventoryRow> lowStock(int limit) {
        return mapper.list(TenantContext.require(), null, true, limit, 0);
    }

    public PageResult<Movement> movements(Long productId, PageQuery q) {
        long tenantId = TenantContext.require();
        return new PageResult<>(mapper.movements(tenantId, productId, q.size(), q.offset()),
                mapper.countMovements(tenantId, productId), q.page(), q.size());
    }

    /** Emits a low-stock event only when a decrement crosses the threshold (no alert storm). */
    private void checkLowStock(long tenantId, long productId, int decrement) {
        InventoryRow row = mapper.findRow(tenantId, productId);
        boolean crossed = row != null && row.isLowStock()
                && row.getAvailable() + decrement > row.getLowStockThreshold();
        if (crossed && "ACTIVE".equals(row.getProductStatus())) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("productId", productId);
            payload.put("sku", row.getSku());
            payload.put("productName", row.getProductName());
            payload.put("available", row.getAvailable());
            payload.put("threshold", row.getLowStockThreshold());
            outbox.publish("product", productId, EventTypes.INVENTORY_LOW_STOCK, payload);
        }
    }

    private void movement(long tenantId, long productId, String type, int qty, String refType, String refId,
                          String note) {
        Movement m = new Movement();
        m.setTenantId(tenantId);
        m.setProductId(productId);
        m.setType(type);
        m.setQuantity(qty);
        m.setRefType(refType);
        m.setRefId(refId);
        m.setNote(note);
        AuthUser user = AuthUser.currentOrNull();
        m.setOperatorId(user == null ? null : user.userId());
        mapper.insertMovement(m);
    }

    /** Collapses duplicate product lines and sorts by product id (deadlock avoidance). */
    static List<StockLine> merge(List<StockLine> lines) {
        Map<Long, Integer> totals = new LinkedHashMap<>();
        for (StockLine l : lines) {
            if (l.quantity() <= 0) {
                throw new BizException(ErrorCode.VALIDATION_FAILED, "Quantity must be positive");
            }
            totals.merge(l.productId(), l.quantity(), Integer::sum);
        }
        return totals.entrySet().stream()
                .map(e -> new StockLine(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(StockLine::productId))
                .toList();
    }
}
