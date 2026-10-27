package br.com.contabilizape.centraldesk.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Código de acesso enviado por e-mail no login")
public record TwoFactorCodeRequest(
    @Schema(example = "042917")
    @NotBlank @Size(max = 6)
    String code,

    @Schema(description = "Dispensa o código neste navegador por 30 dias (desmarque em computador compartilhado)",
            example = "true")
    boolean trustThisBrowser
) {}
