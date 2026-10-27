package br.com.contabilizape.centraldesk.event;

import br.com.contabilizape.centraldesk.model.Ticket;

import java.util.UUID;

/**
 * Troca de responsável. fromQueue = o chamado estava OPEN: para a fila, equivale a assumir
 * (o contador cai); em atendimento, a fila não muda. previousAssigneeId é nulo quando veio da fila
 * (o responsável anterior também recebe e-mail, PLANO-SEGURANCA §13.1).
 */
public record TicketReassignedEvent(UUID eventId, UUID ticketId, UUID actorId, UUID newAssigneeId,
                                    UUID requesterId, boolean fromQueue, UUID previousAssigneeId) {

    /** Chamar depois de reatribuir: o novo responsável já está no chamado; o anterior é lido antes. */
    public static TicketReassignedEvent of(Ticket ticket, UUID actorId, boolean fromQueue, UUID previousAssigneeId) {
        return new TicketReassignedEvent(UUID.randomUUID(), ticket.getId(), actorId, ticket.getAssignee().getId(),
                ticket.getRequester().getId(), fromQueue, previousAssigneeId);
    }
}
