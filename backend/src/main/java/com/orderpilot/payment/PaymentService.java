package com.orderpilot.payment;

import com.orderpilot.audit.AuditService;
import com.orderpilot.common.api.PageQuery;
import com.orderpilot.common.api.PageResult;
import com.orderpilot.common.error.BizException;
import com.orderpilot.common.error.ErrorCode;
import com.orderpilot.common.idempotency.IdempotencyService;
import com.orderpilot.common.tenant.TenantContext;
import com.orderpilot.common.util.Ids;
import com.orderpilot.messaging.EventTypes;
import com.orderpilot.messaging.OutboxService;
import com.orderpilot.order.OrderService;
import com.orderpilot.order.OrderStatus;
import com.orderpilot.order.SalesOrder;
import com.orderpilot.payment.PaymentGatewaySimulator.ChargeResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Payment flow in three steps so no DB transaction (or row lock) is held during the slow
 * network call to the provider:
 * <ol>
 *   <li>TX1 — lock the order row, verify it is payable, insert a PENDING payment.</li>
 *   <li>Provider call (no transaction).</li>
 *   <li>TX2 — record the outcome; on success CAS the order to PAID and commit the reserved stock.
 *       If the order was cancelled by the timeout in the meantime, the captured money is refunded
 *       automatically instead of silently keeping it.</li>
 * </ol>
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    public record PayRequest(@NotBlank @Pattern(regexp = "CARD|ALIPAY|WECHAT_PAY|BANK_TRANSFER") String method,
                             Boolean simulateDecline) {
    }

    public record RefundRequest(@NotBlank @Size(max = 255) String reason) {
    }

    private final PaymentMapper paymentMapper;
    private final OrderService orderService;
    private final PaymentGatewaySimulator gateway;
    private final OutboxService outbox;
    private final IdempotencyService idempotency;
    private final AuditService auditService;
    private final TransactionTemplate tx;

    public PaymentService(PaymentMapper paymentMapper, OrderService orderService, PaymentGatewaySimulator gateway,
                          OutboxService outbox, IdempotencyService idempotency, AuditService auditService,
                          TransactionTemplate tx) {
        this.paymentMapper = paymentMapper;
        this.orderService = orderService;
        this.gateway = gateway;
        this.outbox = outbox;
        this.idempotency = idempotency;
        this.auditService = auditService;
        this.tx = tx;
    }

    public Payment pay(long orderId, PayRequest req, String idempotencyKey) {
        return idempotency.execute("payment.pay", idempotencyKey, Payment.class,
                () -> doPay(orderId, req, idempotencyKey));
    }

    private Payment doPay(long orderId, PayRequest req, String idempotencyKey) {
        long tenantId = TenantContext.require();

        // TX1: reserve the right to charge this order.
        Payment payment = tx.execute(s -> {
            SalesOrder order = orderService.lockForPayment(orderId);
            if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
                throw BizException.invalidState("Order " + order.getOrderNo() + " is " + order.getStatus()
                        + " and cannot be paid");
            }
            if (order.getExpiresAt() != null && order.getExpiresAt().isBefore(LocalDateTime.now())) {
                throw BizException.invalidState("The payment window for order " + order.getOrderNo() + " has closed");
            }
            if (paymentMapper.countActive(tenantId, orderId) > 0) {
                throw new BizException(ErrorCode.REQUEST_IN_PROGRESS, "A payment for this order is already in progress");
            }
            Payment p = new Payment();
            p.setTenantId(tenantId);
            p.setPaymentNo(Ids.paymentNo());
            p.setOrderId(orderId);
            p.setAmount(order.getTotalAmount());
            p.setMethod(req.method());
            p.setStatus("PENDING");
            p.setIdempotencyKey(idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey);
            paymentMapper.insert(p);
            return p;
        });

        // Provider call outside any transaction.
        ChargeResult result;
        try {
            result = gateway.charge(payment.getPaymentNo(), payment.getAmount(), req.method(),
                    Boolean.TRUE.equals(req.simulateDecline()));
        } catch (RuntimeException e) {
            result = new ChargeResult(false, null, "Gateway error: " + e.getMessage());
        }
        ChargeResult outcome = result;

        // TX2: apply the outcome.
        tx.executeWithoutResult(s -> {
            if (!outcome.success()) {
                paymentMapper.markFailed(tenantId, payment.getId(), outcome.declineReason());
                Map<String, Object> payload = payload(payment);
                payload.put("reason", outcome.declineReason());
                outbox.publish("payment", payment.getId(), EventTypes.PAYMENT_FAILED, payload);
                return;
            }
            paymentMapper.markSucceeded(tenantId, payment.getId(), outcome.providerTxnId());
            if (!orderService.markPaid(orderId)) {
                // Order expired/cancelled while the provider was processing: give the money back.
                gateway.refund(outcome.providerTxnId());
                paymentMapper.markRefunded(tenantId, payment.getId(), "Order no longer payable; auto-refunded");
                log.warn("Payment {} captured for non-payable order {}, auto-refunded", payment.getPaymentNo(), orderId);
            }
        });

        Payment saved = paymentMapper.findById(tenantId, payment.getId());
        auditService.record("payment." + saved.getStatus().toLowerCase(), "payment", saved.getPaymentNo(),
                saved.getOrderNo() + " " + saved.getAmount() + " via " + saved.getMethod());
        if ("FAILED".equals(saved.getStatus())) {
            throw new BizException(ErrorCode.PAYMENT_DECLINED, saved.getFailureReason());
        }
        return saved;
    }

    public Payment refund(long orderId, String reason) {
        long tenantId = TenantContext.require();
        Payment refunded = tx.execute(s -> {
            SalesOrder order = orderService.lockForPayment(orderId);
            if (order.getStatus() != OrderStatus.PAID) {
                throw BizException.invalidState("Only paid, unshipped orders can be refunded (order is "
                        + order.getStatus() + ")");
            }
            Payment captured = paymentMapper.findByOrder(tenantId, orderId).stream()
                    .filter(p -> "SUCCEEDED".equals(p.getStatus())).findFirst()
                    .orElseThrow(() -> BizException.invalidState("No captured payment found for this order"));
            gateway.refund(captured.getProviderTxnId());
            paymentMapper.markRefunded(tenantId, captured.getId(), reason);
            orderService.markRefunded(orderId, reason);
            return paymentMapper.findById(tenantId, captured.getId());
        });
        auditService.record("payment.refund", "payment", refunded.getPaymentNo(), reason);
        return refunded;
    }

    public List<Payment> forOrder(long orderId) {
        return paymentMapper.findByOrder(TenantContext.require(), orderId);
    }

    public PageResult<Payment> page(String status, PageQuery q) {
        long tenantId = TenantContext.require();
        return new PageResult<>(paymentMapper.page(tenantId, status, q.size(), q.offset()),
                paymentMapper.count(tenantId, status), q.page(), q.size());
    }

    private static Map<String, Object> payload(Payment p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("paymentId", p.getId());
        m.put("paymentNo", p.getPaymentNo());
        m.put("orderId", p.getOrderId());
        m.put("amount", p.getAmount());
        m.put("method", p.getMethod());
        return m;
    }
}
