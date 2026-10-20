package br.com.contabilizape.centraldesk.exception;

import br.com.contabilizape.centraldesk.model.TicketStatus;

/** Ação que não cabe no status atual do chamado (ex.: assumir um chamado já em atendimento). */
public class InvalidTicketTransitionException extends RuntimeException {

    public InvalidTicketTransitionException(TicketStatus from, TicketStatus to) {
        super("O chamado está " + from + " e não pode passar para " + to + ".");
    }
}
