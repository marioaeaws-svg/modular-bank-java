package com.modularbank.transfers.adapter.out.messaging;

import com.modularbank.transfers.adapter.out.messaging.dto.TransferCompletedEvent;
import com.modularbank.transfers.adapter.out.messaging.dto.TransferFailedEvent;
import com.modularbank.transfers.application.port.out.TransferEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.stereotype.Component;

import static com.modularbank.transfers.adapter.out.messaging.EventTypes.TRANSFER_COMPLETED_EVENT;
import static com.modularbank.transfers.adapter.out.messaging.EventTypes.TRANSFER_FAILED_EVENT;
import static com.modularbank.transfers.adapter.out.messaging.RabbitTopology.*;

@Component
@RequiredArgsConstructor
public class RabbitTransferEventPublisher implements TransferEventPublisher {

    private final AmqpTemplate amqpTemplate;
    private final EventEnvelopeFactory envelopeFactory;

    @Override
    public void publishCompleted(TransferCompletedEvent event) {
        amqpTemplate.convertAndSend(TRANSFERS_EVENTS_EXCHANGE, ROUTING_KEY_TRANSFER_COMPLETED,
            envelopeFactory.wrap(TRANSFER_COMPLETED_EVENT, event));
    }

    @Override
    public void publishFailed(TransferFailedEvent event) {
        amqpTemplate.convertAndSend(TRANSFERS_EVENTS_EXCHANGE, ROUTING_KEY_TRANSFER_FAILED,
            envelopeFactory.wrap(TRANSFER_FAILED_EVENT, event));
    }
}
