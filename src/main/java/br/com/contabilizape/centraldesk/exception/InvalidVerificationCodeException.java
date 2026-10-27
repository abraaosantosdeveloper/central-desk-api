package br.com.contabilizape.centraldesk.exception;

/**
 * Código errado, vencido, já usado ou que nunca existiu: a resposta é a mesma (400, campo "code"),
 * para não ajudar quem tenta adivinhar.
 */
public class InvalidVerificationCodeException extends InvalidFieldException {

    public InvalidVerificationCodeException() {
        super("code", "Código inválido ou vencido.");
    }
}
