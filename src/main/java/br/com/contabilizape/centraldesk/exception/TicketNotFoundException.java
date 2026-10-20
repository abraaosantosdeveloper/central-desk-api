package br.com.contabilizape.centraldesk.exception;

import java.util.UUID;

public class TicketNotFoundException extends RuntimeException {

    public TicketNotFoundException(UUID ticketId) {
        super("Chamado não encontrado: " + ticketId);
    }
}
