package br.com.contabilizape.centraldesk.exception;

/** Usuário consultado por id (rotas de administrador) que não existe. */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException() {
        super("Usuário não encontrado.");
    }
}
