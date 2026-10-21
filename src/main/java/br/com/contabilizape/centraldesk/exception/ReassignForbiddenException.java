package br.com.contabilizape.centraldesk.exception;

public class ReassignForbiddenException extends RuntimeException {

    public ReassignForbiddenException() {
        super("Só o responsável pelo chamado ou um administrador podem reatribuí-lo.");
    }
}
