package br.com.contabilizape.centraldesk.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Envio pela API do Resend: cabeçalhos, corpo e política de novas tentativas.
 * O sender nunca lança: a operação que originou o e-mail já foi gravada.
 */
class ResendEmailSenderTest {

    private static final String URL = "https://api.resend.com/emails";
    private static final String FROM = "CentralDesk <centraldesk@notificacoes.contabilizape.com.br>";
    private static final String KEY = "ticket-closed/9b1c/4f0e";

    private MockRestServiceServer server;
    private ResendEmailSender sender;
    private final List<Duration> sleeps = new ArrayList<>();

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.resend.com");
        server = MockRestServiceServer.bindTo(builder).build();
        sender = new ResendEmailSender(builder.build(), "re_test_key", FROM, sleeps::add);
    }

    private EmailMessage message() {
        return new EmailMessage("maria.souza@contabilizape.com.br", "Chamado Encerrado",
                "<p>Olá</p>", KEY, Map.of("type", "ticket_closed"));
    }

    private static org.springframework.test.web.client.ResponseCreator ok() {
        return withSuccess("{\"id\":\"49a3999c\"}", MediaType.APPLICATION_JSON);
    }

    @Test
    void sendsTheEmailWithAuthorizationIdempotencyAndThreadingHeaders() {
        server.expect(requestTo(URL))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer re_test_key"))
                .andExpect(header("Idempotency-Key", KEY))
                .andExpect(jsonPath("$.from").value(FROM))
                .andExpect(jsonPath("$.to[0]").value("maria.souza@contabilizape.com.br"))
                .andExpect(jsonPath("$.subject").value("Chamado Encerrado"))
                .andExpect(jsonPath("$.html").value("<p>Olá</p>"))
                .andExpect(jsonPath("$.headers['X-Entity-Ref-ID']").value(KEY))
                .andExpect(jsonPath("$.tags[0].name").value("type"))
                .andExpect(jsonPath("$.tags[0].value").value("ticket_closed"))
                .andRespond(ok());

        sender.send(message());

        server.verify();
        assertThat(sleeps).isEmpty();
    }

    @Test
    void sendsExactlyOneRecipientPerRequest() {
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.to.length()").value(1))
                .andRespond(ok());

        sender.send(message());

        server.verify();
    }

    @Test
    void waitsForRetryAfterOnTooManyRequestsAndTriesAgain() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "2"));
        server.expect(requestTo(URL)).andRespond(ok());

        sender.send(message());

        server.verify();
        assertThat(sleeps).containsExactly(Duration.ofSeconds(2));
    }

    @Test
    void limitsTheRetryAfterWaitSoTheThreadIsNotHeldForLong() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "120"));
        server.expect(requestTo(URL)).andRespond(ok());

        sender.send(message());

        assertThat(sleeps).containsExactly(Duration.ofSeconds(10));
    }

    @Test
    void retriesServerErrorsWithGrowingWaitAndGivesUpAfterThreeAttempts() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatCode(() -> sender.send(message())).doesNotThrowAnyException();

        server.verify();
        assertThat(sleeps).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2));
    }

    @Test
    void retriesWhenAnotherRequestWithTheSameKeyIsStillInProgress() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\":\"concurrent_idempotent_requests\",\"message\":\"em andamento\"}"));
        server.expect(requestTo(URL)).andRespond(ok());

        sender.send(message());

        server.verify();
        assertThat(sleeps).hasSize(1);
    }

    @Test
    void doesNotRetrySameKeyWithDifferentContent() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\":\"invalid_idempotent_request\",\"message\":\"conteúdo diferente\"}"));

        assertThatCode(() -> sender.send(message())).doesNotThrowAnyException();

        server.verify();
        assertThat(sleeps).isEmpty();
    }

    @Test
    void doesNotRetryClientErrors() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\":\"validation_error\",\"message\":\"domínio não verificado\"}"));

        assertThatCode(() -> sender.send(message())).doesNotThrowAnyException();

        server.verify();
        assertThat(sleeps).isEmpty();
    }

    @Test
    void retriesNetworkFailuresAndNeverThrows() {
        server.expect(requestTo(URL)).andRespond(withException(new IOException("timeout")));
        server.expect(requestTo(URL)).andRespond(withException(new IOException("timeout")));
        server.expect(requestTo(URL)).andRespond(withException(new IOException("timeout")));

        assertThatCode(() -> sender.send(message())).doesNotThrowAnyException();

        server.verify();
        assertThat(sleeps).hasSize(2);
    }

    @Test
    void stopsRetryingWhenTheThreadIsInterrupted() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.resend.com");
        MockRestServiceServer interrupted = MockRestServiceServer.bindTo(builder).build();
        ResendEmailSender interruptible = new ResendEmailSender(builder.build(), "re_test_key", FROM, wait -> {
            throw new InterruptedException();
        });
        interrupted.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatCode(() -> interruptible.send(message())).doesNotThrowAnyException();

        interrupted.verify(); // uma só tentativa: a espera foi interrompida
        assertThat(Thread.interrupted()).isTrue(); // o sinal é devolvido à thread (e limpo aqui)
    }
}
