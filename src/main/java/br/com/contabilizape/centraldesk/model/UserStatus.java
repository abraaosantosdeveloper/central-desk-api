package br.com.contabilizape.centraldesk.model;

/**
 * Situação da conta. Só ACTIVE entra no sistema.
 *
 * PENDING_VERIFICATION → ACTIVE (e-mail confirmado) ⇄ DEACTIVATED → ANONYMIZED (final);
 * ACTIVE → BLOCKED (5 tentativas erradas) → ACTIVE (senha redefinida por um administrador).
 */
public enum UserStatus {
    ACTIVE,
    DEACTIVATED,
    ANONYMIZED,
    /** Cadastro feito, e-mail ainda não confirmado: não entra; é apagado depois de 24 h. */
    PENDING_VERIFICATION,
    /** 5 tentativas de acesso erradas seguidas: só um administrador libera (redefinindo a senha). */
    BLOCKED
}
