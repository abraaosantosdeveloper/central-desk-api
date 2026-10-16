package br.com.contabilizape.centraldesk.exception;

/**
 * Token válido, mas o usuário dele não existe mais.
 */
public class InvalidSessionException extends RuntimeException {

    public InvalidSessionException() {
        super("Sessão inválida. Entre novamente.");
    }
}
