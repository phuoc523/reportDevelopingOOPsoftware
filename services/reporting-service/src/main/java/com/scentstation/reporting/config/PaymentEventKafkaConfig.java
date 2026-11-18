package com.scentstation.reporting.config;

import com.scentstation.reporting.event.PaymentEventPayloadException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@ConditionalOnProperty(name = "reporting.events.kafka.enabled", havingValue = "true")
public class PaymentEventKafkaConfig {
    @Bean
    DefaultErrorHandler paymentEventErrorHandler(
            KafkaTemplate<Object, Object> kafka,
            @Value("${reporting.events.kafka.dead-letter-topic:payment-events.v1.DLT}") String deadLetterTopic,
            @Value("${reporting.events.kafka.retry-attempts:5}") long retryAttempts,
            @Value("${reporting.events.kafka.retry-backoff-ms:2000}") long retryBackoffMs
    ) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
            kafka, (record, exception) -> new TopicPartition(deadLetterTopic, record.partition()));
        DefaultErrorHandler handler = new DefaultErrorHandler(
            recoverer, new FixedBackOff(retryBackoffMs, Math.max(0, retryAttempts - 1)));
        handler.addNotRetryableExceptions(PaymentEventPayloadException.class);
        return handler;
    }
}
