package br.com.contabilizape.centraldesk.service;

/**
 * Resultado do login: a sessão aberta, ou o desafio da verificação em duas etapas (a sessão só sai
 * depois do código, em POST /auth/login/tfa).
 */
public record LoginOutcome(AuthenticatedSession session, String challengeToken) {

    public static LoginOutcome opened(AuthenticatedSession session) {
        return new LoginOutcome(session, null);
    }

    public static LoginOutcome twoFactorRequired(String challengeToken) {
        return new LoginOutcome(null, challengeToken);
    }

    public boolean isTwoFactorRequired() {
        return challengeToken != null;
    }
}
