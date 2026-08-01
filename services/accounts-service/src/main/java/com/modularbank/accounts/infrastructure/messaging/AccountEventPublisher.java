package com.modularbank.accounts.infrastructure.messaging;

import com.modularbank.accounts.infrastructure.messaging.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.stereotype.Component;

import static com.modularbank.accounts.infrastructure.messaging.EventTypes.*;
import static com.modularbank.accounts.infrastructure.messaging.RabbitTopology.*;

@Component
@RequiredArgsConstructor
public class AccountEventPublisher {

    private final AmqpTemplate amqpTemplate;
    private final EventEnvelopeFactory envelopeFactory;

    public void publishDebited(AccountDebitedEvent event) {
        amqpTemplate.convertAndSend(ACCOUNTS_EVENTS_EXCHANGE, ROUTING_KEY_ACCOUNT_DEBITED,
            envelopeFactory.wrap(ACCOUNT_DEBITED_EVENT, event));
    }

    public void publishDebitFailed(AccountDebitFailedEvent event) {
        amqpTemplate.convertAndSend(ACCOUNTS_EVENTS_EXCHANGE, ROUTING_KEY_ACCOUNT_DEBIT_FAILED,
            envelopeFactory.wrap(ACCOUNT_DEBIT_FAILED_EVENT, event));
    }

    public void publishCredited(AccountCreditedEvent event) {
        amqpTemplate.convertAndSend(ACCOUNTS_EVENTS_EXCHANGE, ROUTING_KEY_ACCOUNT_CREDITED,
            envelopeFactory.wrap(ACCOUNT_CREDITED_EVENT, event));
    }

    public void publishCreditFailed(AccountCreditFailedEvent event) {
        amqpTemplate.convertAndSend(ACCOUNTS_EVENTS_EXCHANGE, ROUTING_KEY_ACCOUNT_CREDIT_FAILED,
            envelopeFactory.wrap(ACCOUNT_CREDIT_FAILED_EVENT, event));
    }
}
