package br.com.contabilizape.centraldesk.event;

import br.com.contabilizape.centraldesk.model.Ticket;

import java.util.UUID;

/**
 * Troca de responsável. fromQueue = o chamado estava OPEN: para a fila, equivale a assumir
 * (o contador cai); em atendimento, a fila não muda.
 */
public record TicketReassignedEvent(UUID eventId, UUID ticketId, UUID actorId, UUID newAssigneeId,
                                    UUID requesterId, boolean fromQueue) {

    /** Chamar depois de reatribuir: o novo responsável já está no chamado. */
    public static TicketReassignedEvent of(Ticket ticket, UUID actorId, boolean fromQueue) {
        return new TicketReassignedEvent(UUID.randomUUID(), ticket.getId(), actorId, ticket.getAssignee().getId(),
                ticket.getRequester().getId(), fromQueue);
    }
}
