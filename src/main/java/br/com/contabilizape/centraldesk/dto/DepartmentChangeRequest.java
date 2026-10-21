package br.com.contabilizape.centraldesk.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Novo departamento da conta, definido pelo administrador")
public record DepartmentChangeRequest(
    @Schema(example = "3094ed3f-42ab-4bc0-9d8d-f6eb08891016") @NotNull
    UUID departmentId
) {}
