package com.modularbank.modules.transfers.api;

import com.modularbank.modules.transfers.application.TransferUseCase;
import com.modularbank.modules.transfers.application.dto.TransferRequest;
import com.modularbank.modules.transfers.domain.Transfer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/transfers")
@RequiredArgsConstructor
public class TransfersController {

    private final TransferUseCase transferUseCase;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Transfer executeTransfer(@RequestBody @Valid TransferRequest request,
                                    Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        return transferUseCase.execute(userId, request);
    }

    @GetMapping
    public List<Transfer> getHistory(@RequestParam UUID accountId) {
        return transferUseCase.getHistory(accountId);
    }
}
