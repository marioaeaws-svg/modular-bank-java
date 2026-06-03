package com.modularbank.modules.accounts;

import com.modularbank.shared.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class AccountsIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    private String loginAndGetToken() {
        rest.postForEntity("/auth/register",
            Map.of("email", "acc@example.com", "password", "Password123!", "name", "Acc User"), Map.class);
        var response = rest.postForEntity("/auth/login",
            Map.of("email", "acc@example.com", "password", "Password123!"), Map.class);
        return (String) response.getBody().get("accessToken");
    }

    @Test
    void createAccountAndGetBalance() {
        String token = loginAndGetToken();
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
}
