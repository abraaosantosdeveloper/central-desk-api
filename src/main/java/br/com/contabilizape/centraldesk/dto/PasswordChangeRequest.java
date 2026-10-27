package br.com.contabilizape.centraldesk.dto;

import br.com.contabilizape.centraldesk.validation.StrongPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Troca da própria senha")
public record PasswordChangeRequest(
    @Schema(description = "Senha atual", example = "senhaForte123", accessMode = Schema.AccessMode.WRITE_ONLY)
    @NotBlank
    String currentPassword,

    @Schema(description = "Senha nova: 8 a 72 caracteres, com ao menos uma letra e um número, diferente da atual",
            example = "novaSenha456", accessMode = Schema.AccessMode.WRITE_ONLY)
    @StrongPassword
    String newPassword,

    @Schema(description = "Código enviado por POST /users/me/password/code. Obrigatório com a verificação em duas etapas "
            + "ligada (e sempre para administradores), menos na troca obrigatória da senha temporária.", example = "042917")
    @Size(max = 6)
    String code
) {

    public PasswordChangeRequest(String currentPassword, String newPassword) {
        this(currentPassword, newPassword, null);
    }
}
