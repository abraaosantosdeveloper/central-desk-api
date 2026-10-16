package br.com.contabilizape.centraldesk.exception;

import java.util.UUID;

public class DepartmentNotFoundException extends RuntimeException {

    public DepartmentNotFoundException(UUID departmentId) {
        super("Departamento não encontrado: " + departmentId);
    }
}
