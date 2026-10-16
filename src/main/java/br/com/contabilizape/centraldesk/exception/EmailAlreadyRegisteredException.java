package br.com.contabilizape.centraldesk.exception;

public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException() {
        super("E-mail já cadastrado");
    }
}
