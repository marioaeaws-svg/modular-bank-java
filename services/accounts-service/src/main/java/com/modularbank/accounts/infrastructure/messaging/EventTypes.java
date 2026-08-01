package com.modularbank.accounts.infrastructure.messaging;

/** eventType string constants this service produces or consumes. See ADR-010 and docs/contracts/events/v1/. */
public final class EventTypes {

    private EventTypes() {
    }

    public static final String DEBIT_ACCOUNT_COMMAND = "DebitAccountCommand";
    public static final String CREDIT_ACCOUNT_COMMAND = "CreditAccountCommand";
    public static final String ACCOUNT_DEBITED_EVENT = "AccountDebitedEvent";
    public static final String ACCOUNT_DEBIT_FAILED_EVENT = "AccountDebitFailedEvent";
    public static final String ACCOUNT_CREDITED_EVENT = "AccountCreditedEvent";
    public static final String ACCOUNT_CREDIT_FAILED_EVENT = "AccountCreditFailedEvent";

    public static final String EVENT_VERSION_1_0 = "1.0";
    public static final String PRODUCER = "accounts-service";
}
