package br.com.contabilizape.centraldesk.exception;

import java.time.Duration;

/** Pedidos de código demais (intervalo de 60 s, 5 por hora por conta ou limite do IP): 429. */
public class CodeRequestLimitException extends RuntimeException {

    private final Duration retryAfter;

    public CodeRequestLimitException(Duration retryAfter) {
        super("Muitos pedidos de código. Aguarde um pouco e tente de novo.");
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
