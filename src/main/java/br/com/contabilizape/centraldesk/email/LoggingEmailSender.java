package br.com.contabilizape.centraldesk.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Usado quando não há RESEND_API_KEY (desenvolvimento e CI; em produção a aplicação não sobe assim,
 * ver EmailConfig). O e-mail só aparece no log. O corpo fica no nível DEBUG, para não ir parar nos
 * logs por engano; nos e-mails confidenciais (códigos, segurança da conta), o destinatário aparece
 * mascarado. Para ler um código em desenvolvimento:
 * {@code logging.level.br.com.contabilizape.centraldesk.email.LoggingEmailSender=DEBUG}.
 */
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(EmailMessage message) {
        String recipient = message.confidential() ? message.maskedRecipient() : message.to();
        log.info("E-mail não enviado (sem chave do Resend): para={} assunto=\"{}\" chave={}",
                recipient, message.subject(), message.idempotencyKey());
        log.debug("Corpo do e-mail {}:\n{}", message.idempotencyKey(), message.html());
    }
}
