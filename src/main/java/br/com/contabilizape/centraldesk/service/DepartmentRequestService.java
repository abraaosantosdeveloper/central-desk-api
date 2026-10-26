package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.dto.DepartmentRequestResponse;
import br.com.contabilizape.centraldesk.exception.DepartmentRequestNotFoundException;
import br.com.contabilizape.centraldesk.exception.InvalidAccountStatusException;
import br.com.contabilizape.centraldesk.exception.InvalidSessionException;
import br.com.contabilizape.centraldesk.model.DepartmentRequest;
import br.com.contabilizape.centraldesk.model.DepartmentRequestStatus;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.realtime.UserPermissionsChangedEvent;
import br.com.contabilizape.centraldesk.repository.DepartmentRequestRepository;
import br.com.contabilizape.centraldesk.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Fila de pedidos de entrada em departamento (hoje, só a Inovação exige aprovação).
 * Quem pode chamar é decidido no SecurityConfig (só ROLE_ADMIN).
 */
@Service
public class DepartmentRequestService {

    private final DepartmentRequestRepository departmentRequestRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public DepartmentRequestService(DepartmentRequestRepository departmentRequestRepository,
                                    UserRepository userRepository, ApplicationEventPublisher eventPublisher,
                                    Clock clock) {
        this.departmentRequestRepository = departmentRequestRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<DepartmentRequestResponse> listPending() {
        return departmentRequestRepository.findByStatusOrderByCreatedAtAsc(DepartmentRequestStatus.PENDING).stream()
                .map(DepartmentRequestResponse::from)
                .toList();
    }

    /** Quem pediu passa para o departamento; as permissões mudam na próxima renovação da sessão. */
    @Transactional
    public DepartmentRequestResponse approve(UUID requestId, UUID adminId) {
        DepartmentRequest request = findForUpdate(requestId);
        User reviewer = findReviewer(adminId);
        if (!request.getUser().isActive()) {
            throw new InvalidAccountStatusException("A conta de quem fez o pedido não está ativa.");
        }
        request.approve(reviewer, OffsetDateTime.now(clock));
        departmentRequestRepository.flush(); // grava agora e atualiza o updated_at
        eventPublisher.publishEvent(new UserPermissionsChangedEvent(request.getUser().getId()));
        return DepartmentRequestResponse.from(request);
    }

    @Transactional
    public DepartmentRequestResponse reject(UUID requestId, UUID adminId) {
        DepartmentRequest request = findForUpdate(requestId);
        request.reject(findReviewer(adminId), OffsetDateTime.now(clock));
        departmentRequestRepository.flush();
        return DepartmentRequestResponse.from(request);
    }

    // Travado até o fim da transação: dois admins não decidem o mesmo pedido ao mesmo tempo
    private DepartmentRequest findForUpdate(UUID requestId) {
        return departmentRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new DepartmentRequestNotFoundException(requestId));
    }

    private User findReviewer(UUID adminId) {
        return userRepository.findById(adminId).orElseThrow(InvalidSessionException::new);
    }
}
