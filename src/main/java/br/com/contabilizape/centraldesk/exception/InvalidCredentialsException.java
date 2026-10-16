package br.com.contabilizape.centraldesk.exception;

/**
 * E-mail inexistente ou senha errada. A mensagem é a mesma nos dois casos, para não revelar quem tem conta.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("E-mail ou senha inválidos.");
    }
}
