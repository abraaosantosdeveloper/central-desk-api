package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.dto.CurrentUserResponse;
import br.com.contabilizape.centraldesk.dto.UserCreateRequest;
import br.com.contabilizape.centraldesk.dto.UserProfileUpdateRequest;
import br.com.contabilizape.centraldesk.dto.UserResponse;
import br.com.contabilizape.centraldesk.exception.AccountNotActiveException;
import br.com.contabilizape.centraldesk.exception.CpfAlreadyRegisteredException;
import br.com.contabilizape.centraldesk.exception.DepartmentNotFoundException;
import br.com.contabilizape.centraldesk.exception.EmailAlreadyRegisteredException;
import br.com.contabilizape.centraldesk.exception.InvalidSessionException;
import br.com.contabilizape.centraldesk.model.CodePurpose;
import br.com.contabilizape.centraldesk.model.Department;
import br.com.contabilizape.centraldesk.model.DepartmentRequest;
import br.com.contabilizape.centraldesk.model.DepartmentRequestStatus;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.notification.DepartmentRequestCreatedEvent;
import br.com.contabilizape.centraldesk.repository.DepartmentRepository;
import br.com.contabilizape.centraldesk.repository.DepartmentRequestRepository;
import br.com.contabilizape.centraldesk.repository.UserRepository;
import br.com.contabilizape.centraldesk.security.AccountSecurityPolicy;
import br.com.contabilizape.centraldesk.security.ClientInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final DepartmentRequestRepository departmentRequestRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final AccountSecurityPolicy policy;
    private final VerificationCodeService codes;
    private final Clock clock;
    private final UUID handlerDepartmentId;
    private final UUID defaultDepartmentId;

    public UserService(UserRepository userRepository,
                       DepartmentRepository departmentRepository,
                       DepartmentRequestRepository departmentRequestRepository,
                       PasswordEncoder passwordEncoder,
                       ApplicationEventPublisher eventPublisher,
                       AccountSecurityPolicy policy,
                       VerificationCodeService codes,
                       Clock clock,
                       @Value("${app.tickets.handler-department-id}") UUID handlerDepartmentId,
                       @Value("${app.departments.default-department-id}") UUID defaultDepartmentId) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.departmentRequestRepository = departmentRequestRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.policy = policy;
        this.codes = codes;
        this.clock = clock;
        this.handlerDepartmentId = handlerDepartmentId;
        this.defaultDepartmentId = defaultDepartmentId;
    }

    /**
     * Cadastro (SCRUM-205). A conta nasce PENDING_VERIFICATION e recebe um código por e-mail; só entra
     * depois de confirmar (EmailVerificationService). O pedido de entrada na Inovação fica guardado e só
     * é criado na confirmação. Um cadastro pendente com o mesmo e-mail ou CPF é substituído: quem tem
     * acesso à caixa de e-mail ganha (B3). Contas confirmadas nunca são substituídas (409).
     */
    @Transactional
    public UserResponse create(UserCreateRequest request, ClientInfo client) {
        String email = Emails.normalize(request.email());
        User byEmail = userRepository.findByEmail(email).orElse(null);
        if (byEmail != null && !byEmail.isPendingVerification()) {
            throw new EmailAlreadyRegisteredException();
        }
        User byCpf = userRepository.findByCpf(request.cpf()).orElse(null);
        if (byCpf != null && !byCpf.isPendingVerification()) {
            throw new CpfAlreadyRegisteredException();
        }

        Department requestedDepartment = findRequestedDepartment(request.departmentId());
        boolean needsApproval = requiresApproval(requestedDepartment);
        Department initialDepartment = needsApproval ? findDefaultDepartment() : requestedDepartment;
        String fullName = request.fullName().trim();
        String passwordHash = passwordEncoder.encode(request.password());

        User user;
        if (byEmail != null || byCpf != null) {
            user = byEmail != null ? byEmail : byCpf;
            if (byEmail != null && byCpf != null && byEmail != byCpf) {
                // Dois cadastros pendentes (um com o e-mail, outro com o CPF): fica um só
                userRepository.delete(byCpf);
                userRepository.flush();
            }
            user.replacePendingRegistration(fullName, request.cpf(), email, passwordHash, initialDepartment);
        } else {
            user = new User(fullName, request.cpf(), email, passwordHash, initialDepartment);
            user.awaitEmailConfirmation();
            if (!policy.emailVerificationRequired()) {
                user.confirmEmail(OffsetDateTime.now(clock));
            }
        }
        userRepository.saveAndFlush(user); // flush preenche o createdAt antes de montar a resposta

        if (user.isPendingVerification()) {
            if (needsApproval) {
                user.requestDepartment(requestedDepartment);
            }
            codes.send(user, CodePurpose.EMAIL_VERIFICATION, client.ip());
        } else if (needsApproval) {
            createDepartmentRequest(user, requestedDepartment);
        }

        return UserResponse.from(user, needsApproval);
    }

    /**
     * Dados do usuário logado. As permissões são as do token, calculadas no login.
     */
    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(UUID userId, List<String> roles) {
        return toCurrentUser(findActiveUser(userId), roles);
    }

    /** Edição do próprio perfil: só o nome. CPF, e-mail, departamento e permissões não mudam aqui. */
    @Transactional
    public CurrentUserResponse updateProfile(UUID userId, UserProfileUpdateRequest request, List<String> roles) {
        User user = findActiveUser(userId);
        user.rename(request.fullName().trim());
        return toCurrentUser(user, roles);
    }

    /** Usuário do token: apagado → sessão inválida (401); conta não ativa → 403. */
    private User findActiveUser(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(InvalidSessionException::new);
        if (!user.isActive()) {
            throw new AccountNotActiveException();
        }
        return user;
    }

    private CurrentUserResponse toCurrentUser(User user, List<String> roles) {
        boolean approvalPending =
                departmentRequestRepository.existsByUserIdAndStatus(user.getId(), DepartmentRequestStatus.PENDING);
        return CurrentUserResponse.from(user, approvalPending, roles);
    }

    private Department findRequestedDepartment(UUID departmentId) {
        return departmentRepository.findById(departmentId)
                .orElseThrow(() -> new DepartmentNotFoundException(departmentId));
    }

    private boolean requiresApproval(Department department) {
        return department.getId().equals(handlerDepartmentId);
    }

    // Quem pede a Inovação começa no Geral até um admin aprovar
    private Department findDefaultDepartment() {
        return departmentRepository.findById(defaultDepartmentId)
                .orElseThrow(() -> new IllegalStateException(
                        "Departamento padrão não encontrado: verifique a V8 e o application.properties"));
    }

    /** Pedido de entrada na Inovação; os admins são avisados. Também usado na confirmação do e-mail. */
    void createDepartmentRequest(User user, Department requestedDepartment) {
        DepartmentRequest departmentRequest =
                departmentRequestRepository.save(new DepartmentRequest(user, requestedDepartment));

        eventPublisher.publishEvent(new DepartmentRequestCreatedEvent(
                departmentRequest.getId(),
                user.getFullName(),
                requestedDepartment.getName()
        ));
    }
}