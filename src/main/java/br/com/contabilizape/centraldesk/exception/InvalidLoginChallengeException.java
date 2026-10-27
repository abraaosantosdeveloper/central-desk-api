package br.com.contabilizape.centraldesk.exception;

/** Sem o cookie do desafio, ou desafio vencido, usado ou esgotado: é preciso entrar de novo (401). */
public class InvalidLoginChallengeException extends RuntimeException {

    public InvalidLoginChallengeException() {
        super("A verificação expirou. Entre de novo com e-mail e senha.");
    }
}
