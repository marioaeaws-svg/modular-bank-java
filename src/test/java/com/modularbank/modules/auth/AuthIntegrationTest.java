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

        // Also verify login works with same credentials
        var loginBody = Map.of("email", "test@example.com", "password", "Password123!");
        ResponseEntity<Map> loginResponse = rest.postForEntity("/auth/login", loginBody, Map.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResponse.getBody()).containsKey("accessToken");
    }

    @Test
    void loginWithWrongPasswordReturns401() {
        // Register the user first
        rest.postForEntity("/auth/register",
            Map.of("email", "user@example.com", "password", "Password123!", "name", "User"),
            Map.class);

        // Now try with wrong password
        var body = Map.of("email", "user@example.com", "password", "WrongPassword!");
        ResponseEntity<Map> response = rest.postForEntity("/auth/login", body, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
