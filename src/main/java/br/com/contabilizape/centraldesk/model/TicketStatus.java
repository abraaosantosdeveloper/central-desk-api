package br.com.contabilizape.centraldesk.model;

/**
 * O status muda só por ações (abrir, assumir, encerrar), nunca por edição direta.
 */
public enum TicketStatus {
    OPEN,
    IN_PROGRESS,
    CLOSED
}
