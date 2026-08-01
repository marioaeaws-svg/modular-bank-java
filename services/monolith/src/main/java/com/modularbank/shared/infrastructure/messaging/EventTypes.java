package com.modularbank.shared.infrastructure.messaging;

/** eventType string constants this service consumes. See ADR-010 and docs/contracts/events/v1/. */
public final class EventTypes {

    private EventTypes() {
    }

    public static final String TRANSFER_COMPLETED_EVENT = "TransferCompletedEvent";
    public static final String TRANSFER_FAILED_EVENT = "TransferFailedEvent";
}
