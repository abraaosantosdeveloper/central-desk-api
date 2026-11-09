package br.com.contabilizape.centraldesk.exception;

/** Período do painel de métricas invertido ou longo demais. Vira 400 em {@code errors.from}. */
public class InvalidMetricsPeriodException extends InvalidFieldException {

    public InvalidMetricsPeriodException(String message) {
        super("from", message);
    }
}
