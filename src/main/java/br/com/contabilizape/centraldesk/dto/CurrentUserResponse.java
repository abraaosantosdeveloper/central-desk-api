package br.com.contabilizape.centraldesk.dto;

import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.model.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Usuário logado e suas permissões. Nunca inclui a senha, o hash nem o CPF.")
public record CurrentUserResponse(
    UUID id,
    String fullName,
    String email,
    UserStatus status,
    UUID departmentId,
    String departmentName,
    @Schema(description = "true quando há pedido de entrada na Inovação aguardando um administrador")
    boolean departmentApprovalPending,
    OffsetDateTime createdAt,
    @Schema(description = "Permissões: ROLE_ADMIN (administrador) e TICKET_HANDLER (atende chamados). Vazia para colaboradores.",
            example = "[]")
    List<String> roles
) {

    public static CurrentUserResponse from(User user, boolean departmentApprovalPending, List<String> roles) {
        return new CurrentUserResponse(
            user.getId(),
            user.getFullName(),
            user.getEmail(),
            user.getStatus(),
            user.getDepartment().getId(),
            user.getDepartment().getName(),
            departmentApprovalPending,
            user.getCreatedAt(),
            List.copyOf(roles)
        );
    }
}
