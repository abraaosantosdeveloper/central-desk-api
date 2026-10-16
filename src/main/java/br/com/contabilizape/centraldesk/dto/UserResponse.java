package br.com.contabilizape.centraldesk.dto;

import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.model.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Conta do usuário. Nunca inclui a senha nem o hash.")
public record UserResponse(
    UUID id,
    String fullName,
    String email,
    UserStatus status,
    @Schema(description = "Departamento atual (quem pediu Inovação fica em Geral até a aprovação)")
    UUID departmentId,
    String departmentName,
    @Schema(description = "true quando há pedido de entrada na Inovação aguardando um administrador")
    boolean departmentApprovalPending,
    OffsetDateTime createdAt
) {

    public static UserResponse from(User user, boolean departmentApprovalPending){
        return new UserResponse(
            user.getId(), 
            user.getFullName(), 
            user.getEmail(), 
            user.getStatus(), 
            user.getDepartment().getId(), 
            user.getDepartment().getName(), 
            departmentApprovalPending, 
            user.getCreatedAt()
        );
    }

}
