package com.orderpilot.messaging;

/** Event types double as RabbitMQ routing keys on the {@code orderpilot.events} topic exchange. */
public final class EventTypes {

    public static final String ORDER_CREATED = "order.created";
    public static final String ORDER_PAID = "order.paid";
    public static final String ORDER_CANCELLED = "order.cancelled";
    public static final String ORDER_SHIPPED = "order.shipped";
    public static final String ORDER_REFUNDED = "order.refunded";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String INVENTORY_LOW_STOCK = "inventory.low_stock";

    private EventTypes() {
    }
}
