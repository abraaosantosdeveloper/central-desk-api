package br.com.contabilizape.centraldesk.realtime;

/** O que mexeu na fila de atendimento (campo "reason" do evento SSE queue.changed). */
public enum QueueChangeReason {
    /** Chamado novo na fila. */
    OPENED,
    /** Alguém assumiu o chamado (ou um admin o reatribuiu a partir da fila). */
    ASSIGNED,
    /** Chamado encerrado ainda na fila. */
    CLOSED,
    /** Chamados em atendimento voltaram à fila porque o responsável saiu. */
    RETURNED,
    /** Chamado encerrado foi reaberto e voltou para a fila (o responsável não pode mais atender). */
    REOPENED
}
