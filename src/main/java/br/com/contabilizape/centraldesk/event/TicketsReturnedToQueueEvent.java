package br.com.contabilizape.centraldesk.event;

import br.com.contabilizape.centraldesk.model.Ticket;

import java.util.List;
import java.util.UUID;

/**
 * Chamados que estavam em atendimento voltaram para a fila porque o responsável saiu
 * (conta desativada ou anonimizada, ou saída da Inovação). A fila ganhou todos de uma vez.
 */
public record TicketsReturnedToQueueEvent(UUID eventId, List<UUID> ticketIds) {

    public static TicketsReturnedToQueueEvent of(List<Ticket> tickets) {
        return new TicketsReturnedToQueueEvent(UUID.randomUUID(), tickets.stream().map(Ticket::getId).toList());
    }
}
