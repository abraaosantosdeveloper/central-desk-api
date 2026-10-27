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
        "API_DOCS_ENABLED=false",
        // Nenhum teste fala com o Resend de verdade (o .env local pode ter a chave); e-mails saem na mesma thread
        "app.email.resend.api-key=", "app.email.async=false",
        "app.security.jwt.secret=" + IntegrationTest.JWT_SECRET,
        "app.security.content-encryption-key=" + IntegrationTest.CONTENT_ENCRYPTION_KEY,
        "app.security.otp-hmac-key=" + IntegrationTest.OTP_HMAC_KEY,
        "app.security.cookie.secure=true"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public @interface IntegrationTest {

    /** Chave dos tokens nos testes (base64 de 33 bytes). A real fica só no .env (JWT_SECRET). */
    String JWT_SECRET = "Y2VudHJhbGRlc2stdGVzdC1zZWNyZXQtMzItYnl0ZXMh";

    /** Chave dos comentários nos testes (base64 de 32 bytes). A real fica só no .env (CONTENT_ENCRYPTION_KEY). */
    String CONTENT_ENCRYPTION_KEY = "Y2hhdmUtZGUtdGVzdGUtZG9zLWNvbWVudGFyaW9zISE=";

    /** Chave dos códigos por e-mail nos testes (base64 de 32 bytes). A real fica só no .env (OTP_HMAC_KEY). */
    String OTP_HMAC_KEY = "Y2hhdmUtZGUtdGVzdGUtZG9zLWNvZGlnb3MtMzJieXQ=";
}
