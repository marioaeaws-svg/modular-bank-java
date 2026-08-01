package com.modularbank.transfers.adapter.out.messaging;

import com.modularbank.transfers.adapter.out.messaging.dto.CreditAccountCommand;
import com.modularbank.transfers.adapter.out.messaging.dto.DebitAccountCommand;
import com.modularbank.transfers.application.port.out.AccountCommandPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.stereotype.Component;

import static com.modularbank.transfers.adapter.out.messaging.EventTypes.CREDIT_ACCOUNT_COMMAND;
import static com.modularbank.transfers.adapter.out.messaging.EventTypes.DEBIT_ACCOUNT_COMMAND;
import static com.modularbank.transfers.adapter.out.messaging.RabbitTopology.*;

@Component
@RequiredArgsConstructor
public class RabbitAccountCommandPublisher implements AccountCommandPublisher {

    private final AmqpTemplate amqpTemplate;
    private final EventEnvelopeFactory envelopeFactory;

    @Override
    public void publishDebit(DebitAccountCommand command) {
        amqpTemplate.convertAndSend(ACCOUNTS_COMMANDS_EXCHANGE, ROUTING_KEY_ACCOUNT_DEBIT,
            envelopeFactory.wrap(DEBIT_ACCOUNT_COMMAND, command));
    }

    @Override
    public void publishCredit(CreditAccountCommand command) {
        amqpTemplate.convertAndSend(ACCOUNTS_COMMANDS_EXCHANGE, ROUTING_KEY_ACCOUNT_CREDIT,
            envelopeFactory.wrap(CREDIT_ACCOUNT_COMMAND, command));
    }
}
