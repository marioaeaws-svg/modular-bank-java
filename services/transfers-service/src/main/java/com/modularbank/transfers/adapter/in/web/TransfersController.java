package com.modularbank.transfers.adapter.in.web;

import com.modularbank.transfers.application.TransferSagaOrchestrator;
import com.modularbank.transfers.application.dto.TransferRequest;
import com.modularbank.transfers.domain.Transfer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

/**
 * Unlike Paso 1's synchronous contract, {@code POST /transfers} now returns
 * {@code 202 Accepted} with the transfer in PENDING status — the saga runs
 * asynchronously over RabbitMQ against accounts-service, so there is no
 * final result to return synchronously. Clients poll {@code GET /transfers/{id}}
 * until status is a terminal one (COMPLETED, FAILED, REVERSED). This is a
 * deliberate HTTP contract change from Paso 1, not an oversight — see
 * docs/evidencia/paso-2/.
 */
@RestController
@RequestMapping("/transfers")
@RequiredArgsConstructor
public class TransfersController {

    private final TransferSagaOrchestrator orchestrator;

    @PostMapping
    public ResponseEntity<Transfer> initiate(@RequestBody @Valid TransferRequest request, Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        Transfer transfer = orchestrator.initiate(userId, request);
        // Business context for structured logs (Paso 4, see ADR-009) — the transferId now
        // shows up in every subsequent log line for this request, and (via the AMQP
        // observation propagating this span's traceId into the DebitAccountCommand) in
        // accounts-service's logs for the same operation too.
        MDC.put("transferId", transfer.getId().toString());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(transfer);
    }

    @GetMapping
    public ResponseEntity<List<Transfer>> getHistory(@RequestParam UUID accountId, Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(orchestrator.getHistory(userId, accountId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Transfer> getById(@PathVariable UUID id, Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(orchestrator.getById(userId, id));
    }
}
