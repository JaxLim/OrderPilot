package com.orderpilot.messaging;

import java.time.Duration;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topology:
 * <pre>
 *  orderpilot.events (topic)
 *     ├─ order.* / payment.* / inventory.*  → op.notification      (DLX → op.notification.dlq)
 *     └─ order.created                       → op.order.delay       (TTL = payment timeout, no consumer)
 *                                                  └─ expires → orderpilot.dlx (direct) "order.timeout"
 *                                                                     → op.order.timeout (cancels unpaid order)
 * </pre>
 * The delay queue is the classic TTL + dead-letter pattern for delayed delivery without plugins.
 */
@Configuration
public class RabbitConfig {

    public static final String EVENTS_EXCHANGE = "orderpilot.events";
    public static final String DLX = "orderpilot.dlx";
    public static final String NOTIFICATION_QUEUE = "op.notification";
    public static final String NOTIFICATION_DLQ = "op.notification.dlq";
    public static final String ORDER_DELAY_QUEUE = "op.order.delay";
    public static final String ORDER_TIMEOUT_QUEUE = "op.order.timeout";
    public static final String ORDER_TIMEOUT_DLQ = "op.order.timeout.dlq";
    public static final String ORDER_TIMEOUT_KEY = "order.timeout";

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public Declarables topology(@Value("${orderpilot.order.payment-timeout}") Duration paymentTimeout) {
        TopicExchange events = new TopicExchange(EVENTS_EXCHANGE, true, false);
        DirectExchange dlx = new DirectExchange(DLX, true, false);

        Queue notification = QueueBuilder.durable(NOTIFICATION_QUEUE)
                .deadLetterExchange(DLX).deadLetterRoutingKey(NOTIFICATION_DLQ).build();
        Queue notificationDlq = QueueBuilder.durable(NOTIFICATION_DLQ).build();

        Queue delay = QueueBuilder.durable(ORDER_DELAY_QUEUE)
                .ttl((int) paymentTimeout.toMillis())
                .deadLetterExchange(DLX).deadLetterRoutingKey(ORDER_TIMEOUT_KEY).build();
        Queue timeout = QueueBuilder.durable(ORDER_TIMEOUT_QUEUE)
                .deadLetterExchange(DLX).deadLetterRoutingKey(ORDER_TIMEOUT_DLQ).build();
        Queue timeoutDlq = QueueBuilder.durable(ORDER_TIMEOUT_DLQ).build();

        Binding[] bindings = {
                BindingBuilder.bind(notification).to(events).with("order.*"),
                BindingBuilder.bind(notification).to(events).with("payment.*"),
                BindingBuilder.bind(notification).to(events).with("inventory.*"),
                BindingBuilder.bind(notificationDlq).to(dlx).with(NOTIFICATION_DLQ),
                BindingBuilder.bind(delay).to(events).with(EventTypes.ORDER_CREATED),
                BindingBuilder.bind(timeout).to(dlx).with(ORDER_TIMEOUT_KEY),
                BindingBuilder.bind(timeoutDlq).to(dlx).with(ORDER_TIMEOUT_DLQ),
        };
        return new Declarables(events, dlx, notification, notificationDlq, delay, timeout, timeoutDlq,
                bindings[0], bindings[1], bindings[2], bindings[3], bindings[4], bindings[5], bindings[6]);
    }
}
