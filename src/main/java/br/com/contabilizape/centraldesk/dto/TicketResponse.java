package br.com.contabilizape.centraldesk.dto;

import br.com.contabilizape.centraldesk.model.Department;
import br.com.contabilizape.centraldesk.model.Ticket;
import br.com.contabilizape.centraldesk.model.TicketStatus;
import br.com.contabilizape.centraldesk.model.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Chamado completo (detalhes); as listagens usam um resumo sem a descrição")
public record TicketResponse(
    UUID id,
    @Schema(description = "Número sequencial exibido como #n; o id continua sendo o UUID", example = "123")
    Long number,
    String title,
    String description,
    TicketStatus status,
    CategoryResponse category,
    PriorityResponse priority,
    UUID requesterId,
    String requesterName,
    @Schema(description = "Setor de origem: o departamento de quem abriu, no momento da abertura (nunca muda)")
    DepartmentResponse originDepartment,
    @Schema(description = "Setor de destino: quem recebe o chamado (hoje sempre a Inovação)")
    DepartmentResponse targetDepartment,
    @Schema(description = "Responsável; vazio até alguém da Inovação assumir")
    UUID assigneeId,
    String assigneeName,
    OffsetDateTime assignedAt,
    @Schema(description = "Quem encerrou; vazio enquanto o chamado não está CLOSED")
    UUID closedById,
    String closedByName,
    OffsetDateTime closedAt,
    @Schema(description = "O que foi feito, registrado por quem encerrou; vazio enquanto o chamado não está CLOSED")
    String resolution,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {

    public static TicketResponse from(Ticket ticket) {
        return new TicketResponse(
            ticket.getId(),
            ticket.getNumber(),
            ticket.getTitle(),
            ticket.getDescription(),
            ticket.getStatus(),
            CategoryResponse.from(ticket.getCategory()),
            PriorityResponse.from(ticket.getPriority()),
            ticket.getRequester().getId(),
            ticket.getRequester().getFullName(),
            departmentOf(ticket.getOriginDepartment()),
            departmentOf(ticket.getTargetDepartment()),
            idOf(ticket.getAssignee()),
            nameOf(ticket.getAssignee()),
            ticket.getAssignedAt(),
            idOf(ticket.getClosedBy()),
            nameOf(ticket.getClosedBy()),
            ticket.getClosedAt(),
            ticket.getResolution(),
            ticket.getCreatedAt(),
            ticket.getUpdatedAt()
        );
    }

    private static DepartmentResponse departmentOf(Department department) {
        return department == null ? null : DepartmentResponse.from(department);
    }

    private static UUID idOf(User user) {
        return user == null ? null : user.getId();
    }

    private static String nameOf(User user) {
        return user == null ? null : user.getFullName();
    }
}
