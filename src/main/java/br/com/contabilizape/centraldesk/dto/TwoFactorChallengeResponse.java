package br.com.contabilizape.centraldesk.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Senha certa; falta o código enviado por e-mail (POST /auth/login/tfa)")
public record TwoFactorChallengeResponse(
    @Schema(example = "true")
    boolean tfaRequired
) {}
