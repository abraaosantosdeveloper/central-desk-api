package br.com.contabilizape.centraldesk.dto;

import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.model.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Conta vista pelo administrador. Nunca inclui a senha, o hash nem o CPF.")
public record AdminUserResponse(
    UUID id,
    String fullName,
    String email,
    UserStatus status,
    UUID departmentId,
    String departmentName,
    @Schema(description = "true quando a conta é de administrador")
    boolean admin,
    @Schema(description = "true quando a conta é da Inovação (atende chamados)")
    boolean ticketHandler,
    @Schema(description = "true quando há pedido de entrada na Inovação aguardando um administrador")
    boolean departmentApprovalPending,
    OffsetDateTime createdAt,
    @Schema(description = "Quando a conta foi desativada; vazio se estiver ativa")
    OffsetDateTime deactivatedAt
) {

    public static AdminUserResponse from(User user, boolean admin, boolean ticketHandler, boolean approvalPending) {
        return new AdminUserResponse(
            user.getId(),
            user.getFullName(),
            user.getEmail(),
            user.getStatus(),
            user.getDepartment().getId(),
            user.getDepartment().getName(),
            admin,
            ticketHandler,
            approvalPending,
            user.getCreatedAt(),
            user.getDeactivatedAt()
        );
    }
}
