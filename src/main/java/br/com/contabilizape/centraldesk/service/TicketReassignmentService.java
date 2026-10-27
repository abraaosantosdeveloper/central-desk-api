package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.dto.AssigneeResponse;
import br.com.contabilizape.centraldesk.dto.TicketResponse;
import br.com.contabilizape.centraldesk.event.TicketReassignedEvent;
import br.com.contabilizape.centraldesk.exception.AccountNotActiveException;
import br.com.contabilizape.centraldesk.exception.InvalidAssigneeException;
import br.com.contabilizape.centraldesk.exception.InvalidSessionException;
import br.com.contabilizape.centraldesk.exception.ReassignConflictException;
import br.com.contabilizape.centraldesk.exception.ReassignForbiddenException;
import br.com.contabilizape.centraldesk.exception.TicketNotFoundException;
import br.com.contabilizape.centraldesk.model.Ticket;
import br.com.contabilizape.centraldesk.model.TicketStatus;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.model.UserStatus;
import br.com.contabilizape.centraldesk.repository.AdminRepository;
import br.com.contabilizape.centraldesk.repository.TicketRepository;
import br.com.contabilizape.centraldesk.repository.UserRepository;
import br.com.contabilizape.centraldesk.security.Roles;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Reatribuição de chamados (SCRUM-173): o responsável passa o chamado a um colega, ou um admin
 * redistribui. Quem chega a estas rotas (Inovação ou admin) é decidido no SecurityConfig.
 */
@Service
public class TicketReassignmentService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final UUID handlerDepartmentId;

    public TicketReassignmentService(TicketRepository ticketRepository, UserRepository userRepository,
                                     AdminRepository adminRepository, ApplicationEventPublisher eventPublisher,
                                     Clock clock,
                                     @Value("${app.tickets.handler-department-id}") UUID handlerDepartmentId) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.handlerDepartmentId = handlerDepartmentId;
    }

    @Transactional(readOnly = true)
    public List<AssigneeResponse> assignees() {
        return userRepository.findAssignees(handlerDepartmentId, UserStatus.ACTIVE, TicketStatus.IN_PROGRESS);
    }

    @Transactional
    public TicketResponse reassign(UUID ticketId, UUID userId, List<String> roles, UUID newAssigneeId) {
        findActiveUser(userId);
        Ticket ticket = ticketRepository.findByIdForUpdate(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));
        boolean isAssignee = ticket.getAssignee() != null && ticket.getAssignee().getId().equals(userId);
        if (!isAssignee && !roles.contains(Roles.ADMIN)) {
            throw new ReassignForbiddenException();
        }
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new ReassignConflictException("Chamado encerrado não pode ser reatribuído.");
        }
        if (ticket.getAssignee() != null && ticket.getAssignee().getId().equals(newAssigneeId)) {
            throw new ReassignConflictException("Esta pessoa já é a responsável pelo chamado.");
        }

        boolean fromQueue = ticket.isOpen(); // lido antes: reatribuir um chamado da fila o coloca em atendimento
        ticket.reassign(findEligibleAssignee(newAssigneeId, ticket), OffsetDateTime.now(clock));
        ticketRepository.flush(); // grava agora e atualiza o updated_at
        eventPublisher.publishEvent(TicketReassignedEvent.of(ticket, userId, fromQueue));
        return TicketResponse.from(ticket);
    }

    // Destino: conta ativa da Inovação ou admin. Quem abriu o chamado só pode recebê-lo se for admin (SCRUM-207)
    private User findEligibleAssignee(UUID assigneeId, Ticket ticket) {
        User assignee = userRepository.findById(assigneeId)
                .filter(User::isActive)
                .filter(user -> user.getDepartment().getId().equals(handlerDepartmentId)
                        || adminRepository.existsById(user.getId()))
                .orElseThrow(() -> new InvalidAssigneeException(
                        "Escolha uma pessoa ativa da Inovação ou um administrador."));
        if (assignee.getId().equals(ticket.getRequester().getId()) && !adminRepository.existsById(assignee.getId())) {
            throw new InvalidAssigneeException("Quem abriu o chamado não pode atendê-lo.");
        }
        return assignee;
    }

    private User findActiveUser(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(InvalidSessionException::new);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountNotActiveException();
        }
        return user;
    }
}
