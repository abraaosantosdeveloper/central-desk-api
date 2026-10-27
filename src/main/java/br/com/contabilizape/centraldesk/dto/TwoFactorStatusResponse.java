package br.com.contabilizape.centraldesk.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Verificação em duas etapas da própria conta")
public record TwoFactorStatusResponse(
    @Schema(description = "O login pede o código enviado por e-mail (salvo em navegador confiável)")
    boolean enabled,
    @Schema(description = "Obrigatória para administradores: o interruptor não aparece para eles")
    boolean mandatory
) {}
