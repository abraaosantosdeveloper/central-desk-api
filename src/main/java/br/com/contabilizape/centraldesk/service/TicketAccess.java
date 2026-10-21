package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.model.Ticket;
import br.com.contabilizape.centraldesk.security.Roles;

import java.util.List;
import java.util.UUID;

/**
 * Quem pode ver (e, por isso, comentar e encerrar) um chamado: o solicitante,
 * a Inovação (TICKET_HANDLER) e os administradores. Os demais recebem 404.
 */
final class TicketAccess {

    private TicketAccess() {
    }

    static boolean canView(Ticket ticket, UUID userId, List<String> roles) {
        return ticket.getRequester().getId().equals(userId)
                || roles.contains(Roles.TICKET_HANDLER)
                || roles.contains(Roles.ADMIN);
    }
}
