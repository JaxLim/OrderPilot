package com.orderpilot.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Polls the outbox and publishes to RabbitMQ with publisher confirms. An event is marked SENT
 * only after the broker acks it and it was routed to at least one queue; otherwise it is retried
 * with exponential back-off and eventually parked as FAILED for manual replay.
 *
 * <p>Delivery is therefore at-least-once; consumers de-duplicate on {@code eventId}.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxMapper outboxMapper;
    private final RabbitTemplate rabbitTemplate;
    private final TransactionTemplate tx;
    private final ObjectMapper objectMapper;
    private final int batchSize;
    private final int maxAttempts;

    public OutboxRelay(OutboxMapper outboxMapper, RabbitTemplate rabbitTemplate, TransactionTemplate tx,
                       ObjectMapper objectMapper,
                       @Value("${orderpilot.outbox.batch-size}") int batchSize,
                       @Value("${orderpilot.outbox.max-attempts}") int maxAttempts) {
        this.outboxMapper = outboxMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.tx = tx;
        this.objectMapper = objectMapper;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${orderpilot.outbox.poll-interval-ms}", initialDelay = 3000)
    public void relay() {
        try {
            Integer sent;
            do {
                sent = tx.execute(status -> relayBatch());
            } while (sent != null && sent == batchSize);
        } catch (RuntimeException e) {
            log.warn("Outbox relay cycle failed: {}", e.getMessage());
        }
    }

    /** @return number of events processed in this batch */
    int relayBatch() {
        List<OutboxEvent> events = outboxMapper.lockPendingGlobal(batchSize);
        if (events.isEmpty()) {
            return 0;
        }
        List<CorrelationData> confirms = new ArrayList<>(events.size());
        for (OutboxEvent e : events) {
            CorrelationData cd = new CorrelationData(e.getEventId());
            confirms.add(cd);
            try {
                rabbitTemplate.convertAndSend(RabbitConfig.EVENTS_EXCHANGE, e.getEventType(), toEnvelope(e), m -> {
                    m.getMessageProperties().setMessageId(e.getEventId());
                    m.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    m.getMessageProperties().setHeader("x-tenant-id", e.getTenantId());
                    return m;
                }, cd);
            } catch (RuntimeException ex) {
                cd.getFuture().completeExceptionally(ex);
            }
        }
        for (int i = 0; i < events.size(); i++) {
            OutboxEvent e = events.get(i);
            CorrelationData cd = confirms.get(i);
            String error = awaitConfirm(cd);
            if (error == null) {
                outboxMapper.markSentGlobal(e.getId());
            } else {
                int attempt = e.getAttempts() + 1;
                long backoffSeconds = Math.min(300, 1L << Math.min(attempt, 8));
                outboxMapper.markRetryGlobal(e.getId(), truncate(error),
                        LocalDateTime.now().plusSeconds(backoffSeconds), maxAttempts);
                log.warn("Outbox event {} ({}) not confirmed, attempt {}: {}", e.getEventId(), e.getEventType(),
                        attempt, error);
            }
        }
        return events.size();
    }

    private String awaitConfirm(CorrelationData cd) {
        try {
            CorrelationData.Confirm confirm = cd.getFuture().get(5, TimeUnit.SECONDS);
            if (!confirm.isAck()) {
                return "nack: " + confirm.getReason();
            }
            if (cd.getReturned() != null) {
                return "unroutable: " + cd.getReturned().getReplyText();
            }
            return null;
        } catch (Exception ex) {
            return ex.getClass().getSimpleName() + ": " + ex.getMessage();
        }
    }

    private EventEnvelope toEnvelope(OutboxEvent e) {
        try {
            Map<String, Object> payload = objectMapper.readValue(e.getPayload(), new TypeReference<>() {
            });
            return new EventEnvelope(e.getEventId(), e.getTenantId(), e.getEventType(), e.getAggregateType(),
                    e.getAggregateId(), e.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant(), payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Corrupt outbox payload " + e.getEventId(), ex);
        }
    }

    private static String truncate(String s) {
        return s.length() > 500 ? s.substring(0, 500) : s;
    }
}
