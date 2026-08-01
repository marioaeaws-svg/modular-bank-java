package com.modularbank.transfers.application.port.out;

import com.modularbank.transfers.adapter.out.messaging.dto.CreditAccountCommand;
import com.modularbank.transfers.adapter.out.messaging.dto.DebitAccountCommand;

/** Outbound port: how the saga orchestrator asks accounts-service (MS1) to move money. */
public interface AccountCommandPublisher {
    void publishDebit(DebitAccountCommand command);
    void publishCredit(CreditAccountCommand command);
}
