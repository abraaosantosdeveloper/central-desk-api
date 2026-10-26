package br.com.contabilizape.centraldesk.realtime;

import br.com.contabilizape.centraldesk.event.DepartmentRequestApprovedEvent;
import br.com.contabilizape.centraldesk.event.DepartmentRequestRejectedEvent;
import br.com.contabilizape.centraldesk.model.DepartmentRequestStatus;
import br.com.contabilizape.centraldesk.notification.DepartmentRequestCreatedEvent;
import br.com.contabilizape.centraldesk.repository.DepartmentRequestRepository;
import br.com.contabilizape.centraldesk.security.Roles;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Set;
import java.util.UUID;

/**
 * Avisa os administradores conectados de que os pedidos pendentes mudaram, já com a contagem.
 * Roda depois do commit. Resolve a tela de Aprovações que só atualizava com F5.
 */
@Component
public class DepartmentRequestRealtimeListener {

    public static final String DEPARTMENT_REQUESTS_CHANGED = "department-requests.changed";

    private static final Set<String> AUDIENCE = Set.of(Roles.ADMIN);

    private final DepartmentRequestRepository departmentRequestRepository;
    private final SseEmitterRegistry registry;

    public DepartmentRequestRealtimeListener(DepartmentRequestRepository departmentRequestRepository,
                                             SseEmitterRegistry registry) {
        this.departmentRequestRepository = departmentRequestRepository;
        this.registry = registry;
    }

    @TransactionalEventListener
    public void onCreated(DepartmentRequestCreatedEvent event) {
        publish(event.requestId(), DepartmentRequestChangeReason.CREATED);
    }

    @TransactionalEventListener
    public void onApproved(DepartmentRequestApprovedEvent event) {
        publish(event.requestId(), DepartmentRequestChangeReason.APPROVED);
    }

    @TransactionalEventListener
    public void onRejected(DepartmentRequestRejectedEvent event) {
        publish(event.requestId(), DepartmentRequestChangeReason.REJECTED);
    }

    private void publish(UUID requestId, DepartmentRequestChangeReason reason) {
        long pendingCount = departmentRequestRepository.countByStatus(DepartmentRequestStatus.PENDING);
        registry.sendToRoles(AUDIENCE, DEPARTMENT_REQUESTS_CHANGED,
                new DepartmentRequestsChangedPayload(pendingCount, requestId, reason));
    }
}
