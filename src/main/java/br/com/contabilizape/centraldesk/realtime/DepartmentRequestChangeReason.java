package br.com.contabilizape.centraldesk.realtime;

/** O que mexeu nos pedidos pendentes (campo "reason" do evento SSE department-requests.changed). */
public enum DepartmentRequestChangeReason {
    /** Pedido novo aguardando decisão. */
    CREATED,
    /** Um administrador aprovou o pedido. */
    APPROVED,
    /** Um administrador recusou o pedido. */
    REJECTED
}
