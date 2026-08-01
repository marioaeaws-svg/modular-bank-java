package com.modularbank.shared.infrastructure.messaging;

/**
 * The monolith remnant's own copy of the {@code transfers.events} names (see
 * the identically-named class in transfers-service) — it only needs the one
 * exchange it consumes from, not the accounts.* exchanges (those are private
 * to the MS1<->MS2 conversation).
 */
public final class RabbitTopology {

    private RabbitTopology() {
    }

    public static final String TRANSFERS_EVENTS_EXCHANGE = "transfers.events";
    public static final String ROUTING_KEY_PATTERN = "transfer.*";

    public static final String NOTIFICATIONS_QUEUE = "monolith.notifications.transfer-events";
    public static final String AUDIT_QUEUE = "monolith.audit.transfer-events";
}
