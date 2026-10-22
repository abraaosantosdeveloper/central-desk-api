package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.dto.AdminUserDetailResponse;
import br.com.contabilizape.centraldesk.dto.AdminUserResponse;
import br.com.contabilizape.centraldesk.dto.AdminUserUpdateRequest;
import br.com.contabilizape.centraldesk.dto.PageResponse;
import br.com.contabilizape.centraldesk.dto.UserFilter;
import br.com.contabilizape.centraldesk.exception.CpfAlreadyRegisteredException;
import br.com.contabilizape.centraldesk.exception.DepartmentNotFoundException;
import br.com.contabilizape.centraldesk.exception.EmailAlreadyRegisteredException;
import br.com.contabilizape.centraldesk.exception.InvalidAccountStatusException;
import br.com.contabilizape.centraldesk.exception.SelfDeactivationException;
import br.com.contabilizape.centraldesk.exception.UserNotFoundException;
import br.com.contabilizape.centraldesk.model.Admin;
import br.com.contabilizape.centraldesk.model.Department;
import br.com.contabilizape.centraldesk.model.DepartmentRequestStatus;
import br.com.contabilizape.centraldesk.model.Ticket;
import br.com.contabilizape.centraldesk.model.TicketStatus;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.model.UserStatus;
import br.com.contabilizape.centraldesk.repository.AdminRepository;
import br.com.contabilizape.centraldesk.repository.DepartmentRepository;
import br.com.contabilizape.centraldesk.repository.DepartmentRequestRepository;
import br.com.contabilizape.centraldesk.repository.TicketRepository;
import br.com.contabilizape.centraldesk.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
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
 * Painel do administrador: consultar, corrigir os dados, desativar e reativar contas.
 * Quem pode chamar é decidido no SecurityConfig (só ROLE_ADMIN).
 */
@Service
public class UserAdministrationService {

    public static final int MAX_PAGE_SIZE = 100;
    private static final Sort BY_NAME = Sort.by("fullName", "email");

    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final DepartmentRequestRepository departmentRequestRepository;
    private final DepartmentRepository departmentRepository;
    private final TicketRepository ticketRepository;
    private final AccountOffboarding offboarding;
    private final Clock clock;
    private final UUID handlerDepartmentId;

