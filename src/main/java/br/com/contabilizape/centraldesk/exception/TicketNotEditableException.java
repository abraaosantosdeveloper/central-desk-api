package br.com.contabilizape.centraldesk.exception;

/**
 * Depois que alguém da Inovação assume, o chamado não muda mais de conteúdo.
 */
public class TicketNotEditableException extends RuntimeException {

    public TicketNotEditableException() {
        super("O chamado já foi assumido e não pode mais ser editado.");
    }
}
