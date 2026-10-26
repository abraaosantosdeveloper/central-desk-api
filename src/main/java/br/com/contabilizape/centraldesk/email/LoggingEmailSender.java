package br.com.contabilizape.centraldesk.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Usado quando não há RESEND_API_KEY: o e-mail só aparece no log. O corpo (que pode ter o texto de um
 * comentário) fica no nível DEBUG, para não ir parar nos logs de produção por engano.
 */
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(EmailMessage message) {
        log.info("E-mail não enviado (sem chave do Resend): para={} assunto=\"{}\" chave={}",
                message.to(), message.subject(), message.idempotencyKey());
        log.debug("Corpo do e-mail {}:\n{}", message.idempotencyKey(), message.html());
    }
}
