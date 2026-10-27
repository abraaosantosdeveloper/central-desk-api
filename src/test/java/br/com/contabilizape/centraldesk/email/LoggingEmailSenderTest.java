package br.com.contabilizape.centraldesk.email;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Substituto do Resend em desenvolvimento e na CI: o e-mail só aparece no log.
 */
@ExtendWith(OutputCaptureExtension.class)
class LoggingEmailSenderTest {

    @Test
    void logsRecipientSubjectAndKeyWithoutThrowing(CapturedOutput output) {
        EmailMessage message = new EmailMessage("maria.souza@contabilizape.com.br", "Chamado Aberto",
                "<p>corpo secreto do comentário</p>", "ticket-opened/1/2", Map.of());

        assertThatCode(() -> new LoggingEmailSender().send(message)).doesNotThrowAnyException();

        assertThat(output).contains("maria.souza@contabilizape.com.br", "Chamado Aberto", "ticket-opened/1/2");
    }

    @Test
    void doesNotPrintTheBodyAtInfoLevel(CapturedOutput output) {
        EmailMessage message = new EmailMessage("a@contabilizape.com.br", "Assunto",
                "<p>corpo secreto do comentário</p>", "k", Map.of());

        new LoggingEmailSender().send(message);

        assertThat(output).doesNotContain("corpo secreto do comentário");
    }

    @Test
    void confidentialEmailHidesTheBodyAndMasksTheRecipient(CapturedOutput output) {
        EmailMessage message = new EmailMessage("maria.souza@contabilizape.com.br", "Seu código de acesso",
                "<p>Seu código: 042917</p>", "login-code/1/2", Map.of(), true);

        new LoggingEmailSender().send(message);

        assertThat(output).contains("Seu código de acesso", "m***@contabilizape.com.br");
        assertThat(output).doesNotContain("042917").doesNotContain("maria.souza@");
    }
}
