package com.orderpilot.payment;

import com.orderpilot.common.util.Ids;
import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

/**
 * Stand-in for a real PSP (Stripe, Adyen, Alipay...). It is called outside any DB transaction,
 * exactly like a network call to a real provider would be, and has realistic latency. The caller
 * chooses the outcome so declines can be demonstrated deterministically.
 */
@Component
public class PaymentGatewaySimulator {

    public record ChargeResult(boolean success, String providerTxnId, String declineReason) {
    }

    public ChargeResult charge(String paymentNo, BigDecimal amount, String method, boolean forceDecline) {
        sleep(ThreadLocalRandom.current().nextInt(60, 180));
        if (forceDecline) {
            return new ChargeResult(false, null, "Card declined by issuer (simulated)");
        }
        if (amount.compareTo(new BigDecimal("50000")) > 0) {
            return new ChargeResult(false, null, "Amount exceeds the simulated risk limit");
        }
        return new ChargeResult(true, Ids.providerTxnId(), null);
    }

    public String refund(String providerTxnId) {
        sleep(ThreadLocalRandom.current().nextInt(40, 120));
        return "RF-" + providerTxnId;
    }

    private static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
