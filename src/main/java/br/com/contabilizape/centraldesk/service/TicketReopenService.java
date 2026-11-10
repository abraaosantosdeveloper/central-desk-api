package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.dto.TicketResponse;
import br.com.contabilizape.centraldesk.event.TicketReopenedEvent;
import br.com.contabilizape.centraldesk.exception.AccountNotActiveException;
import br.com.contabilizape.centraldesk.exception.InvalidSessionException;
import br.com.contabilizape.centraldesk.exception.TicketNotFoundException;
import br.com.contabilizape.centraldesk.exception.TicketReopenConflictException;
import br.com.contabilizape.centraldesk.exception.TicketReopenForbiddenException;
import br.com.contabilizape.centraldesk.model.Comment;
import br.com.contabilizape.centraldesk.model.Ticket;
import br.com.contabilizape.centraldesk.model.TicketClosure;
import br.com.contabilizape.centraldesk.model.TicketStatus;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.repository.AdminRepository;
import br.com.contabilizape.centraldesk.repository.CommentRepository;
import br.com.contabilizape.centraldesk.repository.TicketClosureRepository;
import br.com.contabilizape.centraldesk.repository.TicketRepository;
import br.com.contabilizape.centraldesk.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Reabertura de chamados (SCRUM-208, PLANO-SEGURANCA §13.2):
 * R1 reabrem o solicitante e os administradores; R2 até 7 dias depois do encerramento; R3 volta para o
 * mesmo responsável se ele ainda pode atender, senão para a fila; R4 motivo obrigatório, que entra na
 * conversa; R5 no máximo 3 reaberturas por chamado.
 */
@Service
public class TicketReopenService {

    static final Duration DEADLINE = Duration.ofDays(7);
    static final int MAX_REOPENINGS = 3;

    private final TicketRepository ticketRepository;
    private final TicketClosureRepository closureRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final UUID handlerDepartmentId;

    public TicketReopenService(TicketRepository ticketRepository, TicketClosureRepository closureRepository,
                               CommentRepository commentRepository, UserRepository userRepository,
                               AdminRepository adminRepository, ApplicationEventPublisher eventPublisher, Clock clock,
                               @Value("${app.tickets.handler-department-id}") UUID handlerDepartmentId) {
        this.ticketRepository = ticketRepository;
        this.closureRepository = closureRepository;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.handlerDepartmentId = handlerDepartmentId;
    }

    /** Quem não vê o chamado recebe 404, como em GET /tickets/{id}. */
    @Transactional
    public TicketResponse reopen(UUID ticketId, UUID userId, List<String> roles, String reason) {
        User actor = userRepository.findById(userId).orElseThrow(InvalidSessionException::new);
        if (!actor.isActive()) {
            throw new AccountNotActiveException();
        }
        Ticket ticket = ticketRepository.findByIdForUpdate(ticketId)
                .filter(found -> TicketAccess.canView(found, userId, roles))
                .orElseThrow(() -> new TicketNotFoundException(ticketId));
        // A condição de admin vem do banco: vale na hora em que o acesso é removido
        if (!ticket.getRequester().getId().equals(userId) && !adminRepository.existsById(userId)) {
            throw new TicketReopenForbiddenException();
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (ticket.getStatus() != TicketStatus.CLOSED) {
            throw new TicketReopenConflictException("Só chamados encerrados podem ser reabertos.");
        }
        if (ticket.getClosedAt() != null && ticket.getClosedAt().plus(DEADLINE).isBefore(now)) {
            throw new TicketReopenConflictException(
                    "O prazo para reabrir (7 dias depois do encerramento) acabou. Abra um chamado novo.");
        }
        if (closureRepository.countByTicketId(ticketId) >= MAX_REOPENINGS) {
            throw new TicketReopenConflictException("Este chamado já foi reaberto 3 vezes. Abra um chamado novo.");
        }

        String trimmed = reason.trim();
        TicketClosure closure = closureRepository.save(new TicketClosure(ticket, actor, now, trimmed));
        ticket.reopen(canStillHandle(ticket));
        commentRepository.save(new Comment(ticket, actor, "Chamado reaberto: " + trimmed));
        ticketRepository.flush(); // grava agora e atualiza o updated_at
        eventPublisher.publishEvent(TicketReopenedEvent.of(ticket, closure, userId));
        return TicketResponse.from(ticket);
    }

    // Mesmas regras de quem pode receber um chamado (TicketReassignmentService): ativo, da Inovação ou admin;
    // o próprio solicitante só se for admin ou coordenador (demanda interna, SCRUM-207)
    private boolean canStillHandle(Ticket ticket) {
        User assignee = ticket.getAssignee();
        if (assignee == null || !assignee.isActive()) {
            return false;
        }
        boolean admin = adminRepository.existsById(assignee.getId());
        boolean coordinator = userRepository.isCoordinator(assignee.getId());
        boolean handler = assignee.getDepartment().getId().equals(handlerDepartmentId);
        boolean requester = assignee.getId().equals(ticket.getRequester().getId());
        return (handler || admin) && (!requester || admin || coordinator);
    }
}
