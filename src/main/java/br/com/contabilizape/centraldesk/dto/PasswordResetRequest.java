package br.com.contabilizape.centraldesk.dto;

import br.com.contabilizape.centraldesk.validation.StrongPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Redefinição de senha com o código recebido por e-mail")
public record PasswordResetRequest(
    @Schema(example = "maria.silva@contabilizape.com.br")
    @NotBlank @Size(max = 255)
    String email,

    @Schema(description = "Código de 6 dígitos recebido por e-mail", example = "042917")
    @NotBlank @Size(max = 6)
    String code,

    @Schema(description = "Senha nova: 8 a 72 caracteres, com ao menos uma letra e um número",
            example = "novaSenha456", accessMode = Schema.AccessMode.WRITE_ONLY)
    @StrongPassword
    String newPassword
) {}
