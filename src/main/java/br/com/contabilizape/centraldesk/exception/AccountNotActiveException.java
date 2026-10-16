package br.com.contabilizape.centraldesk.exception;

public class AccountNotActiveException extends RuntimeException {

    public AccountNotActiveException() {
        super("Esta conta não está ativa. Procure um administrador.");
    }
}
