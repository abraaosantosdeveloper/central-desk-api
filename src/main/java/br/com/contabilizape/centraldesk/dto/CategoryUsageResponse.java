package br.com.contabilizape.centraldesk.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Quantos chamados usam cada categoria ativa")
public record CategoryUsageResponse(
    UUID categoryId,
    @Schema(example = "12") long tickets
) {}
