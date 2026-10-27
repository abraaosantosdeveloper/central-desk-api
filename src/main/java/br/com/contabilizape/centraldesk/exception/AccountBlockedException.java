package br.com.contabilizape.centraldesk.exception;

/**
 * Senha certa numa conta bloqueada por tentativas erradas (403). Com a senha errada, a resposta é o
 * 401 genérico: só quem sabe a senha descobre que a conta está bloqueada (B2).
 */
public class AccountBlockedException extends RuntimeException {

    public AccountBlockedException() {
        super("Conta bloqueada. Procure a equipe de Inovação.");
    }
}
