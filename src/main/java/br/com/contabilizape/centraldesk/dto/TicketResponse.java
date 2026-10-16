package br.com.contabilizape.centraldesk.dto;

import br.com.contabilizape.centraldesk.model.Ticket;
import br.com.contabilizape.centraldesk.model.TicketStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Chamado")
public record TicketResponse(
    UUID id,
    String title,
    String description,
    TicketStatus status,
    CategoryResponse category,
    PriorityResponse priority,
    UUID requesterId,
    String requesterName,
    @Schema(description = "Responsável; vazio até alguém da Inovação assumir")
    UUID assigneeId,
    OffsetDateTime createdAt
) {

    public static TicketResponse from(Ticket ticket) {
        return new TicketResponse(
            ticket.getId(),
            ticket.getTitle(),
            ticket.getDescription(),
            ticket.getStatus(),
            CategoryResponse.from(ticket.getCategory()),
            PriorityResponse.from(ticket.getPriority()),
            ticket.getRequester().getId(),
            ticket.getRequester().getFullName(),
            ticket.getAssignee() == null ? null : ticket.getAssignee().getId(),
            ticket.getCreatedAt()
        );
    }
}
