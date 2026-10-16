package br.com.contabilizape.centraldesk;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Configuração padrão dos testes de integração: aplicação inteira, MockMvc e Postgres no Docker.
 *
 * Todas as classes anotadas com ela têm as mesmas propriedades, então o Spring reaproveita
 * um único contexto (e um único contêiner) entre elas. Para mudar uma propriedade numa classe,
 * use @TestPropertySource, que tem precedência sobre as daqui (mas cria outro contexto).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        // O application.properties importa o .env local; os testes não podem depender dele
        "app.bootstrap.admin.name=", "app.bootstrap.admin.cpf=",
        "app.bootstrap.admin.email=", "app.bootstrap.admin.password=",
        "API_DOCS_ENABLED=false"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public @interface IntegrationTest {
}
