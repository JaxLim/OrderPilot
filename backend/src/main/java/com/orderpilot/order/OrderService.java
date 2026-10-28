package com.orderpilot.order;

import com.orderpilot.audit.AuditService;
import com.orderpilot.catalog.Product;
import com.orderpilot.catalog.ProductService;
import com.orderpilot.common.api.PageQuery;
import com.orderpilot.common.api.PageResult;
import com.orderpilot.common.error.BizException;
import com.orderpilot.common.error.ErrorCode;
import com.orderpilot.common.idempotency.IdempotencyService;
import com.orderpilot.common.tenant.TenantContext;
import com.orderpilot.common.util.Ids;
import com.orderpilot.iam.AuthUser;
import com.orderpilot.inventory.InventoryDtos.StockLine;
import com.orderpilot.inventory.InventoryService;
import com.orderpilot.messaging.EventTypes;
import com.orderpilot.messaging.OutboxService;
import com.orderpilot.order.OrderDtos.CreateOrderItem;
import com.orderpilot.order.OrderDtos.CreateOrderRequest;
import com.orderpilot.order.OrderDtos.OrderDetail;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderMapper orderMapper;
    private final ProductService productService;
    private final InventoryService inventoryService;
    private final OutboxService outbox;
    private final IdempotencyService idempotency;
    private final AuditService auditService;
    private final TransactionTemplate tx;
    private final Duration paymentTimeout;

    public OrderService(OrderMapper orderMapper, ProductService productService, InventoryService inventoryService,
                        OutboxService outbox, IdempotencyService idempotency, AuditService auditService,
                        TransactionTemplate tx, @Value("${orderpilot.order.payment-timeout}") Duration paymentTimeout) {
        this.orderMapper = orderMapper;
        this.productService = productService;
        this.inventoryService = inventoryService;
        this.outbox = outbox;
        this.idempotency = idempotency;
        this.auditService = auditService;
        this.tx = tx;
        this.paymentTimeout = paymentTimeout;
    }

    // ------------------------------------------------------------------ create

    /**
     * Places an order. One local transaction covers: stock reservation (atomic conditional
     * UPDATEs), order + items insert, and the ORDER_CREATED outbox event. The Idempotency-Key makes
     * client retries safe; the (tenant_id, idempotency_key) unique index is the last line of defence.
     */
    public OrderDetail create(CreateOrderRequest req, String idempotencyKey) {
        long tenantId = TenantContext.require();
        try {
            return idempotency.execute("order.create", idempotencyKey, OrderDetail.class,
                    () -> tx.execute(s -> doCreate(tenantId, req, idempotencyKey)));
        } catch (DuplicateKeyException e) {
            SalesOrder existing = idempotencyKey == null ? null : orderMapper.findByIdempotencyKey(tenantId, idempotencyKey);
            if (existing == null) {
                throw e;
            }
            log.info("Duplicate order request resolved by DB unique key: {}", idempotencyKey);
            return detail(existing.getId());
        }
    }

    private OrderDetail doCreate(long tenantId, CreateOrderRequest req, String idempotencyKey) {
        String orderNo = Ids.orderNo();
        List<OrderItem> items = new ArrayList<>();
        List<StockLine> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;

        for (CreateOrderItem line : req.items()) {
            // Price and name come from the server-side catalog (cache-aside), never from the client.
            Product p = productService.findCached(line.productId())
                    .orElseThrow(() -> BizException.notFound("Product", line.productId()));
            if (!p.isActive()) {
                throw BizException.invalidState("Product " + p.getSku() + " is not available for sale");
            }
            OrderItem item = new OrderItem();
            item.setTenantId(tenantId);
            item.setProductId(p.getId());
            item.setSku(p.getSku());
            item.setProductName(p.getName());
            item.setUnitPrice(p.getPrice());
            item.setQuantity(line.quantity());
            item.setLineTotal(p.getPrice().multiply(BigDecimal.valueOf(line.quantity())));
            items.add(item);
            lines.add(new StockLine(p.getId(), line.quantity()));
            total = total.add(item.getLineTotal());
            count += line.quantity();
        }

        inventoryService.reserve(lines, orderNo);

        AuthUser user = AuthUser.currentOrNull();
        SalesOrder order = new SalesOrder();
        order.setTenantId(tenantId);
        order.setOrderNo(orderNo);
        order.setCustomerName(req.customerName());
        order.setCustomerEmail(req.customerEmail());
        order.setShippingAddress(req.shippingAddress());
        order.setRemark(req.remark());
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setTotalAmount(total);
        order.setItemCount(count);
        order.setIdempotencyKey(blankToNull(idempotencyKey));
        order.setCreatedBy(user == null ? null : user.userId());
        order.setExpiresAt(LocalDateTime.now().plus(paymentTimeout));
        orderMapper.insert(order);
        items.forEach(i -> i.setOrderId(order.getId()));
        orderMapper.insertItems(items);

        outbox.publish("order", order.getId(), EventTypes.ORDER_CREATED, event(order));
        auditService.record("order.create", "order", orderNo, "total=" + total);
        return new OrderDetail(orderMapper.findById(tenantId, order.getId()), orderMapper.items(tenantId, order.getId()));
    }

    // ------------------------------------------------------------- transitions

    @Transactional
    public OrderDetail cancel(long id, String reason) {
        long tenantId = TenantContext.require();
        SalesOrder order = require(tenantId, id);
        assertTransition(order, OrderStatus.CANCELLED);
        doCancel(tenantId, order, reason == null || reason.isBlank() ? "Cancelled by staff" : reason);
        auditService.record("order.cancel", "order", order.getOrderNo(), reason);
        return detail(id);
    }

    /**
     * Cancels an unpaid order whose payment window elapsed. Called by the RabbitMQ delayed message
     * and by the sweeper; safe to call any number of times (CAS on status).
     *
     * @return true if this call cancelled the order
     */
    @Transactional
    public boolean expireIfUnpaid(long orderId) {
        long tenantId = TenantContext.require();
        SalesOrder order = orderMapper.findById(tenantId, orderId);
        if (order == null || order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return false;
        }
        if (order.getExpiresAt() != null && order.getExpiresAt().isAfter(LocalDateTime.now())) {
            return false; // not yet due (e.g. timeout configuration changed); the sweeper retries later
        }
        return doCancel(tenantId, order, "Payment timeout");
    }

    private boolean doCancel(long tenantId, SalesOrder order, String reason) {
        if (orderMapper.markCancelled(tenantId, order.getId(), reason) == 0) {
            return false; // lost the race (e.g. paid a millisecond earlier)
        }
        inventoryService.release(stockLines(tenantId, order.getId()), order.getOrderNo(), reason);
        Map<String, Object> payload = event(order);
        payload.put("reason", reason);
        outbox.publish("order", order.getId(), EventTypes.ORDER_CANCELLED, payload);
        return true;
    }

    @Transactional
    public OrderDetail ship(long id) {
        long tenantId = TenantContext.require();
        SalesOrder order = require(tenantId, id);
        assertTransition(order, OrderStatus.SHIPPED);
        if (orderMapper.markShipped(tenantId, id) == 0) {
            throw BizException.invalidState("Order changed concurrently, please refresh");
        }
        outbox.publish("order", id, EventTypes.ORDER_SHIPPED, event(order));
        auditService.record("order.ship", "order", order.getOrderNo(), null);
        return detail(id);
    }

    @Transactional
    public OrderDetail complete(long id) {
        long tenantId = TenantContext.require();
        SalesOrder order = require(tenantId, id);
        assertTransition(order, OrderStatus.COMPLETED);
        if (orderMapper.markCompleted(tenantId, id) == 0) {
            throw BizException.invalidState("Order changed concurrently, please refresh");
        }
        auditService.record("order.complete", "order", order.getOrderNo(), null);
        return detail(id);
    }

    /** Called by the payment module inside its transaction once the gateway confirmed the charge. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean markPaid(long orderId) {
        long tenantId = TenantContext.require();
        if (orderMapper.markPaid(tenantId, orderId) == 0) {
            return false;
        }
        SalesOrder order = orderMapper.findById(tenantId, orderId);
        inventoryService.commitReserved(stockLines(tenantId, orderId), order.getOrderNo());
        outbox.publish("order", orderId, EventTypes.ORDER_PAID, event(order));
        return true;
    }

    /** Called by the payment module inside its refund transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void markRefunded(long orderId, String reason) {
        long tenantId = TenantContext.require();
        SalesOrder order = require(tenantId, orderId);
        assertTransition(order, OrderStatus.REFUNDED);
        if (orderMapper.markRefunded(tenantId, orderId, reason) == 0) {
            throw BizException.invalidState("Order changed concurrently, please refresh");
        }
        inventoryService.restock(stockLines(tenantId, orderId), order.getOrderNo(), "Refund: " + reason);
        Map<String, Object> payload = event(order);
        payload.put("reason", reason);
        outbox.publish("order", orderId, EventTypes.ORDER_REFUNDED, payload);
    }

    // ------------------------------------------------------------------ queries

    public OrderDetail detail(long id) {
        long tenantId = TenantContext.require();
        return new OrderDetail(require(tenantId, id), orderMapper.items(tenantId, id));
    }

    public SalesOrder lockForPayment(long id) {
        SalesOrder order = orderMapper.lockById(TenantContext.require(), id);
        if (order == null) {
            throw BizException.notFound("Order", id);
        }
        return order;
    }

    public SalesOrder findByNo(String orderNo) {
        return orderMapper.findByNo(TenantContext.require(), orderNo);
    }

    public PageResult<SalesOrder> search(OrderStatus status, String keyword, LocalDate from, LocalDate to,
                                         PageQuery q) {
        long tenantId = TenantContext.require();
        LocalDateTime f = from == null ? null : from.atStartOfDay();
        LocalDateTime t = to == null ? null : to.plusDays(1).atStartOfDay();
        return new PageResult<>(orderMapper.search(tenantId, status, keyword, f, t, q.size(), q.offset()),
                orderMapper.count(tenantId, status, keyword, f, t), q.page(), q.size());
    }

    // ------------------------------------------------------------------ helpers

    private SalesOrder require(long tenantId, long id) {
        SalesOrder o = orderMapper.findById(tenantId, id);
        if (o == null) {
            throw BizException.notFound("Order", id);
        }
        return o;
    }

    private static void assertTransition(SalesOrder order, OrderStatus target) {
        if (!order.getStatus().canTransitionTo(target)) {
            throw new BizException(ErrorCode.INVALID_STATE,
                    "Cannot move order " + order.getOrderNo() + " from " + order.getStatus() + " to " + target);
        }
    }

    private List<StockLine> stockLines(long tenantId, long orderId) {
        return orderMapper.items(tenantId, orderId).stream()
                .map(i -> new StockLine(i.getProductId(), i.getQuantity()))
                .toList();
    }

    private static Map<String, Object> event(SalesOrder o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderId", o.getId());
        m.put("orderNo", o.getOrderNo());
        m.put("customerName", o.getCustomerName());
        m.put("totalAmount", o.getTotalAmount());
        return m;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
