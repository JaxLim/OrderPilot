package com.orderpilot.order;

import com.orderpilot.common.tenant.TenantContext;
import com.orderpilot.messaging.EventEnvelope;
import com.orderpilot.messaging.RabbitConfig;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Two independent mechanisms cancel unpaid orders:
 * <ol>
 *   <li>Primary: ORDER_CREATED sits in a TTL queue and dead-letters into {@code op.order.timeout}
 *       exactly when the payment window closes.</li>
 *   <li>Safety net: a sweeper catches anything the broker path missed (broker outage, message
 *       parked in a DLQ, orders seeded without events).</li>
 * </ol>
 * Both call the same idempotent compare-and-set, so running both is harmless.
 */
@Component
public class OrderTimeoutHandlers {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutHandlers.class);

    private final OrderService orderService;
    private final OrderMapper orderMapper;

    public OrderTimeoutHandlers(OrderService orderService, OrderMapper orderMapper) {
        this.orderService = orderService;
        this.orderMapper = orderMapper;
    }

    @RabbitListener(queues = RabbitConfig.ORDER_TIMEOUT_QUEUE)
    public void onPaymentWindowClosed(EventEnvelope event) {
        long orderId = event.longValue("orderId");
        boolean cancelled = TenantContext.callAs(event.tenantId(), () -> orderService.expireIfUnpaid(orderId));
        if (cancelled) {
            log.info("Order {} cancelled by payment timeout (delayed message)", event.str("orderNo"));
        }
    }

    @Scheduled(fixedDelayString = "${orderpilot.order.sweep-interval-ms:60000}", initialDelay = 10000)
    public void sweepExpired() {
        List<SalesOrder> expired = orderMapper.findExpiredGlobal(200);
        for (SalesOrder o : expired) {
            try {
                boolean cancelled = TenantContext.callAs(o.getTenantId(), () -> orderService.expireIfUnpaid(o.getId()));
                if (cancelled) {
                    log.info("Order {} cancelled by expiry sweeper", o.getId());
                }
            } catch (RuntimeException e) {
                log.warn("Sweeper failed to expire order {}: {}", o.getId(), e.getMessage());
            }
        }
    }
}
