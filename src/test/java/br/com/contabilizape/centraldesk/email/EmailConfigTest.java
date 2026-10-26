package br.com.contabilizape.centraldesk.email;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Escolha do EmailSender (Resend com chave, log sem chave) e do executor (assíncrono, ou síncrono nos testes).
 */
class EmailConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(EmailConfig.class)
            .withPropertyValues(
                    "app.email.from=CentralDesk <centraldesk@notificacoes.contabilizape.com.br>",
                    "app.email.async=true",
                    "app.email.resend.base-url=https://api.resend.com",
                    "app.email.resend.connect-timeout=3s",
                    "app.email.resend.read-timeout=10s");

    @Test
    void withoutApiKeyEmailsGoToTheLog() {
        runner.withPropertyValues("app.email.resend.api-key=").run(context ->
                assertThat(context.getBean(EmailSender.class)).isInstanceOf(LoggingEmailSender.class));
    }

    @Test
    void withBlankApiKeyEmailsGoToTheLog() {
        runner.withPropertyValues("app.email.resend.api-key=   ").run(context ->
                assertThat(context.getBean(EmailSender.class)).isInstanceOf(LoggingEmailSender.class));
    }

    @Test
    void withApiKeyEmailsGoThroughResend() {
        runner.withPropertyValues("app.email.resend.api-key=re_test_key").run(context ->
                assertThat(context.getBean(EmailSender.class)).isInstanceOf(ResendEmailSender.class));
    }

    @Test
    void sendsInBackgroundThreadsByDefault() {
        runner.withPropertyValues("app.email.resend.api-key=").run(context ->
                assertThat(context.getBean(EmailConfig.EXECUTOR)).isInstanceOf(ThreadPoolTaskExecutor.class));
    }

    @Test
    void sendsInTheSameThreadWhenAsyncIsOff() {
        runner.withPropertyValues("app.email.resend.api-key=", "app.email.async=false").run(context ->
                assertThat(context.getBean(EmailConfig.EXECUTOR)).isInstanceOf(SyncTaskExecutor.class));
    }
}
