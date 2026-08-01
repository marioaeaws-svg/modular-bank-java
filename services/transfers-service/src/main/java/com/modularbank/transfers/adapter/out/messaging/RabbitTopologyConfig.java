package com.modularbank.transfers.adapter.out.messaging;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.DefaultClassMapper;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import java.util.Map;

import static com.modularbank.transfers.adapter.out.messaging.RabbitTopology.*;

/**
 * Declares the topology this service needs on all three exchanges: it is the
 * producer for {@code accounts.commands} and {@code transfers.events}, and
 * the consumer for {@code accounts.events}. Declaring an exchange that
 * another service also declares (same name/type/durability) is idempotent —
 * each side only declares what it needs, no shared schema module required.
 *
 * Since Paso 3 (ADR-010), every message on the wire is an {@code EventEnvelope}
 * — the class mapper only needs that one mapping, not one per DTO. The
 * business type travels as {@code envelope.eventType()}, resolved by the
 * listener (see AccountEventsListener), not by the AMQP type header.
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

    @Bean
    public TopicExchange transfersEventsExchange() {
        return ExchangeBuilder.topicExchange(TRANSFERS_EVENTS_EXCHANGE).durable(true).build();
    }

    // Resilience pattern (dead-letter queue, see ADR-010 / docs/evidencia/paso-3/03-patrones-resiliencia.md):
    // messages that exhaust the retry advice chain below (rabbitListenerContainerFactory) land here instead
    // of being lost or endlessly redelivered — using the no-name default exchange to route straight to the
    // DLQ by name is the standard minimal DLQ wiring for a single-queue-per-consumer topology like this one.
    @Bean
    public Queue accountEventsDeadLetterQueue() {
        return QueueBuilder.durable(TRANSFERS_SERVICE_ACCOUNT_EVENTS_QUEUE + ".dlq").build();
    }

    @Bean
    public Queue accountEventsQueue() {
        return QueueBuilder.durable(TRANSFERS_SERVICE_ACCOUNT_EVENTS_QUEUE)
            .withArgument("x-dead-letter-exchange", "")
            .withArgument("x-dead-letter-routing-key", TRANSFERS_SERVICE_ACCOUNT_EVENTS_QUEUE + ".dlq")
            .build();
    }

    @Bean
    public Binding accountEventsBinding(Queue accountEventsQueue, TopicExchange accountsEventsExchange) {
        return BindingBuilder.bind(accountEventsQueue).to(accountsEventsExchange).with(ROUTING_KEY_ACCOUNT_EVENTS_PATTERN);
    }

    @Bean
    public MessageConverter messageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultClassMapper classMapper = new DefaultClassMapper();
        classMapper.setIdClassMapping(Map.of("EventEnvelope", EventEnvelope.class));
        // DefaultClassMapper only builds its outbound reverse (Class -> id) mapping inside
        // afterPropertiesSet() (an InitializingBean callback) — since this instance isn't
        // itself a Spring bean, that callback never fires unless invoked explicitly. Without
        // this, outbound messages get the FQN as __TypeId__ instead of "EventEnvelope", and
        // the consumer's own class mapper rejects it as an untrusted package (bit us in Paso 2).
        classMapper.afterPropertiesSet();
        converter.setClassMapper(classMapper);
        return converter;
    }

    /**
     * Resilience pattern (retry with exponential backoff, see ADR-010 /
     * docs/evidencia/paso-3/03-patrones-resiliencia.md): a transient failure (e.g. the
     * database being briefly unreachable) gets 3 attempts, 500ms/1s/2s apart, before the
     * message is rejected without requeue — which, combined with the dead-letter args on
     * the queue above, routes it to the DLQ instead of retrying forever or being dropped.
     *
     * Overriding the bean named "rabbitListenerContainerFactory" replaces Spring Boot's
     * auto-configured one, so every {@code @RabbitListener} in this service gets it — but
     * building the factory from scratch would silently drop everything Boot normally wires
     * in from {@code spring.rabbitmq.*} (converter, and critically,
     * {@code listener.simple.observation-enabled} — without it, {@code @RabbitListener}
     * consumption creates no span and breaks the distributed trace at every broker hop,
     * Paso 4, see ADR-009). {@code SimpleRabbitListenerContainerFactoryConfigurer} is the
     * exact helper Boot's own autoconfiguration uses internally — reusing it here means our
     * custom factory gets identical property-driven wiring, with the retry chain layered on top.
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
