package br.com.contabilizape.centraldesk.dto;

import br.com.contabilizape.centraldesk.model.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Uma conta aberta pelo administrador: os campos da listagem mais o CPF, que a tela de edição precisa.
 * A listagem (AdminUserResponse) continua sem o CPF.
 */
@Schema(description = "Conta aberta pelo administrador: os campos da listagem mais o CPF. Nunca inclui a senha nem o hash.")
public record AdminUserDetailResponse(
    UUID id,
    String fullName,
    @Schema(description = "CPF só com os 11 dígitos; vazio em conta anonimizada", example = "52998224725")
    String cpf,
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

    public static AdminUserDetailResponse from(AdminUserResponse account, String cpf) {
        return new AdminUserDetailResponse(
            account.id(),
            account.fullName(),
            cpf,
            account.email(),
            account.status(),
            account.departmentId(),
            account.departmentName(),
            account.admin(),
            account.ticketHandler(),
            account.departmentApprovalPending(),
            account.createdAt(),
            account.deactivatedAt()
        );
    }
}
