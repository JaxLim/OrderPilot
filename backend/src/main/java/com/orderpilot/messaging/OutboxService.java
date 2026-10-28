package com.orderpilot.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderpilot.common.tenant.TenantContext;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records domain events in the outbox table. {@link Propagation#MANDATORY} makes it impossible to
 * publish an event outside the business transaction: either both the state change and the event
 * are committed, or neither is — no dual-write between MySQL and RabbitMQ.
 */
@Service
public class OutboxService {

    private final OutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxMapper outboxMapper, ObjectMapper objectMapper) {
        this.outboxMapper = outboxMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String publish(String aggregateType, Object aggregateId, String eventType, Map<String, Object> payload) {
        OutboxEvent e = new OutboxEvent();
        e.setEventId(UUID.randomUUID().toString());
        e.setTenantId(TenantContext.require());
        e.setAggregateType(aggregateType);
        e.setAggregateId(String.valueOf(aggregateId));
        e.setEventType(eventType);
        try {
            e.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Event payload is not serializable", ex);
        }
        outboxMapper.insert(e);
        return e.getEventId();
    }
}
