package br.com.contabilizape.centraldesk.dto;

import br.com.contabilizape.centraldesk.model.PrivilegeLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Novo nível de privilégio da conta (exclusivos entre si)")
public record PrivilegeChangeRequest(
    @Schema(description = "ADMIN (só contas da Inovação), COORDINATOR (coordena o próprio departamento) ou COLLABORATOR",
            example = "COORDINATOR")
    @NotNull(message = "Escolha o nível de privilégio.")
    PrivilegeLevel level
) {
}
