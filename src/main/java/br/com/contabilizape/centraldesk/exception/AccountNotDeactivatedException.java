package br.com.contabilizape.centraldesk.exception;

/** O administrador só anonimiza contas já desativadas. */
public class AccountNotDeactivatedException extends RuntimeException {

    public AccountNotDeactivatedException() {
        super("Só contas desativadas podem ser anonimizadas. Desative a conta antes.");
    }
}
