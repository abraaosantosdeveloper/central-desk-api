package br.com.contabilizape.centraldesk.email;

import br.com.contabilizape.centraldesk.event.TicketAssignedEvent;
import br.com.contabilizape.centraldesk.event.TicketClosedEvent;
import br.com.contabilizape.centraldesk.event.TicketCommentedEvent;
import br.com.contabilizape.centraldesk.event.TicketOpenedEvent;
import br.com.contabilizape.centraldesk.event.TicketReassignedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.function.Supplier;

/**
 * E-mails dos chamados: depois do commit e fora da thread da requisição. O usuário não espera o Resend,
 * e uma falha aqui não desfaz a operação (que já foi gravada). Uma falha de envio para uma pessoa não
 * impede os demais (o EmailSender nunca lança).
 */
@Component
public class TicketEmailListener {

    private static final Logger log = LoggerFactory.getLogger(TicketEmailListener.class);

    private final TicketEmailPlanner planner;
    private final EmailSender sender;

    public TicketEmailListener(TicketEmailPlanner planner, EmailSender sender) {
        this.planner = planner;
        this.sender = sender;
    }

    @Async(EmailConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(TicketOpenedEvent event) {
        send("abertura", event.ticketId().toString(), () -> planner.plan(event));
    }

    @Async(EmailConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(TicketAssignedEvent event) {
        send("assumir", event.ticketId().toString(), () -> planner.plan(event));
    }

    @Async(EmailConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(TicketReassignedEvent event) {
        send("reatribuição", event.ticketId().toString(), () -> planner.plan(event));
    }

    @Async(EmailConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(TicketCommentedEvent event) {
        send("comentário", event.ticketId().toString(), () -> planner.plan(event));
    }

    @Async(EmailConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(TicketClosedEvent event) {
        send("encerramento", event.ticketId().toString(), () -> planner.plan(event));
    }

    private void send(String what, String ticketId, Supplier<List<EmailMessage>> plan) {
        try {
            plan.get().forEach(sender::send);
        } catch (RuntimeException e) {
            log.error("E-mails de {} do chamado {} não montados", what, ticketId, e);
        }
    }
}
