package com.modularbank.transfers.adapter.out.messaging;

/**
 * Names shared across the three exchanges this service touches. Kept as
 * plain constants (not an enum/config server) for Paso 2 — Paso 3 formalizes
 * these into versioned event contracts.
 */
public final class RabbitTopology {

    private RabbitTopology() {
    }

    // MS2 (transfers-service) -> MS1 (accounts-service): commands, direct exchange, one queue, competing consumers.
    public static final String ACCOUNTS_COMMANDS_EXCHANGE = "accounts.commands";
    public static final String ROUTING_KEY_ACCOUNT_DEBIT = "account.debit";
    public static final String ROUTING_KEY_ACCOUNT_CREDIT = "account.credit";

    // MS1 (accounts-service) -> MS2 (transfers-service): result events, topic exchange.
    public static final String ACCOUNTS_EVENTS_EXCHANGE = "accounts.events";
    public static final String TRANSFERS_SERVICE_ACCOUNT_EVENTS_QUEUE = "transfers-service.account-events";
    public static final String ROUTING_KEY_ACCOUNT_EVENTS_PATTERN = "account.*";
    public static final String ROUTING_KEY_ACCOUNT_DEBITED = "account.debited";
    public static final String ROUTING_KEY_ACCOUNT_DEBIT_FAILED = "account.debit-failed";
    public static final String ROUTING_KEY_ACCOUNT_CREDITED = "account.credited";
    public static final String ROUTING_KEY_ACCOUNT_CREDIT_FAILED = "account.credit-failed";

    // MS2 (transfers-service) -> monolith remnant (notifications, audit): domain events, topic exchange,
    // one independent queue per consumer so both modules receive every event (pub/sub fan-out).
    public static final String TRANSFERS_EVENTS_EXCHANGE = "transfers.events";
    public static final String ROUTING_KEY_TRANSFER_COMPLETED = "transfer.completed";
    public static final String ROUTING_KEY_TRANSFER_FAILED = "transfer.failed";

    public static final String CREDIT_PURPOSE_FORWARD = "FORWARD";
    public static final String CREDIT_PURPOSE_COMPENSATION = "COMPENSATION";
}
