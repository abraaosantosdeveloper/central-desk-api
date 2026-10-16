package br.com.contabilizape.centraldesk;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Sobe um PostgreSQL descartável no Docker para os testes de integração.
 * O @ServiceConnection faz o Spring usar esse banco no lugar do configurado
 * no application.properties; o Flyway aplica as migrations nele ao subir.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        // Mesma imagem do docker-compose.yaml
        return new PostgreSQLContainer("postgres:16-alpine");
    }
}
