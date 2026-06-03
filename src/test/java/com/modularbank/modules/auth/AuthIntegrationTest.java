package com.modularbank.modules.auth;

import com.modularbank.shared.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class AuthIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void registerAndLoginSuccessfully() {
        var registerBody = Map.of(
            "email", "test@example.com",
            "password", "Password123!",
            "name", "Test User"
        );
        ResponseEntity<Map> registerResponse = rest.postForEntity("/auth/register", registerBody, Map.class);
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registerResponse.getBody()).containsKey("accessToken");
        assertThat(registerResponse.getBody()).containsKey("refreshToken");
    }

    @Test
    void loginWithWrongPasswordReturns401() {
        var body = Map.of("email", "nobody@example.com", "password", "wrong");
        ResponseEntity<Map> response = rest.postForEntity("/auth/login", body, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
