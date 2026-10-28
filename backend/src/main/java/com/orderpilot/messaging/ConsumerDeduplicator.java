package com.orderpilot.messaging;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exactly-once <em>effect</em> on top of at-least-once delivery. Call inside the consumer's
 * transaction: the marker row commits together with the side effect, and rolls back with it if
 * the handler fails (so the redelivery is processed again).
 */
@Component
public class ConsumerDeduplicator {

    private final OutboxMapper outboxMapper;

    public ConsumerDeduplicator(OutboxMapper outboxMapper) {
        this.outboxMapper = outboxMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstDelivery(String messageId, String consumer) {
        return outboxMapper.markConsumedGlobal(messageId, consumer) == 1;
    }
}
