package br.com.contabilizape.centraldesk.exception;

/** Mudança de status de conta que não cabe no estado atual (ex.: reativar uma conta ativa). */
public class InvalidAccountStatusException extends RuntimeException {

    public InvalidAccountStatusException(String message) {
        super(message);
    }
}
