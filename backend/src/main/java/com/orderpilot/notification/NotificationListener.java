package com.orderpilot.notification;

import com.orderpilot.common.tenant.TenantContext;
import com.orderpilot.messaging.ConsumerDeduplicator;
import com.orderpilot.messaging.EventEnvelope;
import com.orderpilot.messaging.EventTypes;
import com.orderpilot.messaging.RabbitConfig;
import com.orderpilot.notification.NotificationMapper.Notification;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Turns domain events into in-app notifications. Failures are retried (3 attempts, back-off) by
 * the listener container and then dead-lettered to {@code op.notification.dlq}; duplicates are
 * dropped via {@link ConsumerDeduplicator}.
 */
@Component
public class NotificationListener {

    static final String CONSUMER = "notification";

    private final NotificationMapper mapper;
    private final ConsumerDeduplicator dedup;
    private final TransactionTemplate tx;

    public NotificationListener(NotificationMapper mapper, ConsumerDeduplicator dedup, TransactionTemplate tx) {
        this.mapper = mapper;
        this.dedup = dedup;
        this.tx = tx;
    }

    @RabbitListener(queues = RabbitConfig.NOTIFICATION_QUEUE)
    public void onEvent(EventEnvelope event) {
        TenantContext.runAs(event.tenantId(), () -> tx.executeWithoutResult(s -> {
            if (!dedup.firstDelivery(event.eventId(), CONSUMER)) {
                return;
            }
            Notification n = toNotification(event);
            if (n != null) {
                mapper.insert(n);
            }
        }));
    }

    static Notification toNotification(EventEnvelope e) {
        Notification n = new Notification();
        n.setTenantId(e.tenantId());
        n.setType(e.eventType());
        n.setLevel("INFO");
        String orderNo = e.str("orderNo");
        switch (e.eventType()) {
            case EventTypes.ORDER_CREATED -> {
                n.setTitle("New order " + orderNo);
                n.setContent(e.str("customerName") + " placed an order of $" + e.str("totalAmount"));
            }
            case EventTypes.ORDER_PAID -> {
                n.setLevel("SUCCESS");
                n.setTitle("Payment received for " + orderNo);
                n.setContent("$" + e.str("totalAmount") + " from " + e.str("customerName"));
            }
            case EventTypes.ORDER_CANCELLED -> {
                n.setLevel("WARNING");
                n.setTitle("Order " + orderNo + " cancelled");
                n.setContent("Reason: " + e.str("reason") + ". Reserved stock was released.");
            }
            case EventTypes.ORDER_SHIPPED -> {
                n.setTitle("Order " + orderNo + " shipped");
                n.setContent("Shipment dispatched to " + e.str("customerName"));
            }
            case EventTypes.ORDER_REFUNDED -> {
                n.setLevel("WARNING");
                n.setTitle("Order " + orderNo + " refunded");
                n.setContent("$" + e.str("totalAmount") + " refunded. Reason: " + e.str("reason"));
            }
            case EventTypes.PAYMENT_FAILED -> {
                n.setLevel("ERROR");
                n.setTitle("Payment failed (" + e.str("paymentNo") + ")");
                n.setContent(e.str("reason"));
            }
            case EventTypes.INVENTORY_LOW_STOCK -> {
                n.setLevel("WARNING");
                n.setTitle("Low stock: " + e.str("productName"));
                n.setContent(e.str("sku") + " has " + e.str("available") + " units left (threshold "
                        + e.str("threshold") + ")");
            }
            default -> {
                return null;
            }
        }
        return n;
    }
}
