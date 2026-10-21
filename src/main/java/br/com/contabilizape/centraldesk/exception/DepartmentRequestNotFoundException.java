package br.com.contabilizape.centraldesk.exception;

import java.util.UUID;

public class DepartmentRequestNotFoundException extends RuntimeException {

    public DepartmentRequestNotFoundException(UUID id) {
        super("Pedido não encontrado: " + id);
    }
}
