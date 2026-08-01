package com.modularbank.shared.infrastructure.messaging;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.DefaultClassMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import java.util.Map;

import static com.modularbank.shared.infrastructure.messaging.RabbitTopology.*;

/**
 * The monolith remnant is a pure consumer here: notifications and audit each
 * get their own queue bound to {@code transfer.*} on {@code transfers.events},
 * so both receive every event independently (pub/sub fan-out, not competing
 * consumers) — this is what lets both modules "react to events" per the
 * Paso 2 requirement without either being extracted.
 *
 * Since Paso 3 (ADR-010), every message on the wire is an {@code EventEnvelope}
 * — the class mapper only needs that one mapping, not one per DTO.
 */
@Configuration
public class RabbitTopologyConfig {

    @Bean
    public TopicExchange transfersEventsExchange() {
        return ExchangeBuilder.topicExchange(TRANSFERS_EVENTS_EXCHANGE).durable(true).build();
    }

    // Resilience pattern (dead-letter queues, see ADR-010 / docs/evidencia/paso-3/03-patrones-resiliencia.md):
    // each consumer queue gets its own DLQ so a poison message from one consumer's retry
    // exhaustion never affects the other's delivery.
    @Bean
    public Queue notificationsDeadLetterQueue() {
        return QueueBuilder.durable(NOTIFICATIONS_QUEUE + ".dlq").build();
    }

    @Bean
    public Queue auditDeadLetterQueue() {
        return QueueBuilder.durable(AUDIT_QUEUE + ".dlq").build();
    }

    @Bean
    public Queue notificationsTransferEventsQueue() {
        return QueueBuilder.durable(NOTIFICATIONS_QUEUE)
            .withArgument("x-dead-letter-exchange", "")
            .withArgument("x-dead-letter-routing-key", NOTIFICATIONS_QUEUE + ".dlq")
            .build();
    }

    @Bean
    public Queue auditTransferEventsQueue() {
        return QueueBuilder.durable(AUDIT_QUEUE)
            .withArgument("x-dead-letter-exchange", "")
            .withArgument("x-dead-letter-routing-key", AUDIT_QUEUE + ".dlq")
            .build();
    }

    @Bean
    public Binding notificationsBinding(Queue notificationsTransferEventsQueue, TopicExchange transfersEventsExchange) {
        return BindingBuilder.bind(notificationsTransferEventsQueue).to(transfersEventsExchange).with(ROUTING_KEY_PATTERN);
    }

    @Bean
    public Binding auditBinding(Queue auditTransferEventsQueue, TopicExchange transfersEventsExchange) {
        return BindingBuilder.bind(auditTransferEventsQueue).to(transfersEventsExchange).with(ROUTING_KEY_PATTERN);
    }

    @Bean
    public MessageConverter messageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultClassMapper classMapper = new DefaultClassMapper();
        classMapper.setIdClassMapping(Map.of("EventEnvelope", EventEnvelope.class));
        // See transfers-service's RabbitTopologyConfig for why this explicit call is required.
        classMapper.afterPropertiesSet();
        converter.setClassMapper(classMapper);
        return converter;
    }

    /**
     * Resilience pattern (retry with exponential backoff): 3 attempts, 500ms/1s/2s apart,
     * before rejecting without requeue — which routes to the matching DLQ above.
     *
     * Overriding the bean named "rabbitListenerContainerFactory" replaces Spring Boot's
     * auto-configured one — but building the factory from scratch would silently drop
     * everything Boot normally wires in from {@code spring.rabbitmq.*} (converter, and
     * critically, {@code listener.simple.observation-enabled}, without which
     * {@code @RabbitListener} consumption creates no span and breaks the distributed trace
     * at every broker hop, Paso 4, see ADR-009). {@code SimpleRabbitListenerContainerFactoryConfigurer}
     * is the exact helper Boot's own autoconfiguration uses internally — reusing it here
     * means our custom factory gets identical property-driven wiring, with the retry chain
     * layered on top.
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setAdviceChain(retryInterceptor());
        return factory;
    }

    private RetryOperationsInterceptor retryInterceptor() {
        return RetryInterceptorBuilder.stateless()
            .maxAttempts(3)
            .backOffOptions(500, 2.0, 4000)
            .recoverer(new RejectAndDontRequeueRecoverer())
            .build();
    }
}
