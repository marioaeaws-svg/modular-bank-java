package com.modularbank.shared;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class BaseIntegrationTest {

    // Singleton pattern: one container for the entire test suite JVM lifetime.
    // Prevents container restart between test classes, which would leave the
    // cached Spring context pointing to a dead datasource/broker connection.
    static final PostgreSQLContainer<?> postgres;
    // Needed since Paso 2: the monolith remnant now has @RabbitListener beans
    // (notifications/audit reacting to transfer.* events), so the context
    // fails to start without a broker to connect to.
    static final RabbitMQContainer rabbitmq;

    static {
        postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("modular_bank")
            .withUsername("bank")
            .withPassword("bank");
        postgres.start();

        rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management");
        rabbitmq.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitmq::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitmq::getAdminPassword);
    }
}
