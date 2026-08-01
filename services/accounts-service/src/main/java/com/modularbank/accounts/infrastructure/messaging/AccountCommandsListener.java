package com.modularbank.accounts.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modularbank.accounts.domain.Account;
import com.modularbank.accounts.infrastructure.AccountRepository;
import com.modularbank.accounts.infrastructure.messaging.dto.*;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.modularbank.accounts.infrastructure.messaging.EventTypes.*;
import static com.modularbank.accounts.infrastructure.messaging.RabbitTopology.ACCOUNTS_SERVICE_COMMANDS_QUEUE;

/**
 * Replaces the Paso 1 synchronous {@code /internal/accounts/{id}/debit|credit}
 * endpoints: transfers-service (MS2) no longer calls accounts-service (MS1)
 * over HTTP at all, only over this queue. Ownership is enforced here (via
 * {@code userId} on the command) since transfers-service never queries
 * accounts-service synchronously, not even to check who owns an account.
 *
 * Dispatches on {@code envelope.eventType()} (see ADR-010) and is guarded by
 * an idempotency check — see docs/evidencia/paso-3/03-patrones-resiliencia.md.
 * Skipping a duplicate {@code CreditAccountCommand} here specifically is what
 * prevents a redelivered message from crediting the same money twice.
 */
@Component
@RequiredArgsConstructor
public class AccountCommandsListener {

    private static final String CONSUMER = ACCOUNTS_SERVICE_COMMANDS_QUEUE;
    private static final Logger log = LoggerFactory.getLogger(AccountCommandsListener.class);

    private final AccountRepository accountRepository;
    private final AccountEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final ProcessedMessageRepository processedMessageRepository;
    private final Tracer tracer;
    private final Propagator propagator;

    @RabbitListener(queues = ACCOUNTS_SERVICE_COMMANDS_QUEUE)
    @Transactional
    public void handle(EventEnvelope envelope) {
        // Continue the distributed trace across the broker hop (Paso 4, see ADR-009 and
        // EventEnvelope's javadoc for why this is manual instead of relying on Spring AMQP's
        // own message observation).
        Map<String, String> tracingContext = envelope.tracingContext() != null ? envelope.tracingContext() : Map.of();
        Span span = propagator.extract(tracingContext, Map::get)
            .name("rabbit.consume " + CONSUMER)
            .kind(Span.Kind.CONSUMER)
            .start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            // Business context for structured logs (Paso 4, see ADR-009).
            MDC.put("eventId", envelope.eventId());
            MDC.put("eventType", envelope.eventType());
            for (String key : List.of("accountId", "transferId", "userId")) {
                Object value = envelope.data().get(key);
                if (value != null) {
                    MDC.put(key, value.toString());
                }
            }
            try {
                if (processedMessageRepository.existsByEventIdAndConsumer(envelope.eventId(), CONSUMER)) {
                    log.info("Duplicate delivery of {} (eventId={}) for consumer {} — skipping, already processed",
                        envelope.eventType(), envelope.eventId(), CONSUMER);
                    return;
                }

                switch (envelope.eventType()) {
                    case DEBIT_ACCOUNT_COMMAND -> handleDebit(convert(envelope, DebitAccountCommand.class));
                    case CREDIT_ACCOUNT_COMMAND -> handleCredit(convert(envelope, CreditAccountCommand.class));
                    default -> log.warn("Unknown eventType {} on {} — ignoring", envelope.eventType(), CONSUMER);
                }

                processedMessageRepository.save(ProcessedMessage.builder()
                    .eventId(envelope.eventId())
                    .consumer(CONSUMER)
                    .build());
            } finally {
                MDC.remove("eventId");
                MDC.remove("eventType");
                MDC.remove("accountId");
                MDC.remove("transferId");
                MDC.remove("userId");
            }
        } finally {
            span.end();
        }
    }

    private void handleDebit(DebitAccountCommand command) {
        Optional<Account> account = accountRepository.findById(command.accountId());
        if (account.isEmpty()) {
            eventPublisher.publishDebitFailed(new AccountDebitFailedEvent(
                command.transferId(), command.accountId(), command.amount(), "ACCOUNT_NOT_FOUND"));
            return;
        }
        if (!account.get().getUserId().equals(command.userId())) {
            eventPublisher.publishDebitFailed(new AccountDebitFailedEvent(
                command.transferId(), command.accountId(), command.amount(), "NOT_OWNER"));
            return;
        }

        int updated = accountRepository.debitIfSufficient(command.accountId(), command.amount());
        if (updated == 0) {
            eventPublisher.publishDebitFailed(new AccountDebitFailedEvent(
                command.transferId(), command.accountId(), command.amount(), "INSUFFICIENT_FUNDS"));
            return;
        }

        log.info("Debited {} from account {} for transfer {}", command.amount(), command.accountId(), command.transferId());
        eventPublisher.publishDebited(new AccountDebitedEvent(command.transferId(), command.accountId(), command.amount()));
    }

    private void handleCredit(CreditAccountCommand command) {
        if (!accountRepository.existsById(command.accountId())) {
            eventPublisher.publishCreditFailed(new AccountCreditFailedEvent(
                command.transferId(), command.accountId(), command.amount(), "ACCOUNT_NOT_FOUND", command.purpose()));
            return;
        }

        accountRepository.credit(command.accountId(), command.amount());
        log.info("Credited {} to account {} for transfer {} (purpose={})", command.amount(), command.accountId(), command.transferId(), command.purpose());
        eventPublisher.publishCredited(new AccountCreditedEvent(
            command.transferId(), command.accountId(), command.amount(), command.purpose()));
    }

    private <T> T convert(EventEnvelope envelope, Class<T> type) {
        return objectMapper.convertValue(envelope.data(), type);
    }
}
