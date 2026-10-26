package br.com.contabilizape.centraldesk.event;

import br.com.contabilizape.centraldesk.model.Ticket;

import java.util.UUID;

/**
 * Chamado aberto: a fila ganhou um item. O setor de destino vira campo quando a SCRUM-148 entrar.
 * O eventId identifica este fato (base da chave de idempotência dos e-mails, ver ARQUITETURA-EMAILS.md §6).
 */
public record TicketOpenedEvent(UUID eventId, UUID ticketId, UUID requesterId) {

    public static TicketOpenedEvent of(Ticket ticket) {
        return new TicketOpenedEvent(UUID.randomUUID(), ticket.getId(), ticket.getRequester().getId());
    }
}
