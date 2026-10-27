package br.com.contabilizape.centraldesk.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "Senha temporária gerada pelo sistema. Aparece só nesta resposta: não é enviada por e-mail nem gravada em log.")
public record TemporaryPasswordResponse(
    @Schema(description = "Entregue pessoalmente; vale até expiresAt e obriga a troca no primeiro acesso",
            example = "K7m2Qx9vTb4Rn8Wd")
    String temporaryPassword,
    OffsetDateTime expiresAt
) {}
