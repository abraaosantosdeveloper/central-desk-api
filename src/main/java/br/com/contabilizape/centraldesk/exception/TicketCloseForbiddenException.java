package br.com.contabilizape.centraldesk.exception;

/** Quem abriu o chamado não o encerra: o encerramento é de quem atende (Inovação ou admin). */
public class TicketCloseForbiddenException extends RuntimeException {

    public TicketCloseForbiddenException() {
        super("Quem abriu o chamado não pode encerrá-lo. Peça o encerramento ao responsável pela conversa.");
    }
}
