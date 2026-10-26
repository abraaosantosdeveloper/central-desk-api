package br.com.contabilizape.centraldesk.realtime;

import br.com.contabilizape.centraldesk.event.DepartmentRequestApprovedEvent;
import br.com.contabilizape.centraldesk.event.DepartmentRequestRejectedEvent;
import br.com.contabilizape.centraldesk.model.DepartmentRequestStatus;
import br.com.contabilizape.centraldesk.notification.DepartmentRequestCreatedEvent;
import br.com.contabilizape.centraldesk.repository.DepartmentRequestRepository;
import br.com.contabilizape.centraldesk.security.Roles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pedidos de entrada na Inovação: a tela de Aprovações (e o círculo do menu) recebem a contagem pronta,
 * em vez de buscá-la uma única vez ao carregar a página.
 */
@ExtendWith(MockitoExtension.class)
class DepartmentRequestRealtimeListenerTest {

    private static final Set<String> AUDIENCE = Set.of(Roles.ADMIN);
    private static final UUID REQUEST_ID = UUID.fromString("cccccccc-0000-4000-8000-000000000003");

    @Mock private DepartmentRequestRepository departmentRequestRepository;
    @Mock private SseEmitterRegistry registry;

    private DepartmentRequestRealtimeListener listener;

    @BeforeEach
    void setUp() {
        listener = new DepartmentRequestRealtimeListener(departmentRequestRepository, registry);
    }

    @Test
    void createdRequestSendsThePendingCountToTheAdminsOnly() {
        when(departmentRequestRepository.countByStatus(DepartmentRequestStatus.PENDING)).thenReturn(2L);

        listener.onCreated(new DepartmentRequestCreatedEvent(REQUEST_ID, "Maria da Silva", "Inovação"));

        verify(registry).sendToRoles(AUDIENCE, "department-requests.changed",
                new DepartmentRequestsChangedPayload(2, REQUEST_ID, DepartmentRequestChangeReason.CREATED));
    }

    @Test
    void approvedRequestSendsThePendingCount() {
        when(departmentRequestRepository.countByStatus(DepartmentRequestStatus.PENDING)).thenReturn(1L);

        listener.onApproved(new DepartmentRequestApprovedEvent(UUID.randomUUID(), REQUEST_ID));

        verify(registry).sendToRoles(AUDIENCE, "department-requests.changed",
                new DepartmentRequestsChangedPayload(1, REQUEST_ID, DepartmentRequestChangeReason.APPROVED));
    }

    @Test
    void rejectedRequestSendsThePendingCount() {
        when(departmentRequestRepository.countByStatus(DepartmentRequestStatus.PENDING)).thenReturn(0L);

        listener.onRejected(new DepartmentRequestRejectedEvent(UUID.randomUUID(), REQUEST_ID));

        verify(registry).sendToRoles(AUDIENCE, "department-requests.changed",
                new DepartmentRequestsChangedPayload(0, REQUEST_ID, DepartmentRequestChangeReason.REJECTED));
    }
}
