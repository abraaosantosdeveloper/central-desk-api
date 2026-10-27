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
 * Testes de integração da segurança das contas (bloqueio, códigos, TFA, verificação do e-mail) com a
 * política de produção (StrictAccountSecurityPolicy). Mesmas propriedades do {@link IntegrationTest}, mas
 * outro contexto (e outro contêiner do Postgres), compartilhado entre as classes com esta anotação.
 *
 * O limite por IP fica em memória e vale para o contexto inteiro: limpe-o no @BeforeEach
 * (LoginAttemptLimiter.clear()).
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
@Import({TestcontainersConfiguration.class, TestEmailConfiguration.class})
public @interface SecurityIntegrationTest {
}
