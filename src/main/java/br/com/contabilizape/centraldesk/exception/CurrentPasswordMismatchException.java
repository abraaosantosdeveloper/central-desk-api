package br.com.contabilizape.centraldesk.exception;

/**
 * Senha atual errada na troca de senha. É 400 (e não 401) para o front não confundir com sessão vencida.
 */
public class CurrentPasswordMismatchException extends InvalidFieldException {

    public CurrentPasswordMismatchException() {
        super("currentPassword", "senha atual incorreta");
    }
}
