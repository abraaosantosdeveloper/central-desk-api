package br.com.contabilizape.centraldesk.realtime;

import br.com.contabilizape.centraldesk.event.TicketAssignedEvent;
import br.com.contabilizape.centraldesk.event.TicketClosedEvent;
import br.com.contabilizape.centraldesk.event.TicketOpenedEvent;
import br.com.contabilizape.centraldesk.event.TicketReassignedEvent;
import br.com.contabilizape.centraldesk.event.TicketsReturnedToQueueEvent;
import br.com.contabilizape.centraldesk.model.TicketStatus;
import br.com.contabilizape.centraldesk.repository.TicketRepository;
import br.com.contabilizape.centraldesk.security.Roles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * O servidor manda a contagem pronta (openCount) e o cliente só a substitui: somar e subtrair no cliente
 * deriva com qualquer evento perdido, duplicado ou fora de ordem.
 */
@ExtendWith(MockitoExtension.class)
class QueueRealtimeListenerTest {

    private static final Set<String> QUEUE_AUDIENCE = Set.of(Roles.TICKET_HANDLER, Roles.ADMIN);
    private static final UUID TICKET_ID = UUID.fromString("dddddddd-0000-4000-8000-000000000004");
    private static final UUID PERSON_ID = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");

    @Mock private TicketRepository ticketRepository;
    @Mock private SseEmitterRegistry registry;

    private QueueRealtimeListener listener;

    @BeforeEach
    void setUp() {
        listener = new QueueRealtimeListener(ticketRepository, registry);
    }

    @Test
    void openedTicketSendsTheCurrentCountWithReasonOpened() {
        when(ticketRepository.countByStatus(TicketStatus.OPEN)).thenReturn(3L);

        listener.onTicketOpened(new TicketOpenedEvent(UUID.randomUUID(), TICKET_ID, PERSON_ID));

        verify(registry).sendToRoles(QUEUE_AUDIENCE, "queue.changed",
                new QueueChangedPayload(3, TICKET_ID, QueueChangeReason.OPENED));
    }

    @Test
    void assignedTicketSendsTheCountWithReasonAssigned() {
        when(ticketRepository.countByStatus(TicketStatus.OPEN)).thenReturn(2L);

        listener.onTicketAssigned(new TicketAssignedEvent(UUID.randomUUID(), TICKET_ID, PERSON_ID, UUID.randomUUID()));

        verify(registry).sendToRoles(QUEUE_AUDIENCE, "queue.changed",
                new QueueChangedPayload(2, TICKET_ID, QueueChangeReason.ASSIGNED));
    }

    @Test
    void reassigningATicketFromTheQueueCountsAsAssigned() {
        when(ticketRepository.countByStatus(TicketStatus.OPEN)).thenReturn(1L);

        listener.onTicketReassigned(reassigned(true));

        verify(registry).sendToRoles(QUEUE_AUDIENCE, "queue.changed",
                new QueueChangedPayload(1, TICKET_ID, QueueChangeReason.ASSIGNED));
    }

    @Test
    void reassigningATicketInProgressDoesNotTouchTheQueue() {
        listener.onTicketReassigned(reassigned(false));

        verifyNoInteractions(registry);
        verify(ticketRepository, never()).countByStatus(any());
    }

    @Test
    void closingATicketThatWasInTheQueueSendsTheCountWithReasonClosed() {
        when(ticketRepository.countByStatus(TicketStatus.OPEN)).thenReturn(4L);

        listener.onTicketClosed(closed(true));

        verify(registry).sendToRoles(QUEUE_AUDIENCE, "queue.changed",
                new QueueChangedPayload(4, TICKET_ID, QueueChangeReason.CLOSED));
    }

    @Test
    void closingATicketInProgressDoesNotTouchTheQueue() {
        listener.onTicketClosed(closed(false));

        verifyNoInteractions(registry);
        verify(ticketRepository, never()).countByStatus(any());
    }

    @Test
    void ticketsReturnedToTheQueueSendOneEventWithoutASingleTicketId() {
        when(ticketRepository.countByStatus(TicketStatus.OPEN)).thenReturn(5L);

        listener.onTicketsReturnedToQueue(new TicketsReturnedToQueueEvent(UUID.randomUUID(), List.of(TICKET_ID, UUID.randomUUID())));

        verify(registry).sendToRoles(QUEUE_AUDIENCE, "queue.changed",
                new QueueChangedPayload(5, null, QueueChangeReason.RETURNED));
        verify(registry, never()).sendToUsers(any(), anyString(), any());
    }

    private static TicketReassignedEvent reassigned(boolean fromQueue) {
        return new TicketReassignedEvent(UUID.randomUUID(), TICKET_ID, PERSON_ID, UUID.randomUUID(), UUID.randomUUID(), fromQueue,
                fromQueue ? null : UUID.randomUUID());
    }

    private static TicketClosedEvent closed(boolean wasOpen) {
        return new TicketClosedEvent(UUID.randomUUID(), TICKET_ID, PERSON_ID, UUID.randomUUID(), wasOpen);
    }
}
