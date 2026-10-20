package br.com.contabilizape.centraldesk.model;

import java.util.Map;
import java.util.Set;

/**
 * O status muda só por ações (abrir, assumir, encerrar), nunca por edição direta.
 *
 * Transições permitidas:
 * <pre>
 *   OPEN ──assumir──▶ IN_PROGRESS ──encerrar──▶ CLOSED
 *     ▲                  │
 *     └─devolver à fila──┘          OPEN ──encerrar──▶ CLOSED
 * </pre>
 * CLOSED é final.
 */
public enum TicketStatus {
    OPEN,
    IN_PROGRESS,
    CLOSED;

    private static final Map<TicketStatus, Set<TicketStatus>> TRANSITIONS = Map.of(
            OPEN, Set.of(IN_PROGRESS, CLOSED),
            IN_PROGRESS, Set.of(OPEN, CLOSED),
            CLOSED, Set.of()
    );

    public boolean canTransitionTo(TicketStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }
}
