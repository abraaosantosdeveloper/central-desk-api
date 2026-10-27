package br.com.contabilizape.centraldesk.email;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Entrega os e-mails de conta na fila prioritária, separada dos e-mails de chamado: um código de
 * acesso não espera atrás de um lote de avisos (PLANO-SEGURANCA §10).
 */
@Component
public class EmailReadyListener {

    private final EmailSender sender;

    public EmailReadyListener(EmailSender sender) {
        this.sender = sender;
    }

    @Async(EmailConfig.PRIORITY_EXECUTOR)
    @TransactionalEventListener(fallbackExecution = true)
    public void on(EmailReadyEvent event) {
        sender.send(event.message());
    }
}