    public UserAdministrationService(UserRepository userRepository, AdminRepository adminRepository,
                                     DepartmentRequestRepository departmentRequestRepository,
                                     DepartmentRepository departmentRepository, TicketRepository ticketRepository,
                                     AccountOffboarding offboarding, Clock clock,
                                     @Value("${app.tickets.handler-department-id}") UUID handlerDepartmentId) {
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
        this.departmentRequestRepository = departmentRequestRepository;
        this.departmentRepository = departmentRepository;
        this.ticketRepository = ticketRepository;
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

    /** A conta aberta no painel traz também o CPF, que a tela de edição precisa. */
    @Transactional(readOnly = true)
    public AdminUserDetailResponse get(UUID userId) {
        User user = findUser(userId);
        return AdminUserDetailResponse.from(toResponse(user), user.getCpf());
    }

    /**
     * Corrige nome, CPF, e-mail e departamento de uma conta ativa (SCRUM-176). Vale também para a
     * própria conta do administrador. CPF e e-mail continuam únicos; trocar o e-mail de outra pessoa
     * derruba as sessões dela, porque o e-mail é o login. O departamento segue as mesmas regras da
     * troca isolada (PATCH /users/{id}/department).
     */
    @Transactional
    public AdminUserDetailResponse update(UUID userId, AdminUserUpdateRequest request, UUID adminId) {
        User user = findUser(userId);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidAccountStatusException("Só contas ativas podem ser editadas.");
        }
        Department department = departmentRepository.findById(request.departmentId())
                .orElseThrow(() -> new DepartmentNotFoundException(request.departmentId()));

        String cpf = request.cpf();
        if (!cpf.equals(user.getCpf()) && userRepository.existsByCpf(cpf)) {
            throw new CpfAlreadyRegisteredException();
        }
        String email = Emails.normalize(request.email());
        boolean emailChanged = !email.equals(user.getEmail());
        if (emailChanged && userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }
        if (!department.getId().equals(user.getDepartment().getId())) {
            moveToDepartment(user, department);
        }

        user.rename(request.fullName().trim());
        user.changeCpf(cpf);
        user.changeEmail(email);
        if (emailChanged && !userId.equals(adminId)) {
            offboarding.endSessions(userId);
        }
        return AdminUserDetailResponse.from(toResponse(user), user.getCpf());
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

    /**
     * Troca o departamento da conta (SCRUM-170). Para a Inovação, a decisão do admin já vale como aprovação;
     * quem sai da Inovação devolve à fila os chamados em atendimento.
     */
    @Transactional
    public AdminUserResponse changeDepartment(UUID userId, UUID departmentId) {
        User user = findUser(userId);
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new DepartmentNotFoundException(departmentId));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidAccountStatusException("Só contas ativas podem mudar de departamento.");
        }
        moveToDepartment(user, department);
        return toResponse(user);
    }

    /**
     * Regras comuns da troca de departamento: pedido pendente decide-se antes, administrador fica
     * na Inovação e quem sai da Inovação devolve à fila os chamados em atendimento.
     */
    private void moveToDepartment(User user, Department department) {
        UUID userId = user.getId();
        if (pendingRequest(userId)) {
            throw new InvalidAccountStatusException(
                    "Há um pedido de entrada na Inovação aguardando decisão. Aprove ou recuse o pedido primeiro.");
        }
        boolean toInnovation = department.getId().equals(handlerDepartmentId);
        if (!toInnovation && !department.getId().equals(user.getDepartment().getId())
                && adminRepository.existsById(userId)) {
            throw new InvalidAccountStatusException(
                    "Administradores precisam estar na Inovação. Remova o acesso de administrador antes de trocar o departamento.");
        }

        boolean leavesInnovation = user.getDepartment().getId().equals(handlerDepartmentId) && !toInnovation;
        user.moveTo(department);
        if (leavesInnovation) {
            ticketRepository.findByAssigneeIdAndStatus(userId, TicketStatus.IN_PROGRESS).forEach(Ticket::returnToQueue);
        }
    }

    /**
     * Concede o perfil de administrador (SCRUM-169). Vale a partir do próximo login ou renovação da sessão.
     * Administradores ficam na Inovação (SCRUM-176): quem é de outro departamento muda de departamento antes.
     */
    @Transactional
    public AdminUserResponse grantAdmin(UUID userId, UUID adminId) {
        User user = findUser(userId);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidAccountStatusException("Só contas ativas podem receber acesso de administrador.");
        }
        if (!user.getDepartment().getId().equals(handlerDepartmentId)) {
            throw new InvalidAccountStatusException(
                    "Só contas da Inovação podem receber acesso de administrador. Mude o departamento da pessoa primeiro.");
        }
        if (adminRepository.existsById(userId)) {
            throw new InvalidAccountStatusException("Esta conta já é de administrador.");
        }
        adminRepository.save(new Admin(user, findUser(adminId)));
        return toResponse(user, true, pendingRequest(userId));
    }

    /** Remove o perfil de administrador. Ninguém remove o próprio, então sempre sobra quem fez a remoção. */
    @Transactional
    public AdminUserResponse revokeAdmin(UUID userId, UUID adminId) {
        if (userId.equals(adminId)) {
            throw new InvalidAccountStatusException("Você não pode remover o seu próprio acesso de administrador.");
        }
        User user = findUser(userId);
        if (!adminRepository.existsById(userId)) {
            throw new InvalidAccountStatusException("Esta conta não é de administrador.");
        }
        offboarding.ensureNotLastActiveAdmin(userId);
        adminRepository.deleteById(userId);
        return toResponse(user, false, pendingRequest(userId));
    }

    private boolean pendingRequest(UUID userId) {
        return departmentRequestRepository.existsByUserIdAndStatus(userId, DepartmentRequestStatus.PENDING);
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

    private Specification<User> matching(UserFilter filter) {
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
            if (filter.departmentId() != null) {
                predicates.add(cb.equal(user.get("department").get("id"), filter.departmentId()));
            }
            if (filter.role() != null) {
                Subquery<UUID> admins = query.subquery(UUID.class);
                admins.select(admins.from(Admin.class).get("userId"));
                Predicate isAdmin = user.get("id").in(admins);
                Predicate inInnovation = cb.equal(user.get("department").get("id"), handlerDepartmentId);
                predicates.add(switch (filter.role()) {
                    case ADMIN -> isAdmin;
                    case HANDLER -> cb.and(inInnovation, cb.not(isAdmin));
                    case COLLABORATOR -> cb.and(cb.not(inInnovation), cb.not(isAdmin));
                });
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
