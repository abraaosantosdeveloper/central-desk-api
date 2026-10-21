package br.com.contabilizape.centraldesk.realtime;

import br.com.contabilizape.centraldesk.dto.TicketSummaryResponse;

/** Chamado aberto. Vai para a Inovação e os administradores conectados (a fila deles mudou). */
public record TicketCreatedEvent(TicketSummaryResponse ticket) {
}
