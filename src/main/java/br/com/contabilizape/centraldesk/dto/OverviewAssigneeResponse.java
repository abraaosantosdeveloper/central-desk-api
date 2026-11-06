package br.com.contabilizape.centraldesk.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Responsável por algum chamado do recorte de \"Todos os chamados\" (opção do filtro de responsável)")
public record OverviewAssigneeResponse(
    UUID id,
    @Schema(example = "Carlos Lima") String fullName,
    @Schema(example = "Inovação") String departmentName
) {}
