package com.modularbank.shared.infrastructure;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The "server" is set to the Gateway, not to this service's own host:port —
 * so "Try it out" in Swagger UI exercises the current routing
 * (Gateway -> monolito remanente) instead of bypassing it.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI monolithOpenApi(@Value("${gateway.url:http://localhost:8080}") String gatewayUrl) {
        return new OpenAPI()
            .info(new Info()
                .title("FinBank — monolito remanente")
                .version("v1")
                .description("auth, notifications, audit — accounts (Paso 1) y transfers (Paso 2) fueron extraídos a microservicios; "
                    + "notifications y audit ahora reaccionan a transfers.events vía RabbitMQ en vez de ser invocados in-process."))
            .addServersItem(new Server().url(gatewayUrl).description("A través del API Gateway"))
            .components(new Components().addSecuritySchemes("bearerAuth",
                new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
