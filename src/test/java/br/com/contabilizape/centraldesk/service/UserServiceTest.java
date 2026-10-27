package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.model.CodePurpose;
import br.com.contabilizape.centraldesk.security.AccountSecurityPolicy;
import br.com.contabilizape.centraldesk.security.ClientInfo;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import br.com.contabilizape.centraldesk.dto.CurrentUserResponse;
import br.com.contabilizape.centraldesk.dto.UserCreateRequest;
import br.com.contabilizape.centraldesk.dto.UserProfileUpdateRequest;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final UUID INNOVATION_ID = UUID.fromString("e9790f1f-41df-4369-bfe7-1be4be535561");
    private static final UUID GENERAL_ID = UUID.fromString("3094ed3f-42ab-4bc0-9d8d-f6eb08891016");
    private static final UUID FINANCE_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-27T12:00:00Z");
    private static final ClientInfo CLIENT = new ClientInfo("203.0.113.7", "Chrome");

    @Mock private UserRepository userRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private DepartmentRequestRepository departmentRequestRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private AccountSecurityPolicy policy;
    @Mock private VerificationCodeService codes;

    private UserService userService;

    private final Department innovation = department(INNOVATION_ID, "Inovação");
    private final Department general = department(GENERAL_ID, "Geral");
    private final Department finance = department(FINANCE_ID, "Financeiro");

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository, departmentRepository, departmentRequestRepository,
                passwordEncoder, eventPublisher, policy, codes, Clock.fixed(NOW, ZoneOffset.UTC),
                INNOVATION_ID, GENERAL_ID);
    }

    // ---------- caminho feliz ----------

    @Test
    void createsActiveUserInRequestedDepartment() {
        when(departmentRepository.findById(FINANCE_ID)).thenReturn(Optional.of(finance));
        when(passwordEncoder.encode("senhaForte123")).thenReturn("hash-bcrypt");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = userService.create(request("maria.silva@contabilizape.com.br", FINANCE_ID), CLIENT);

        User saved = savedUser();
        assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.getDepartment()).isSameAs(finance);
        assertThat(saved.getPasswordHash()).isEqualTo("hash-bcrypt");
        assertThat(response.departmentId()).isEqualTo(FINANCE_ID);
        assertThat(response.departmentApprovalPending()).isFalse();
        verify(departmentRequestRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void normalizesEmailAndTrimsName() {
        when(departmentRepository.findById(FINANCE_ID)).thenReturn(Optional.of(finance));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.create(new UserCreateRequest(
                "  Maria da Silva  ", "52998224725", "  Maria.Silva@ContabilizaPE.com.br ",
                "senhaForte123", FINANCE_ID), CLIENT);

        User saved = savedUser();
        assertThat(saved.getEmail()).isEqualTo("maria.silva@contabilizape.com.br");
        assertThat(saved.getFullName()).isEqualTo("Maria da Silva");
    }

    @Test
    void checksDuplicateEmailUsingNormalizedValue() {
        when(userRepository.findByEmail("maria.silva@contabilizape.com.br"))
                .thenReturn(Optional.of(existingUser(UUID.randomUUID(), finance, UserStatus.ACTIVE)));

        assertThatThrownBy(() -> userService.create(request("MARIA.SILVA@contabilizape.com.br", FINANCE_ID), CLIENT))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    // ---------- Inovação exige aprovação ----------

    @Test
    void innovationRequestStartsInGeneralWithPendingRequest() {
        when(departmentRepository.findById(INNOVATION_ID)).thenReturn(Optional.of(innovation));
        when(departmentRepository.findById(GENERAL_ID)).thenReturn(Optional.of(general));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(departmentRequestRepository.save(any(DepartmentRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = userService.create(request("joao.souza@contabilizape.com.br", INNOVATION_ID), CLIENT);

        User saved = savedUser();
        assertThat(saved.getDepartment()).isSameAs(general);
        assertThat(response.departmentId()).isEqualTo(GENERAL_ID);
        assertThat(response.departmentApprovalPending()).isTrue();

        ArgumentCaptor<DepartmentRequest> requestCaptor = ArgumentCaptor.forClass(DepartmentRequest.class);
        verify(departmentRequestRepository).save(requestCaptor.capture());
        DepartmentRequest pending = requestCaptor.getValue();
        assertThat(pending.getUser()).isSameAs(saved);
        assertThat(pending.getDepartment()).isSameAs(innovation);
        assertThat(pending.getStatus()).isEqualTo(DepartmentRequestStatus.PENDING);
    }

    @Test
    void innovationRequestPublishesEventForAdmins() {
        when(departmentRepository.findById(INNOVATION_ID)).thenReturn(Optional.of(innovation));
        when(departmentRepository.findById(GENERAL_ID)).thenReturn(Optional.of(general));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(departmentRequestRepository.save(any(DepartmentRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        userService.create(request("joao.souza@contabilizape.com.br", INNOVATION_ID), CLIENT);

        ArgumentCaptor<DepartmentRequestCreatedEvent> eventCaptor =
                ArgumentCaptor.forClass(DepartmentRequestCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().requesterName()).isEqualTo("Maria da Silva");
        assertThat(eventCaptor.getValue().departmentName()).isEqualTo("Inovação");
    }

    @Test
    void failsLoudlyWhenDefaultDepartmentIsMissing() {
        when(departmentRepository.findById(INNOVATION_ID)).thenReturn(Optional.of(innovation));
        when(departmentRepository.findById(GENERAL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.create(request("joao.souza@contabilizape.com.br", INNOVATION_ID), CLIENT))
                .isInstanceOf(IllegalStateException.class);
        verify(userRepository, never()).saveAndFlush(any());
    }

    // ---------- recusas ----------

    // O domínio do e-mail é validado no DTO (@CorporateEmail); ver CorporateEmailValidatorTest

    @Test
    void rejectsDuplicateEmail() {
        when(userRepository.findByEmail("maria.silva@contabilizape.com.br"))
                .thenReturn(Optional.of(existingUser(UUID.randomUUID(), finance, UserStatus.ACTIVE)));

        assertThatThrownBy(() -> userService.create(request("maria.silva@contabilizape.com.br", FINANCE_ID), CLIENT))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsDuplicateCpf() {
        when(userRepository.findByCpf("52998224725"))
                .thenReturn(Optional.of(existingUser(UUID.randomUUID(), finance, UserStatus.DEACTIVATED)));

        assertThatThrownBy(() -> userService.create(request("maria.silva@contabilizape.com.br", FINANCE_ID), CLIENT))
                .isInstanceOf(CpfAlreadyRegisteredException.class);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsUnknownDepartment() {
        UUID unknown = UUID.randomUUID();
        when(departmentRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.create(request("maria.silva@contabilizape.com.br", unknown), CLIENT))
                .isInstanceOf(DepartmentNotFoundException.class);
        verify(userRepository, never()).saveAndFlush(any());
    }

    // ---------- SCRUM-205: confirmação do e-mail no cadastro ----------

    @Test
    void withVerificationTheAccountWaitsForTheCodeAndNoRequestIsCreatedYet() {
        when(policy.emailVerificationRequired()).thenReturn(true);
        when(departmentRepository.findById(INNOVATION_ID)).thenReturn(Optional.of(innovation));
        when(departmentRepository.findById(GENERAL_ID)).thenReturn(Optional.of(general));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = userService.create(request("joao.souza@contabilizape.com.br", INNOVATION_ID), CLIENT);

        User saved = savedUser();
        assertThat(saved.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
        assertThat(response.status()).isEqualTo(UserStatus.PENDING_VERIFICATION);
        assertThat(saved.getDepartment()).isSameAs(general);
        // O pedido da Inovação só nasce depois da confirmação (não polui Aprovações com cadastros abandonados)
        verify(departmentRequestRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
        verify(codes).send(saved, CodePurpose.EMAIL_VERIFICATION, CLIENT.ip());
        assertThat(saved.takeRequestedDepartment()).isSameAs(innovation);
    }

    @Test
    void withoutVerificationTheAccountIsActiveAndConfirmedRightAway() {
        when(departmentRepository.findById(FINANCE_ID)).thenReturn(Optional.of(finance));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.create(request("maria.silva@contabilizape.com.br", FINANCE_ID), CLIENT);

        assertThat(savedUser().getEmailVerifiedAt()).isEqualTo(NOW.atOffset(ZoneOffset.UTC));
        verify(codes, never()).send(any(), any(), any());
    }

    @Test
    void newRegistrationReplacesAPendingOneWithTheSameEmail() {
        User pending = existingUser(UUID.randomUUID(), general, UserStatus.PENDING_VERIFICATION);
        when(userRepository.findByEmail("maria.silva@contabilizape.com.br")).thenReturn(Optional.of(pending));
        when(userRepository.findByCpf("52998224725")).thenReturn(Optional.of(pending));
        when(departmentRepository.findById(FINANCE_ID)).thenReturn(Optional.of(finance));
        when(passwordEncoder.encode("senhaForte123")).thenReturn("hash-novo");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.create(request("maria.silva@contabilizape.com.br", FINANCE_ID), CLIENT);

        // Quem tem acesso à caixa de e-mail ganha (B3): a senha e o departamento passam a ser os novos
        assertThat(savedUser()).isSameAs(pending);
        assertThat(pending.getPasswordHash()).isEqualTo("hash-novo");
        assertThat(pending.getDepartment()).isSameAs(finance);
        verify(codes).send(pending, CodePurpose.EMAIL_VERIFICATION, CLIENT.ip());
    }

    @Test
    void pendingRegistrationWithTheSameCpfAndAnotherEmailIsDiscarded() {
        User pendingByCpf = existingUser(UUID.randomUUID(), general, UserStatus.PENDING_VERIFICATION);
        ReflectionTestUtils.setField(pendingByCpf, "email", "outro.email@contabilizape.com.br");
        when(userRepository.findByEmail("maria.silva@contabilizape.com.br")).thenReturn(Optional.empty());
        when(userRepository.findByCpf("52998224725")).thenReturn(Optional.of(pendingByCpf));
        when(departmentRepository.findById(FINANCE_ID)).thenReturn(Optional.of(finance));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.create(request("maria.silva@contabilizape.com.br", FINANCE_ID), CLIENT);

        assertThat(savedUser()).isSameAs(pendingByCpf);
        assertThat(pendingByCpf.getEmail()).isEqualTo("maria.silva@contabilizape.com.br");
    }

    @Test
    void confirmedAccountsAreNeverReplaced() {
        when(userRepository.findByEmail("maria.silva@contabilizape.com.br"))
                .thenReturn(Optional.of(existingUser(UUID.randomUUID(), finance, UserStatus.BLOCKED)));

        assertThatThrownBy(() -> userService.create(request("maria.silva@contabilizape.com.br", FINANCE_ID), CLIENT))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
        verify(codes, never()).send(any(), any(), any());
    }

    // ---------- usuário logado (/users/me) ----------

    @Test
    void returnsCurrentUserWithRolesFromToken() {
        UUID id = UUID.randomUUID();
        User maria = existingUser(id, general, UserStatus.ACTIVE);
        when(userRepository.findById(id)).thenReturn(Optional.of(maria));
        when(departmentRequestRepository.existsByUserIdAndStatus(id, DepartmentRequestStatus.PENDING)).thenReturn(true);

        CurrentUserResponse current = userService.getCurrentUser(id, List.of("ROLE_ADMIN"));

        assertThat(current.id()).isEqualTo(id);
        assertThat(current.email()).isEqualTo("maria.silva@contabilizape.com.br");
        assertThat(current.departmentName()).isEqualTo("Geral");
        assertThat(current.departmentApprovalPending()).isTrue();
        assertThat(current.roles()).containsExactly("ROLE_ADMIN");
    }

    @Test
    void currentUserThatNoLongerExistsIsInvalidSession() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getCurrentUser(id, List.of()))
                .isInstanceOf(InvalidSessionException.class);
    }

    @Test
    void currentUserDeactivatedAfterLoginIsRejected() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.of(existingUser(id, general, UserStatus.DEACTIVATED)));

        assertThatThrownBy(() -> userService.getCurrentUser(id, List.of()))
                .isInstanceOf(AccountNotActiveException.class);
    }


    // ---------- edição do perfil (PUT /users/me) ----------

    @Test
    void updateProfileRenamesTheUserAndReturnsCurrentUser() {
        UUID id = UUID.randomUUID();
        User maria = existingUser(id, general, UserStatus.ACTIVE);
        when(userRepository.findById(id)).thenReturn(Optional.of(maria));

        CurrentUserResponse current = userService.updateProfile(id, new UserProfileUpdateRequest("  Maria Souza  "), List.of());

        assertThat(maria.getFullName()).isEqualTo("Maria Souza");
        assertThat(current.fullName()).isEqualTo("Maria Souza");
        assertThat(maria.getEmail()).isEqualTo("maria.silva@contabilizape.com.br");
        assertThat(maria.getCpf()).isEqualTo("52998224725");
    }

    @Test
    void updateProfileOfMissingUserIsInvalidSession() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateProfile(id, new UserProfileUpdateRequest("Maria"), List.of()))
                .isInstanceOf(InvalidSessionException.class);
    }

    @Test
    void updateProfileOfDeactivatedUserIsRejected() {
        UUID id = UUID.randomUUID();
        User maria = existingUser(id, general, UserStatus.DEACTIVATED);
        when(userRepository.findById(id)).thenReturn(Optional.of(maria));

        assertThatThrownBy(() -> userService.updateProfile(id, new UserProfileUpdateRequest("Outro Nome"), List.of()))
                .isInstanceOf(AccountNotActiveException.class);
        assertThat(maria.getFullName()).isEqualTo("Maria da Silva");
    }
    // ---------- auxiliares ----------

    private static UserCreateRequest request(String email, UUID departmentId) {
        return new UserCreateRequest("Maria da Silva", "52998224725", email, "senhaForte123", departmentId);
    }

    private User savedUser() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private static User existingUser(UUID id, Department department, UserStatus status) {
        User user = new User("Maria da Silva", "52998224725", "maria.silva@contabilizape.com.br", "hash", department);
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "status", status);
        return user;
    }

    // Department só tem construtor protegido (exigência do JPA); no teste usamos um mock
    private static Department department(UUID id, String name) {
        Department department = mock(Department.class, withSettings().strictness(Strictness.LENIENT));
        when(department.getId()).thenReturn(id);
        when(department.getName()).thenReturn(name);
        return department;
    }
}
