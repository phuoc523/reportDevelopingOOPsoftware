package com.scentstation.notification.config;

import com.scentstation.notification.event.MachineAlertPayloadException;
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
@ConditionalOnProperty(name = "notification.kafka.enabled", havingValue = "true")
public class MachineAlertKafkaConfig {
    @Bean
    DefaultErrorHandler machineAlertErrorHandler(
            KafkaTemplate<Object, Object> kafka,
            @Value("${notification.kafka.dead-letter-topic:machine-alert.v1.DLT}") String deadLetterTopic,
            @Value("${notification.kafka.retry-attempts:3}") long retryAttempts,
            @Value("${notification.kafka.retry-backoff-ms:1000}") long retryBackoffMs
    ) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
            kafka, (record, exception) -> new TopicPartition(deadLetterTopic, record.partition()));
        DefaultErrorHandler handler = new DefaultErrorHandler(
            recoverer, new FixedBackOff(retryBackoffMs, Math.max(0, retryAttempts - 1)));
        handler.addNotRetryableExceptions(MachineAlertPayloadException.class);
        return handler;
    }
}
