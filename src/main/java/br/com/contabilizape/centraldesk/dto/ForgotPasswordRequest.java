package br.com.contabilizape.centraldesk.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Sem validar o formato: qualquer e-mail recebe a mesma resposta (B7). */
@Schema(description = "Pedido de código para redefinir a senha")
public record ForgotPasswordRequest(
    @Schema(example = "maria.silva@contabilizape.com.br")
    @NotBlank @Size(max = 255)
    String email
) {}
