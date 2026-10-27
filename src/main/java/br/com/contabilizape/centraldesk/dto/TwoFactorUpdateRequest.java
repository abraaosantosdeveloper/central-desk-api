package br.com.contabilizape.centraldesk.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Liga ou desliga a verificação em duas etapas. Desligar pede a senha atual e o código "
        + "enviado por POST /users/me/two-factor/code; ligar não pede nada.")
public record TwoFactorUpdateRequest(
    @NotNull
    Boolean enabled,

    @Schema(accessMode = Schema.AccessMode.WRITE_ONLY)
    @Size(max = 72)
    String currentPassword,

    @Schema(example = "042917")
    @Size(max = 6)
    String code
) {}
