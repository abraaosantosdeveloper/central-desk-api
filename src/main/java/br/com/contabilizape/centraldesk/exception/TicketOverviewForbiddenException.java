package br.com.contabilizape.centraldesk.exception;

/** "Todos os chamados" é só de administradores e coordenadores (TC4). */
public class TicketOverviewForbiddenException extends RuntimeException {

    public TicketOverviewForbiddenException() {
        super("Só administradores e coordenadores de setor acompanham todos os chamados.");
    }
}
