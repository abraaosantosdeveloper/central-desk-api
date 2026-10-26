package br.com.contabilizape.centraldesk.realtime;

import java.util.UUID;

/**
 * Dados do evento SSE queue.changed. openCount é a contagem pronta, calculada depois do commit: o cliente
 * só a substitui, em vez de somar e subtrair (o que deriva com evento perdido, duplicado ou fora de ordem).
 * ticketId é nulo quando vários chamados mudaram de uma vez (RETURNED).
 */
public record QueueChangedPayload(long openCount, UUID ticketId, QueueChangeReason reason) {
}
