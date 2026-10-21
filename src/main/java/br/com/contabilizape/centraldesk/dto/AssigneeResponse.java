package br.com.contabilizape.centraldesk.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Pessoa que pode atender chamados (Inovação ou administrador) e quantos ela tem em atendimento")
public record AssigneeResponse(
    UUID id,
    @Schema(example = "Carlos Lima") String fullName,
    @Schema(example = "Inovação") String departmentName,
    @Schema(example = "3") long inProgress
) {}
