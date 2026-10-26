package br.com.contabilizape.centraldesk.event;

import java.util.UUID;

/** Pedido de entrada recusado: a fila de Aprovações dos administradores perdeu um item. */
public record DepartmentRequestRejectedEvent(UUID eventId, UUID requestId) {

    public static DepartmentRequestRejectedEvent of(UUID requestId) {
        return new DepartmentRequestRejectedEvent(UUID.randomUUID(), requestId);
    }
}
