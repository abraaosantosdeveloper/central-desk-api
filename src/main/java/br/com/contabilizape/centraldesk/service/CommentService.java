package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.dto.CommentCreateRequest;
import br.com.contabilizape.centraldesk.dto.CommentResponse;
import br.com.contabilizape.centraldesk.event.TicketCommentedEvent;
import br.com.contabilizape.centraldesk.exception.AccountNotActiveException;
import br.com.contabilizape.centraldesk.exception.InvalidSessionException;
import br.com.contabilizape.centraldesk.exception.CommentForbiddenException;
import br.com.contabilizape.centraldesk.exception.TicketClosedForCommentsException;
import br.com.contabilizape.centraldesk.exception.TicketNotFoundException;
import br.com.contabilizape.centraldesk.model.Comment;
import br.com.contabilizape.centraldesk.model.Ticket;
import br.com.contabilizape.centraldesk.model.TicketStatus;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.realtime.CommentCreatedEvent;
import br.com.contabilizape.centraldesk.repository.CommentRepository;
import br.com.contabilizape.centraldesk.repository.TicketRepository;
import br.com.contabilizape.centraldesk.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Conversa do chamado. Leem as mesmas pessoas que veem o chamado (TicketAccess), inclusive os coordenadores
 * do setor de origem ou de destino; comentam só o solicitante e o responsável. A cifra do conteúdo é feita
 * no EncryptedStringConverter, fora daqui.
 */
@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public CommentService(CommentRepository commentRepository, TicketRepository ticketRepository,
                          UserRepository userRepository, ApplicationEventPublisher eventPublisher) {
        this.commentRepository = commentRepository;
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public CommentResponse create(UUID ticketId, UUID userId, List<String> roles, CommentCreateRequest request) {
        User author = findActiveUser(userId);
        Ticket ticket = findVisibleTicket(ticketId, author, roles);
        boolean participant = ticket.getRequester().getId().equals(userId)
                || (ticket.getAssignee() != null && ticket.getAssignee().getId().equals(userId));
        if (!participant) {
            throw new CommentForbiddenException();
        }
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new TicketClosedForCommentsException();
        }

        Comment comment = new Comment(ticket, author, request.content().trim());
        commentRepository.saveAndFlush(comment); // flush preenche o createdAt antes de montar a resposta
        CommentResponse response = CommentResponse.from(comment);

        eventPublisher.publishEvent(new CommentCreatedEvent(response, participants(ticket, author)));
        // Fato de domínio, para o e-mail ao outro lado (o destinatário é decidido agora, não depois)
        eventPublisher.publishEvent(TicketCommentedEvent.of(ticket, comment));
        return response;
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> list(UUID ticketId, UUID userId, List<String> roles) {
        User reader = findActiveUser(userId);
        findVisibleTicket(ticketId, reader, roles);
        return commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId).stream()
                .map(CommentResponse::from)
                .toList();
    }

    // Solicitante, responsável e o próprio autor (as outras abas e dispositivos dele também atualizam)
    private static Set<UUID> participants(Ticket ticket, User author) {
        Set<UUID> ids = new HashSet<>();
        ids.add(ticket.getRequester().getId());
        if (ticket.getAssignee() != null) {
            ids.add(ticket.getAssignee().getId());
        }
        ids.add(author.getId());
        return ids;
    }

    // Coordenador do setor acompanha (lê); comentar continua só para os participantes
    private Ticket findVisibleTicket(UUID ticketId, User user, List<String> roles) {
        return ticketRepository.findById(ticketId)
                .filter(ticket -> TicketAccess.canView(ticket, user.getId(), roles)
                        || (userRepository.isCoordinator(user.getId())
                            && TicketAccess.inSector(ticket, user.getDepartment().getId())))
                .orElseThrow(() -> new TicketNotFoundException(ticketId));
    }

    // O token vale até 15 min: a conta pode ter sido desativada nesse meio-tempo
    private User findActiveUser(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(InvalidSessionException::new);
        if (!user.isActive()) {
            throw new AccountNotActiveException();
        }
        return user;
    }
}
