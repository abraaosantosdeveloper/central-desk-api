package br.com.contabilizape.centraldesk.exception;

public class PriorityNotFoundException extends RuntimeException {

    public PriorityNotFoundException(Short priorityId) {
        super("Prioridade não encontrada: " + priorityId);
    }
}
