package br.com.contabilizape.centraldesk.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Sem validação de formato: qualquer e-mail ou senha que não confira recebe o mesmo 401.
 */
@Schema(description = "Credenciais de acesso")
public record LoginRequest(
    @Schema(description = "E-mail institucional (maiúsculas e espaços nas pontas são ignorados)",
            example = "maria.silva@contabilizape.com.br")
    @NotBlank @Size(max = 255)
    String email,

    @Schema(example = "senhaForte123", accessMode = Schema.AccessMode.WRITE_ONLY)
    @NotBlank @Size(max = 72)
    String password
) {}
