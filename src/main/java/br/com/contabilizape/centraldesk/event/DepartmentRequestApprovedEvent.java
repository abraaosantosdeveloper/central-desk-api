package br.com.contabilizape.centraldesk.event;

import java.util.UUID;

/** Pedido de entrada aprovado: a fila de Aprovações dos administradores perdeu um item. */
public record DepartmentRequestApprovedEvent(UUID eventId, UUID requestId) {

    public static DepartmentRequestApprovedEvent of(UUID requestId) {
        return new DepartmentRequestApprovedEvent(UUID.randomUUID(), requestId);
    }
}
