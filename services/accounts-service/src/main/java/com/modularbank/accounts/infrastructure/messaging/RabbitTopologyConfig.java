package com.modularbank.accounts.infrastructure.messaging;

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

import static com.modularbank.accounts.infrastructure.messaging.RabbitTopology.*;

/**
 * accounts-service is the consumer on {@code accounts.commands} (so it
 * declares the queue and both bindings) and the producer on
 * {@code accounts.events} (declared here so it exists even if
 * transfers-service hasn't started yet).
 *
 * Since Paso 3 (ADR-010), every message on the wire is an {@code EventEnvelope}
 * — the class mapper only needs that one mapping, not one per DTO.
 */
@Configuration
public class RabbitTopologyConfig {

    @Bean
    public DirectExchange accountsCommandsExchange() {
        return ExchangeBuilder.directExchange(ACCOUNTS_COMMANDS_EXCHANGE).durable(true).build();
    }

    @Bean
    public TopicExchange accountsEventsExchange() {
        return ExchangeBuilder.topicExchange(ACCOUNTS_EVENTS_EXCHANGE).durable(true).build();
    }

    // Resilience pattern (dead-letter queue, see ADR-010 / docs/evidencia/paso-3/03-patrones-resiliencia.md):
    // messages that exhaust the retry advice chain below land here instead of being lost.
    @Bean
    public Queue accountsServiceCommandsDeadLetterQueue() {
        return QueueBuilder.durable(ACCOUNTS_SERVICE_COMMANDS_QUEUE + ".dlq").build();
    }

    @Bean
    public Queue accountsServiceCommandsQueue() {
        return QueueBuilder.durable(ACCOUNTS_SERVICE_COMMANDS_QUEUE)
            .withArgument("x-dead-letter-exchange", "")
            .withArgument("x-dead-letter-routing-key", ACCOUNTS_SERVICE_COMMANDS_QUEUE + ".dlq")
            .build();
    }

    @Bean
    public Binding debitCommandBinding(Queue accountsServiceCommandsQueue, DirectExchange accountsCommandsExchange) {
        return BindingBuilder.bind(accountsServiceCommandsQueue).to(accountsCommandsExchange).with(ROUTING_KEY_ACCOUNT_DEBIT);
    }

    @Bean
    public Binding creditCommandBinding(Queue accountsServiceCommandsQueue, DirectExchange accountsCommandsExchange) {
        return BindingBuilder.bind(accountsServiceCommandsQueue).to(accountsCommandsExchange).with(ROUTING_KEY_ACCOUNT_CREDIT);
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
     * before rejecting without requeue — which routes to the DLQ above.
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
