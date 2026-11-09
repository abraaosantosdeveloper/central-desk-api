package br.com.contabilizape.centraldesk.exception;

/** O painel de métricas é só de administradores e coordenadores de setor (D4 e D5). */
public class MetricsForbiddenException extends RuntimeException {

    public MetricsForbiddenException() {
        super("Só administradores e coordenadores de setor veem o painel de métricas.");
    }
}
