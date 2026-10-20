package br.com.contabilizape.centraldesk.exception;

/** Senha errada na confirmação do encerramento da própria conta. */
public class PasswordConfirmationMismatchException extends InvalidFieldException {

    public PasswordConfirmationMismatchException() {
        super("password", "senha incorreta");
    }
}
