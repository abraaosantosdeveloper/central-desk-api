package br.com.contabilizape.centraldesk.exception;

/** O administrador não desativa a própria conta (evita ficar trancado para fora do sistema). */
public class SelfDeactivationException extends RuntimeException {

    public SelfDeactivationException() {
        super("Você não pode desativar a sua própria conta.");
    }
}
