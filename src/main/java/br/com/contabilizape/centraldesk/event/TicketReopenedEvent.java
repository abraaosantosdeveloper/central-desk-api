package br.com.contabilizape.centraldesk.event;

import br.com.contabilizape.centraldesk.model.Ticket;
import br.com.contabilizape.centraldesk.model.TicketClosure;

import java.util.UUID;

/**
 * Chamado reaberto. backToQueue = voltou para a fila (o responsável saiu ou não havia): a fila ganhou
 * um item. closureId aponta para o encerramento desfeito (motivo e quem reabriu).
 */
public record TicketReopenedEvent(UUID eventId, UUID ticketId, UUID closureId, UUID actorId, UUID requesterId,
                                  UUID assigneeId, boolean backToQueue) {

    /** Chamar depois de reabrir. */
    public static TicketReopenedEvent of(Ticket ticket, TicketClosure closure, UUID actorId) {
        return new TicketReopenedEvent(UUID.randomUUID(), ticket.getId(), closure.getId(), actorId,
                ticket.getRequester().getId(), ticket.getAssignee() == null ? null : ticket.getAssignee().getId(),
                ticket.isOpen());
    }
}
