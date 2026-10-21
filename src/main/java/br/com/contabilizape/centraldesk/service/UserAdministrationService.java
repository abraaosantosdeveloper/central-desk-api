package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.dto.AdminUserResponse;
import br.com.contabilizape.centraldesk.dto.PageResponse;
import br.com.contabilizape.centraldesk.dto.UserFilter;
import br.com.contabilizape.centraldesk.exception.InvalidAccountStatusException;
import br.com.contabilizape.centraldesk.exception.SelfDeactivationException;
import br.com.contabilizape.centraldesk.exception.UserNotFoundException;
import br.com.contabilizape.centraldesk.model.DepartmentRequestStatus;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.model.UserStatus;
import br.com.contabilizape.centraldesk.repository.AdminRepository;
import br.com.contabilizape.centraldesk.repository.DepartmentRequestRepository;
import br.com.contabilizape.centraldesk.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Painel do administrador: consultar, desativar e reativar contas.
 * Quem pode chamar é decidido no SecurityConfig (só ROLE_ADMIN).
 */
@Service
public class UserAdministrationService {

    public static final int MAX_PAGE_SIZE = 100;
    private static final Sort BY_NAME = Sort.by("fullName", "email");

    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final DepartmentRequestRepository departmentRequestRepository;
    private final AccountOffboarding offboarding;
    private final Clock clock;
    private final UUID handlerDepartmentId;

    public UserAdministrationService(UserRepository userRepository, AdminRepository adminRepository,
                                     DepartmentRequestRepository departmentRequestRepository,
                                     AccountOffboarding offboarding, Clock clock,
                                     @Value("${app.tickets.handler-department-id}") UUID handlerDepartmentId) {
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
        this.departmentRequestRepository = departmentRequestRepository;
        this.offboarding = offboarding;
        this.clock = clock;
        this.handlerDepartmentId = handlerDepartmentId;
    }

    /** Ordem alfabética fixa; sem filtro de status, as contas anonimizadas ficam de fora. */
    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> list(UserFilter filter, Pageable pageable) {
        Pageable page = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), MAX_PAGE_SIZE), BY_NAME);
        // Poucos admins e poucos pedidos pendentes: duas consultas em vez de uma por conta
        Set<UUID> adminIds = adminRepository.findAllUserIds();
        Set<UUID> pendingIds = departmentRequestRepository.findUserIdsByStatus(DepartmentRequestStatus.PENDING);
        return PageResponse.from(userRepository.findAll(matching(filter), page),
                user -> toResponse(user, adminIds.contains(user.getId()), pendingIds.contains(user.getId())));
    }

    @Transactional(readOnly = true)
    public AdminUserResponse get(UUID userId) {
        return toResponse(findUser(userId));
    }

    /**
     * Bloqueia o login na hora: sessões revogadas (o access token em uso já recebe 403, porque
     * as rotas conferem o status) e chamados em atendimento de volta à fila.
     */
    @Transactional
    public AdminUserResponse deactivate(UUID userId, UUID adminId) {
        if (userId.equals(adminId)) {
            throw new SelfDeactivationException();
        }
        User user = findUser(userId);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidAccountStatusException("Só contas ativas podem ser desativadas.");
        }
        offboarding.ensureNotLastActiveAdmin(userId);

        user.deactivate(OffsetDateTime.now(clock));
        offboarding.release(userId);
        return toResponse(user);
    }

    @Transactional
    public AdminUserResponse reactivate(UUID userId) {
        User user = findUser(userId);
        if (user.getStatus() != UserStatus.DEACTIVATED) {
            throw new InvalidAccountStatusException("Só contas desativadas podem ser reativadas.");
        }
        user.reactivate();
        return toResponse(user);
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    }

    private AdminUserResponse toResponse(User user) {
        boolean pending = departmentRequestRepository.existsByUserIdAndStatus(user.getId(), DepartmentRequestStatus.PENDING);
        return toResponse(user, adminRepository.existsById(user.getId()), pending);
    }

    private AdminUserResponse toResponse(User user, boolean admin, boolean approvalPending) {
        boolean ticketHandler = user.getDepartment().getId().equals(handlerDepartmentId);
        return AdminUserResponse.from(user, admin, ticketHandler, approvalPending);
    }

    private static Specification<User> matching(UserFilter filter) {
        return (user, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.status() != null) {
                predicates.add(cb.equal(user.get("status"), filter.status()));
            } else {
                predicates.add(cb.notEqual(user.get("status"), UserStatus.ANONYMIZED));
            }
            if (filter.search() != null && !filter.search().isBlank()) {
                String pattern = "%" + filter.search().trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(user.get("fullName")), pattern),
                        cb.like(cb.lower(user.get("email")), pattern)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
