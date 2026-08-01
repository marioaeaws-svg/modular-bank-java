package com.modularbank.accounts.infrastructure.messaging;

/**
 * accounts-service's own copy of the shared exchange/routing-key names (see
 * transfers-service's identically-named class) — each service declares only
 * the topology it needs, no shared schema module. Paso 3 formalizes this
 * into a versioned event contract; for Paso 2 the string constants are the
 * contract.
 */
public final class RabbitTopology {

    private RabbitTopology() {
    }

    public static final String ACCOUNTS_COMMANDS_EXCHANGE = "accounts.commands";
    public static final String ACCOUNTS_SERVICE_COMMANDS_QUEUE = "accounts-service.commands";
    public static final String ROUTING_KEY_ACCOUNT_DEBIT = "account.debit";
    public static final String ROUTING_KEY_ACCOUNT_CREDIT = "account.credit";

    public static final String ACCOUNTS_EVENTS_EXCHANGE = "accounts.events";
    public static final String ROUTING_KEY_ACCOUNT_DEBITED = "account.debited";
    public static final String ROUTING_KEY_ACCOUNT_DEBIT_FAILED = "account.debit-failed";
    public static final String ROUTING_KEY_ACCOUNT_CREDITED = "account.credited";
    public static final String ROUTING_KEY_ACCOUNT_CREDIT_FAILED = "account.credit-failed";
}
