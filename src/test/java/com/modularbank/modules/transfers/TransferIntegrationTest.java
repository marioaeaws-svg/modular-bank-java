package com.modularbank.modules.transfers;

import com.modularbank.shared.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class TransferIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    private String tokenA;
    private String tokenB;
    private String accountAId;
    private String accountBId;

    @BeforeEach
    void setUp() {
        rest.postForEntity("/auth/register",
            Map.of("email", "alice@example.com", "password", "Password123!", "name", "Alice"), Map.class);
        rest.postForEntity("/auth/register",
            Map.of("email", "bob@example.com", "password", "Password123!", "name", "Bob"), Map.class);

        tokenA = (String) rest.postForEntity("/auth/login",
            Map.of("email", "alice@example.com", "password", "Password123!"), Map.class)
            .getBody().get("accessToken");
        tokenB = (String) rest.postForEntity("/auth/login",
            Map.of("email", "bob@example.com", "password", "Password123!"), Map.class)
            .getBody().get("accessToken");

        HttpHeaders headersA = new HttpHeaders();
        headersA.setBearerAuth(tokenA);
        accountAId = (String) rest.exchange("/accounts", HttpMethod.POST,
            new HttpEntity<>(Map.of(), headersA), Map.class).getBody().get("id");

        HttpHeaders headersB = new HttpHeaders();
        headersB.setBearerAuth(tokenB);
        accountBId = (String) rest.exchange("/accounts", HttpMethod.POST,
            new HttpEntity<>(Map.of(), headersB), Map.class).getBody().get("id");
    }

    @Test
    void transferBetweenAccountsInsufficientFunds() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = rest.exchange("/transfers", HttpMethod.POST,
            new HttpEntity<>(Map.of(
                "sourceAccountId", accountAId,
                "targetAccountId", accountBId,
                "amount", "100.00"
            ), headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
