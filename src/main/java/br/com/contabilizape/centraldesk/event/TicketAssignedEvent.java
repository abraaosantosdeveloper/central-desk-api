package br.com.contabilizape.centraldesk.event;

import br.com.contabilizape.centraldesk.model.Ticket;

import java.util.UUID;

/** Alguém assumiu o chamado (OPEN → IN_PROGRESS): ele saiu da fila. */
public record TicketAssignedEvent(UUID eventId, UUID ticketId, UUID assigneeId, UUID requesterId) {

    public static TicketAssignedEvent of(Ticket ticket) {
        return new TicketAssignedEvent(UUID.randomUUID(), ticket.getId(), ticket.getAssignee().getId(),
                ticket.getRequester().getId());
    }
}
