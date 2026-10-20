package br.com.contabilizape.centraldesk.dto;

import br.com.contabilizape.centraldesk.model.Ticket;
import br.com.contabilizape.centraldesk.model.TicketStatus;
import br.com.contabilizape.centraldesk.model.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Chamado completo (detalhes); as listagens usam um resumo sem a descrição")
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
    String assigneeName,
    OffsetDateTime assignedAt,
    @Schema(description = "Quem encerrou; vazio enquanto o chamado não está CLOSED")
    UUID closedById,
    String closedByName,
    OffsetDateTime closedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
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
            idOf(ticket.getAssignee()),
            nameOf(ticket.getAssignee()),
            ticket.getAssignedAt(),
            idOf(ticket.getClosedBy()),
            nameOf(ticket.getClosedBy()),
            ticket.getClosedAt(),
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
