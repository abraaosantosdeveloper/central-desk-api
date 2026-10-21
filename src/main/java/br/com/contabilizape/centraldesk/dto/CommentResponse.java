package br.com.contabilizape.centraldesk.dto;

import br.com.contabilizape.centraldesk.model.Comment;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Comentário do chamado (o conteúdo já vem decifrado)")
public record CommentResponse(
    UUID id,
    UUID ticketId,
    UUID authorId,
    String authorName,
    String content,
    OffsetDateTime createdAt
) {

    public static CommentResponse from(Comment comment) {
        return new CommentResponse(
            comment.getId(),
            comment.getTicket().getId(),
            comment.getAuthor().getId(),
            comment.getAuthor().getFullName(),
            comment.getContent(),
            comment.getCreatedAt()
        );
    }
}
