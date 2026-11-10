package br.com.contabilizape.centraldesk.exception;

/** O setor informado já é a origem do chamado: não há o que alterar. */
public class OriginDepartmentConflictException extends RuntimeException {

    public OriginDepartmentConflictException() {
        super("Este já é o setor de origem do chamado.");
    }
}
