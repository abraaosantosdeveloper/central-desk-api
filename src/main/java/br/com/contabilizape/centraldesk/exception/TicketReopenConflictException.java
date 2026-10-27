package br.com.contabilizape.centraldesk.exception;

/** Chamado não encerrado, fora do prazo de 7 dias ou já reaberto 3 vezes (409). */
public class TicketReopenConflictException extends RuntimeException {

    public TicketReopenConflictException(String message) {
        super(message);
    }
}
