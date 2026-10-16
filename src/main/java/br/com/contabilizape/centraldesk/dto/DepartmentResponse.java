package br.com.contabilizape.centraldesk.dto;

import br.com.contabilizape.centraldesk.model.Department;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record DepartmentResponse(
    @Schema(example = "3094ed3f-42ab-4bc0-9d8d-f6eb08891016") UUID id,
    @Schema(example = "Geral") String name
) {

    public static DepartmentResponse from(Department department) {
        return new DepartmentResponse(department.getId(), department.getName());
    }
}
