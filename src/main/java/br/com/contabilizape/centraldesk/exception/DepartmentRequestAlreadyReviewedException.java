package br.com.contabilizape.centraldesk.exception;

/** Pedido de departamento já aprovado ou recusado: a decisão é única. */
public class DepartmentRequestAlreadyReviewedException extends RuntimeException {

    public DepartmentRequestAlreadyReviewedException() {
        super("Este pedido já foi analisado.");
    }
}
