package br.com.contabilizape.centraldesk.exception;

/** Só administradores alteram o setor de origem de um chamado. */
public class OriginDepartmentChangeForbiddenException extends RuntimeException {

    public OriginDepartmentChangeForbiddenException() {
        super("Só administradores podem alterar o setor de origem do chamado.");
    }
}
