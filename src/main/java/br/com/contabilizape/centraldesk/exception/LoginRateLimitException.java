package br.com.contabilizape.centraldesk.exception;

import java.time.Duration;

/** Tentativas erradas demais a partir do mesmo IP (429). */
public class LoginRateLimitException extends RuntimeException {

    private final Duration retryAfter;

    public LoginRateLimitException(Duration retryAfter) {
        super("Muitas tentativas de acesso a partir desta rede. Aguarde alguns minutos e tente de novo.");
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
