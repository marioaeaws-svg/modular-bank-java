package com.modularbank.shared;

import com.auth0.jwt.JWT;
import java.util.UUID;

public final class TestJwtSupport {

    private TestJwtSupport() {
    }

    public static UUID subjectOf(String accessToken) {
        return UUID.fromString(JWT.decode(accessToken).getSubject());
    }
}
