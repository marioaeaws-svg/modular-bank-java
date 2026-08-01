package com.modularbank.transfers.shared;

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
 * so "Try it out" in Swagger UI exercises the Paso 2 routing
 * (Gateway -> transfers-service) instead of bypassing it.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI transfersServiceOpenApi(@Value("${gateway.url:http://localhost:8080}") String gatewayUrl) {
        return new OpenAPI()
            .info(new Info()
                .title("FinBank — transfers-service")
                .version("v1")
                .description("Microservicio de transferencias extraído del monolito modular en el Paso 2 (segunda extracción). "
                    + "Orquesta el débito/crédito contra accounts-service de forma asíncrona vía RabbitMQ (Saga orquestada)."))
            .addServersItem(new Server().url(gatewayUrl).description("A través del API Gateway"))
            .components(new Components().addSecuritySchemes("bearerAuth",
                new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
