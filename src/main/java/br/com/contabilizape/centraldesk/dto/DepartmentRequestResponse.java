package br.com.contabilizape.centraldesk.dto;

import br.com.contabilizape.centraldesk.model.DepartmentRequest;
import br.com.contabilizape.centraldesk.model.DepartmentRequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Pedido de entrada em departamento (hoje, só a Inovação exige aprovação)")
public record DepartmentRequestResponse(
    UUID id,
    UUID userId,
    String userName,
    String userEmail,
    UUID departmentId,
    String departmentName,
    DepartmentRequestStatus status,
    OffsetDateTime createdAt,
    @Schema(description = "Quem aprovou ou recusou; vazio enquanto estiver pendente")
    UUID reviewedById,
    OffsetDateTime reviewedAt
) {

    public static DepartmentRequestResponse from(DepartmentRequest request) {
        return new DepartmentRequestResponse(
            request.getId(),
            request.getUser().getId(),
            request.getUser().getFullName(),
            request.getUser().getEmail(),
            request.getDepartment().getId(),
            request.getDepartment().getName(),
            request.getStatus(),
            request.getCreatedAt(),
            request.getReviewedBy() == null ? null : request.getReviewedBy().getId(),
            request.getReviewedAt()
        );
    }
}
