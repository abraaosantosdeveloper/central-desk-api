package br.com.contabilizape.centraldesk.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Confirmação do e-mail com o código recebido no cadastro")
public record EmailVerificationRequest(
    @Schema(example = "maria.silva@contabilizape.com.br")
    @NotBlank @Size(max = 255)
    String email,

    @Schema(description = "Código de 6 dígitos", example = "042917")
    @NotBlank @Size(max = 6)
    String code
) {}
