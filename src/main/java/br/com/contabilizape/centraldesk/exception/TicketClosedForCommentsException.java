package br.com.contabilizape.centraldesk.exception;

/** Chamado encerrado não recebe comentários novos; o histórico continua visível. */
public class TicketClosedForCommentsException extends RuntimeException {

    public TicketClosedForCommentsException() {
        super("O chamado está encerrado e não recebe novos comentários.");
    }
}
