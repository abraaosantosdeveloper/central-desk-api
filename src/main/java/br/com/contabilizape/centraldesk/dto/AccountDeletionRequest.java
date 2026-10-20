package br.com.contabilizape.centraldesk.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Confirmação do encerramento da própria conta")
public record AccountDeletionRequest(
    @Schema(description = "Senha atual, para confirmar", example = "senhaForte123", accessMode = Schema.AccessMode.WRITE_ONLY)
    @NotBlank
    String password
) {}
