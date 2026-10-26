package br.com.contabilizape.centraldesk.realtime;

import br.com.contabilizape.centraldesk.event.TicketAssignedEvent;
import br.com.contabilizape.centraldesk.event.TicketClosedEvent;
import br.com.contabilizape.centraldesk.event.TicketOpenedEvent;
import br.com.contabilizape.centraldesk.event.TicketReassignedEvent;
import br.com.contabilizape.centraldesk.event.TicketsReturnedToQueueEvent;
import br.com.contabilizape.centraldesk.model.TicketStatus;
import br.com.contabilizape.centraldesk.repository.TicketRepository;
import br.com.contabilizape.centraldesk.security.Roles;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Set;
import java.util.UUID;

/**
 * Avisa a Inovação e os administradores conectados de que a fila mudou, já com o tamanho dela.
 * Roda depois do commit, então a contagem reflete o que foi gravado. Só os fatos que mexem na fila
 * chegam aqui com efeito: reatribuir ou encerrar um chamado em andamento não muda o contador.
 */
@Component
public class QueueRealtimeListener {

    public static final String QUEUE_CHANGED = "queue.changed";

    private static final Set<String> AUDIENCE = Set.of(Roles.TICKET_HANDLER, Roles.ADMIN);

    private final TicketRepository ticketRepository;
    private final SseEmitterRegistry registry;

    public QueueRealtimeListener(TicketRepository ticketRepository, SseEmitterRegistry registry) {
        this.ticketRepository = ticketRepository;
        this.registry = registry;
    }

    @TransactionalEventListener
    public void onTicketOpened(TicketOpenedEvent event) {
        publish(event.ticketId(), QueueChangeReason.OPENED);
    }

    @TransactionalEventListener
    public void onTicketAssigned(TicketAssignedEvent event) {
        publish(event.ticketId(), QueueChangeReason.ASSIGNED);
    }

    /** Reatribuir a partir da fila equivale a assumir em nome de outra pessoa. */
    @TransactionalEventListener
    public void onTicketReassigned(TicketReassignedEvent event) {
        if (event.fromQueue()) {
            publish(event.ticketId(), QueueChangeReason.ASSIGNED);
        }
    }

    @TransactionalEventListener
    public void onTicketClosed(TicketClosedEvent event) {
        if (event.wasOpen()) {
            publish(event.ticketId(), QueueChangeReason.CLOSED);
        }
    }

    @TransactionalEventListener
    public void onTicketsReturnedToQueue(TicketsReturnedToQueueEvent event) {
        publish(null, QueueChangeReason.RETURNED);
    }

    private void publish(UUID ticketId, QueueChangeReason reason) {
        long openCount = ticketRepository.countByStatus(TicketStatus.OPEN);
        registry.sendToRoles(AUDIENCE, QUEUE_CHANGED, new QueueChangedPayload(openCount, ticketId, reason));
    }
}
