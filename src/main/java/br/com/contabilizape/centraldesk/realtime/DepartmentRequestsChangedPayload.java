package br.com.contabilizape.centraldesk.realtime;

import java.util.UUID;

/**
 * Dados do evento SSE department-requests.changed. pendingCount é a contagem pronta, calculada depois do
 * commit: o cliente só a substitui (mesma regra do queue.changed).
 */
public record DepartmentRequestsChangedPayload(long pendingCount, UUID requestId,
                                               DepartmentRequestChangeReason reason) {
}
