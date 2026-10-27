package br.com.contabilizape.centraldesk.exception;

public class TicketReopenForbiddenException extends RuntimeException {

    public TicketReopenForbiddenException() {
        super("Só quem abriu o chamado ou um administrador pode reabri-lo.");
    }
}
