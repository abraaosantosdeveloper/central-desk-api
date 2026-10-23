package br.com.contabilizape.centraldesk.dto;

import br.com.contabilizape.centraldesk.model.Ticket;
import br.com.contabilizape.centraldesk.model.TicketStatus;
import br.com.contabilizape.centraldesk.model.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Resumo do chamado para as listagens dos painéis (sem a descrição; ela vem em GET /tickets/{id})")
public record TicketSummaryResponse(
    UUID id,
    @Schema(description = "Número sequencial exibido como #n; o id continua sendo o UUID", example = "123")
    Long number,
    String title,
    TicketStatus status,
    CategoryResponse category,
    PriorityResponse priority,
    UUID requesterId,
    String requesterName,
    @Schema(description = "Responsável; vazio enquanto o chamado está na fila")
    UUID assigneeId,
    String assigneeName,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {

    public static TicketSummaryResponse from(Ticket ticket) {
        return new TicketSummaryResponse(
            ticket.getId(),
            ticket.getNumber(),
            ticket.getTitle(),
            ticket.getStatus(),
            CategoryResponse.from(ticket.getCategory()),
            PriorityResponse.from(ticket.getPriority()),
            ticket.getRequester().getId(),
            ticket.getRequester().getFullName(),
            idOf(ticket.getAssignee()),
            nameOf(ticket.getAssignee()),
            ticket.getCreatedAt(),
            ticket.getUpdatedAt()
        );
    }

    private static UUID idOf(User user) {
        return user == null ? null : user.getId();
    }

    private static String nameOf(User user) {
        return user == null ? null : user.getFullName();
    }
}
