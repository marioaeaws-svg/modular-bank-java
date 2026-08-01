package com.modularbank.accounts;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class AccountsIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void createAccountAndGetBalance() {
        String token = TestJwtSupport.tokenFor(UUID.randomUUID());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> createResponse = rest.exchange(
            "/accounts", HttpMethod.POST,
            new HttpEntity<>(Map.of(), headers), Map.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String accountId = (String) createResponse.getBody().get("id");
        ResponseEntity<Map> balanceResponse = rest.exchange(
            "/accounts/" + accountId + "/balance", HttpMethod.GET,
            new HttpEntity<>(headers), Map.class);
        assertThat(balanceResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(balanceResponse.getBody().get("amount")).isEqualTo("0.0000");
    }

    @Test
    void listAccountsReturnsOwnedAccounts() {
        String token = TestJwtSupport.tokenFor(UUID.randomUUID());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        rest.exchange("/accounts", HttpMethod.POST, new HttpEntity<>(Map.of(), headers), Map.class);
        rest.exchange("/accounts", HttpMethod.POST, new HttpEntity<>(Map.of(), headers), Map.class);

        ResponseEntity<List> response = rest.exchange(
            "/accounts", HttpMethod.GET, new HttpEntity<>(headers), List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
    }

    // Paso 1's synchronous /internal/accounts/{id}/debit|credit endpoints are gone —
    // debit/credit are now driven asynchronously over RabbitMQ (see
    // AccountCommandsListenerTest and infrastructure.messaging.AccountCommandsListener).
}
