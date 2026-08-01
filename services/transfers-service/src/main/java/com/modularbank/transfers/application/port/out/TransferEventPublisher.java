package com.modularbank.transfers.application.port.out;

import com.modularbank.transfers.adapter.out.messaging.dto.TransferCompletedEvent;
import com.modularbank.transfers.adapter.out.messaging.dto.TransferFailedEvent;

/** Outbound port: how the saga orchestrator tells the rest of the system (the monolith remnant) how a transfer ended. */
public interface TransferEventPublisher {
    void publishCompleted(TransferCompletedEvent event);
    void publishFailed(TransferFailedEvent event);
}
