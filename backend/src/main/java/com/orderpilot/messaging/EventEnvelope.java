package com.orderpilot.messaging;

import java.time.Instant;
import java.util.Map;

/** Wire format of every message on {@code orderpilot.events}. {@code eventId} is the de-dup key. */
public record EventEnvelope(String eventId, long tenantId, String eventType, String aggregateType,
                            String aggregateId, Instant occurredAt, Map<String, Object> payload) {

    public String str(String key) {
        Object v = payload == null ? null : payload.get(key);
        return v == null ? null : String.valueOf(v);
    }

    public long longValue(String key) {
        return Long.parseLong(str(key));
    }
}
