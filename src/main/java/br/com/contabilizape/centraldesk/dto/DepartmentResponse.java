package br.com.contabilizape.centraldesk.dto;

import br.com.contabilizape.centraldesk.model.Department;

import java.util.UUID;

public record DepartmentResponse(UUID id, String name) {

    public static DepartmentResponse from(Department department) {
        return new DepartmentResponse(department.getId(), department.getName());
    }
}
