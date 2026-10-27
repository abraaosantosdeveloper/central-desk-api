package br.com.contabilizape.centraldesk.event;

import br.com.contabilizape.centraldesk.model.Comment;
import br.com.contabilizape.centraldesk.model.Ticket;

import java.util.UUID;

/**
 * Comentário gravado. recipientId é o "outro lado" NO MOMENTO do comentário: o responsável, se quem
 * comentou foi o solicitante, ou o solicitante, se foi o responsável. Nulo quando não há outro lado
 * (chamado na fila, ou admin que é solicitante e responsável da própria demanda).
 */
public record TicketCommentedEvent(UUID eventId, UUID ticketId, UUID commentId, UUID authorId, UUID recipientId) {

    public static TicketCommentedEvent of(Ticket ticket, Comment comment) {
        UUID authorId = comment.getAuthor().getId();
        UUID requesterId = ticket.getRequester().getId();
        UUID assigneeId = ticket.getAssignee() == null ? null : ticket.getAssignee().getId();
        UUID recipientId = authorId.equals(requesterId) ? assigneeId : requesterId;
        if (authorId.equals(recipientId)) {
            recipientId = null;
        }
        return new TicketCommentedEvent(UUID.randomUUID(), ticket.getId(), comment.getId(), authorId, recipientId);
    }
}
