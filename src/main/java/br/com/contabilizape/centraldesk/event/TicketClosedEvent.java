package br.com.contabilizape.centraldesk.event;

import br.com.contabilizape.centraldesk.model.Ticket;

import java.util.UUID;

/**
 * Encerramento. closedById pode ser um admin que não era o responsável. wasOpen = o chamado ainda
 * estava na fila (sem responsável): só nesse caso o contador da fila muda.
 */
public record TicketClosedEvent(UUID eventId, UUID ticketId, UUID closedById, UUID requesterId, boolean wasOpen) {

    /** Chamar depois de encerrar; wasOpen é lido antes, porque o encerramento muda o status. */
    public static TicketClosedEvent of(Ticket ticket, boolean wasOpen) {
        return new TicketClosedEvent(UUID.randomUUID(), ticket.getId(), ticket.getClosedBy().getId(),
                ticket.getRequester().getId(), wasOpen);
    }
}
