package br.com.contabilizape.centraldesk;

import br.com.contabilizape.centraldesk.email.EmailSender;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Nos testes de integração, os e-mails ficam numa lista ({@link RecordingEmailSender}) em vez de ir para o log. */
@TestConfiguration(proxyBeanMethods = false)
public class TestEmailConfiguration {

    @Bean
    @Primary
    RecordingEmailSender recordingEmailSender() {
        return new RecordingEmailSender();
    }
}
