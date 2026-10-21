package br.com.contabilizape.centraldesk.dto;

import br.com.contabilizape.centraldesk.model.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Filtros opcionais da listagem de contas (query string). Campo ausente = sem filtro.
 */
public record UserFilter(
    @Schema(description = "Status da conta. Sem o filtro, as anonimizadas ficam de fora.", example = "ACTIVE")
    UserStatus status,

    @Schema(description = "Parte do nome ou do e-mail, sem diferenciar maiúsculas", example = "maria")
    String search,

    @Schema(description = "Perfil: ADMIN (administradores), HANDLER (Inovação, sem ser admin) ou "
            + "COLLABORATOR (demais contas)", example = "HANDLER")
    UserRole role,

    @Schema(description = "Departamento da conta")
    UUID departmentId
) {

    public enum UserRole { ADMIN, HANDLER, COLLABORATOR }
}
