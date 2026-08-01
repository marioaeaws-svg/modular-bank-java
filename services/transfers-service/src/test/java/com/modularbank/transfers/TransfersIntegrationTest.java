package com.modularbank.transfers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * There is no accounts-service consumer in this test context, so these cases
 * only verify the synchronous half of the contract (the transfer is accepted
 * and created PENDING, and only its owner can read it back) — the full async
 * round-trip to COMPLETED is exercised against the real system in
 * docs/evidencia/paso-2/.
 */
class TransfersIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void initiateTransferReturnsAcceptedWithPendingStatus() {
        String token = TestJwtSupport.tokenFor(UUID.randomUUID());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = rest.exchange("/transfers", HttpMethod.POST,
            new HttpEntity<>(Map.of(
                "sourceAccountId", UUID.randomUUID().toString(),
                "targetAccountId", UUID.randomUUID().toString(),
                "amount", "250.00"
            ), headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().get("status")).isEqualTo("PENDING");
    }

    @Test
    void selfTransferReturns422() {
        String token = TestJwtSupport.tokenFor(UUID.randomUUID());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String accountId = UUID.randomUUID().toString();

        ResponseEntity<Map> response = rest.exchange("/transfers", HttpMethod.POST,
            new HttpEntity<>(Map.of(
                "sourceAccountId", accountId,
                "targetAccountId", accountId,
                "amount", "10.00"
            ), headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void anotherUserCannotReadSomeoneElsesTransfer() {
        String tokenA = TestJwtSupport.tokenFor(UUID.randomUUID());
        String tokenB = TestJwtSupport.tokenFor(UUID.randomUUID());
        HttpHeaders headersA = new HttpHeaders();
        headersA.setBearerAuth(tokenA);
        headersA.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> created = rest.exchange("/transfers", HttpMethod.POST,
            new HttpEntity<>(Map.of(
                "sourceAccountId", UUID.randomUUID().toString(),
                "targetAccountId", UUID.randomUUID().toString(),
                "amount", "50.00"
            ), headersA), Map.class);
        String transferId = (String) created.getBody().get("id");

        HttpHeaders headersB = new HttpHeaders();
        headersB.setBearerAuth(tokenB);
        ResponseEntity<Map> response = rest.exchange("/transfers/" + transferId, HttpMethod.GET,
            new HttpEntity<>(headersB), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
