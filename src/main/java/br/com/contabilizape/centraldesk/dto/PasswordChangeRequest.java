package br.com.contabilizape.centraldesk.dto;

import br.com.contabilizape.centraldesk.validation.StrongPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Troca da própria senha")
public record PasswordChangeRequest(
    @Schema(description = "Senha atual", example = "senhaForte123", accessMode = Schema.AccessMode.WRITE_ONLY)
    @NotBlank
    String currentPassword,

    @Schema(description = "Senha nova: 8 a 72 caracteres, com ao menos uma letra e um número, diferente da atual",
            example = "novaSenha456", accessMode = Schema.AccessMode.WRITE_ONLY)
    @StrongPassword
    String newPassword
) {}
