package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.dto.CurrentUserResponse;
import br.com.contabilizape.centraldesk.dto.UserCreateRequest;
import br.com.contabilizape.centraldesk.dto.UserResponse;
import br.com.contabilizape.centraldesk.exception.AccountNotActiveException;
import br.com.contabilizape.centraldesk.exception.CpfAlreadyRegisteredException;
import br.com.contabilizape.centraldesk.exception.DepartmentNotFoundException;
import br.com.contabilizape.centraldesk.exception.EmailAlreadyRegisteredException;
import br.com.contabilizape.centraldesk.exception.InvalidSessionException;
import br.com.contabilizape.centraldesk.model.Department;
import br.com.contabilizape.centraldesk.model.DepartmentRequest;
import br.com.contabilizape.centraldesk.model.DepartmentRequestStatus;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.model.UserStatus;
import br.com.contabilizape.centraldesk.notification.DepartmentRequestCreatedEvent;
import br.com.contabilizape.centraldesk.repository.DepartmentRepository;
import br.com.contabilizape.centraldesk.repository.DepartmentRequestRepository;
import br.com.contabilizape.centraldesk.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final DepartmentRequestRepository departmentRequestRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final UUID handlerDepartmentId;
    private final UUID defaultDepartmentId;

    public UserService(UserRepository userRepository,
                       DepartmentRepository departmentRepository,
                       DepartmentRequestRepository departmentRequestRepository,
                       PasswordEncoder passwordEncoder,
                       ApplicationEventPublisher eventPublisher,
                       @Value("${app.tickets.handler-department-id}") UUID handlerDepartmentId,
                       @Value("${app.departments.default-department-id}") UUID defaultDepartmentId) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.departmentRequestRepository = departmentRequestRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.handlerDepartmentId = handlerDepartmentId;
        this.defaultDepartmentId = defaultDepartmentId;
    }

    @Transactional
    public UserResponse create(UserCreateRequest request) {
        String email = Emails.normalize(request.email());

        validateUniqueness(email, request.cpf());

        Department requestedDepartment = findRequestedDepartment(request.departmentId());
        boolean needsApproval = requiresApproval(requestedDepartment);
        Department initialDepartment = needsApproval ? findDefaultDepartment() : requestedDepartment;

        User user = new User(
                request.fullName().trim(),
                request.cpf(),
                email,
                passwordEncoder.encode(request.password()),
                initialDepartment
        );
        userRepository.saveAndFlush(user); // flush preenche o createdAt antes de montar a resposta

        if (needsApproval) {
            createDepartmentRequest(user, requestedDepartment);
        }

        return UserResponse.from(user, needsApproval);
    }

    /**
     * Dados do usuário logado. As permissões são as do token, calculadas no login.
     */
    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(UUID userId, List<String> roles) {
        User user = userRepository.findById(userId).orElseThrow(InvalidSessionException::new);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountNotActiveException();
        }
        boolean approvalPending =
                departmentRequestRepository.existsByUserIdAndStatus(userId, DepartmentRequestStatus.PENDING);
        return CurrentUserResponse.from(user, approvalPending, roles);
    }

    private void validateUniqueness(String email, String cpf) {
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }
        if (userRepository.existsByCpf(cpf)) {
            throw new CpfAlreadyRegisteredException();
        }
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

    private void createDepartmentRequest(User user, Department requestedDepartment) {
        DepartmentRequest departmentRequest =
                departmentRequestRepository.save(new DepartmentRequest(user, requestedDepartment));

        eventPublisher.publishEvent(new DepartmentRequestCreatedEvent(
                departmentRequest.getId(),
                user.getFullName(),
                requestedDepartment.getName()
        ));
    }
}