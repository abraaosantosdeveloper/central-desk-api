package br.com.contabilizape.centraldesk.exception;

/**
 * Só quem abriu o chamado pode editá-lo.
 */
public class TicketEditForbiddenException extends RuntimeException {

    public TicketEditForbiddenException() {
        super("Só quem abriu o chamado pode editá-lo.");
    }
}
