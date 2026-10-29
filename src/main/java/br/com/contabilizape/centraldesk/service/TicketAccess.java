package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.model.Department;
import br.com.contabilizape.centraldesk.model.Ticket;
import br.com.contabilizape.centraldesk.security.Roles;

import java.util.List;
import java.util.UUID;

/**
 * Quem pode ver (e, por isso, comentar e encerrar) um chamado: o solicitante,
 * a Inovação (TICKET_HANDLER) e os administradores. Os demais recebem 404.
 *
 * Coordenadores do setor de origem ou de destino também leem o chamado e a conversa ("Todos os chamados",
 * SCRUM-219), mas só acompanham: {@link #inSector} não dá direito de comentar, encerrar nem reabrir.
 */
final class TicketAccess {

    private TicketAccess() {
    }

    static boolean canView(Ticket ticket, UUID userId, List<String> roles) {
        return ticket.getRequester().getId().equals(userId)
                || roles.contains(Roles.TICKET_HANDLER)
                || roles.contains(Roles.ADMIN);
    }

    /** O chamado foi aberto pelo setor (origem) ou enviado a ele (destino). */
    static boolean inSector(Ticket ticket, UUID departmentId) {
        return departmentId != null
                && (sameDepartment(ticket.getOriginDepartment(), departmentId)
                    || sameDepartment(ticket.getTargetDepartment(), departmentId));
    }

    private static boolean sameDepartment(Department department, UUID departmentId) {
        return department != null && departmentId.equals(department.getId());
    }
}
