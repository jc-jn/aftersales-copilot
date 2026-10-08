package com.aftersales.copilot.statistics.application;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class QueueMetrics {
    private final AmqpAdmin rabbit;
    private final AtomicReference<Double> dlq = new AtomicReference<>(Double.NaN);
    private final AtomicReference<Double> up = new AtomicReference<>(0.0);

    public QueueMetrics(AmqpAdmin rabbit, MeterRegistry registry) {
        this.rabbit = rabbit;
        Gauge.builder("aftersales.dlq.messages", dlq, v -> v.get()).register(registry);
        Gauge.builder("aftersales.rabbitmq.scrape.up", up, v -> v.get()).register(registry);
    }

    @Scheduled(fixedDelayString="${app.observability.refresh-ms:30000}", initialDelayString="${app.observability.initial-delay-ms:1000}")
    public void refresh() {
        try {
            var properties = rabbit.getQueueProperties("ai.dead-letter.q");
            if (properties == null) throw new IllegalStateException("Queue unavailable");
            dlq.set(((Number) properties.get(RabbitAdmin.QUEUE_MESSAGE_COUNT)).doubleValue()); up.set(1.0);
        } catch (RuntimeException e) { dlq.set(Double.NaN); up.set(0.0); }
    }
}
