package br.com.contabilizape.centraldesk.exception;

public class NewPasswordSameAsCurrentException extends InvalidFieldException {

    public NewPasswordSameAsCurrentException() {
        super("newPassword", "deve ser diferente da senha atual");
    }
}
