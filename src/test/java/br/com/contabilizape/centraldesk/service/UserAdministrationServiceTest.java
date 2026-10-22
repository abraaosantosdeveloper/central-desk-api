package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.dto.AdminUserDetailResponse;
import br.com.contabilizape.centraldesk.dto.AdminUserResponse;
import br.com.contabilizape.centraldesk.dto.AdminUserUpdateRequest;
import br.com.contabilizape.centraldesk.exception.CpfAlreadyRegisteredException;
import br.com.contabilizape.centraldesk.exception.EmailAlreadyRegisteredException;
import br.com.contabilizape.centraldesk.exception.InvalidAccountStatusException;
import br.com.contabilizape.centraldesk.exception.LastAdminException;
import br.com.contabilizape.centraldesk.exception.SelfDeactivationException;
import br.com.contabilizape.centraldesk.exception.UserNotFoundException;
import br.com.contabilizape.centraldesk.model.Category;
import br.com.contabilizape.centraldesk.model.Department;
import br.com.contabilizape.centraldesk.model.DepartmentRequestStatus;
import br.com.contabilizape.centraldesk.model.Priority;
import br.com.contabilizape.centraldesk.model.Ticket;
import br.com.contabilizape.centraldesk.model.TicketStatus;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.model.UserStatus;
import br.com.contabilizape.centraldesk.repository.AdminRepository;
import br.com.contabilizape.centraldesk.repository.DepartmentRepository;
import br.com.contabilizape.centraldesk.repository.DepartmentRequestRepository;
import br.com.contabilizape.centraldesk.repository.TicketRepository;
import br.com.contabilizape.centraldesk.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@ExtendWith(MockitoExtension.class)
class UserAdministrationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");
    private static final UUID INNOVATION_ID = UUID.fromString("e9790f1f-41df-4369-bfe7-1be4be535561");
    private static final UUID GENERAL_ID = UUID.fromString("3094ed3f-42ab-4bc0-9d8d-f6eb08891016");
    private static final UUID ADMIN_ID = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");
    private static final UUID TARGET_ID = UUID.fromString("bbbbbbbb-0000-4000-8000-000000000002");

    @Mock private UserRepository userRepository;
    @Mock private AdminRepository adminRepository;
    @Mock private DepartmentRequestRepository departmentRequestRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private RefreshTokenService refreshTokenService;

    private UserAdministrationService service;

    private final Department general = department(GENERAL_ID, "Geral");
    private final Department innovation = department(INNOVATION_ID, "Inovação");

    @BeforeEach
    void setUp() {
        AccountOffboarding offboarding = new AccountOffboarding(userRepository, adminRepository, ticketRepository,
                refreshTokenService);
        service = new UserAdministrationService(userRepository, adminRepository, departmentRequestRepository,
                departmentRepository, ticketRepository,
                offboarding, Clock.fixed(NOW, ZoneOffset.UTC), INNOVATION_ID);
    }

    // ---------- desativar ----------

    @Test
    void deactivatingEndsSessionsAndReturnsTicketsToTheQueue() {
        User target = user(TARGET_ID, innovation, UserStatus.ACTIVE);
        Ticket inProgress = ticketAssignedTo(target);
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(ticketRepository.findByAssigneeIdAndStatus(TARGET_ID, TicketStatus.IN_PROGRESS)).thenReturn(List.of(inProgress));

        AdminUserResponse response = service.deactivate(TARGET_ID, ADMIN_ID);

        assertThat(target.getStatus()).isEqualTo(UserStatus.DEACTIVATED);
        assertThat(target.getDeactivatedAt()).isEqualTo(NOW.atOffset(ZoneOffset.UTC));
        verify(refreshTokenService).revokeAll(TARGET_ID);
        assertThat(inProgress.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(inProgress.getAssignee()).isNull();
        assertThat(response.status()).isEqualTo(UserStatus.DEACTIVATED);
        assertThat(response.ticketHandler()).isTrue();
    }

    @Test
    void adminCannotDeactivateHimself() {
        assertThatThrownBy(() -> service.deactivate(ADMIN_ID, ADMIN_ID)).isInstanceOf(SelfDeactivationException.class);
        verifyNoInteractions(userRepository, refreshTokenService);
    }

    @Test
    void lastActiveAdminCannotBeDeactivated() {
        User target = user(TARGET_ID, general, UserStatus.ACTIVE);
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(adminRepository.existsById(TARGET_ID)).thenReturn(true);
        when(userRepository.findAdminsByStatus(UserStatus.ACTIVE)).thenReturn(List.of(target));

        assertThatThrownBy(() -> service.deactivate(TARGET_ID, ADMIN_ID)).isInstanceOf(LastAdminException.class);
        assertThat(target.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(refreshTokenService, never()).revokeAll(any());
    }

    @Test
    void alreadyDeactivatedAccountIsConflict() {
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(user(TARGET_ID, general, UserStatus.DEACTIVATED)));

        assertThatThrownBy(() -> service.deactivate(TARGET_ID, ADMIN_ID)).isInstanceOf(InvalidAccountStatusException.class);
        verify(refreshTokenService, never()).revokeAll(any());
    }

    @Test
    void deactivatingUnknownUserIsNotFound() {
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivate(TARGET_ID, ADMIN_ID)).isInstanceOf(UserNotFoundException.class);
    }

    // ---------- reativar ----------

    @Test
    void reactivatingDeactivatedAccount() {
        User target = user(TARGET_ID, general, UserStatus.DEACTIVATED);
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));

        AdminUserResponse response = service.reactivate(TARGET_ID);

        assertThat(target.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(response.deactivatedAt()).isNull();
    }

    @Test
    void activeOrAnonymizedAccountsCannotBeReactivated() {
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(user(TARGET_ID, general, UserStatus.ACTIVE)));
        assertThatThrownBy(() -> service.reactivate(TARGET_ID)).isInstanceOf(InvalidAccountStatusException.class);

        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(user(TARGET_ID, general, UserStatus.ANONYMIZED)));
        assertThatThrownBy(() -> service.reactivate(TARGET_ID)).isInstanceOf(InvalidAccountStatusException.class);
    }

    // ---------- consultar ----------

    @Test
    void detailsShowAdminAndPendingRequest() {
        User target = user(TARGET_ID, general, UserStatus.ACTIVE);
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(adminRepository.existsById(TARGET_ID)).thenReturn(true);
        when(departmentRequestRepository.existsByUserIdAndStatus(TARGET_ID,
                DepartmentRequestStatus.PENDING)).thenReturn(true);

        AdminUserDetailResponse response = service.get(TARGET_ID);

        assertThat(response.cpf()).isEqualTo("52998224725");
        assertThat(response.admin()).isTrue();
        assertThat(response.ticketHandler()).isFalse();
        assertThat(response.departmentApprovalPending()).isTrue();
        assertThat(response.departmentName()).isEqualTo("Geral");
    }

    @Test
    void unknownUserIsNotFound() {
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(TARGET_ID)).isInstanceOf(UserNotFoundException.class);
    }

    // ---------- editar dados (SCRUM-176) ----------

    @Test
    void adminCorrectsAllFieldsAndEndsSessionsWhenTheEmailChanges() {
        User target = user(TARGET_ID, general, UserStatus.ACTIVE);
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(departmentRepository.findById(GENERAL_ID)).thenReturn(Optional.of(general));
        when(userRepository.existsByCpf("11144477735")).thenReturn(false);
        when(userRepository.existsByEmail("maria.souza@contabilizape.com.br")).thenReturn(false);

        AdminUserDetailResponse response = service.update(TARGET_ID,
                request("  Maria Souza  ", "11144477735", " Maria.Souza@ContabilizaPE.com.br ", GENERAL_ID), ADMIN_ID);

        assertThat(target.getFullName()).isEqualTo("Maria Souza");
        assertThat(target.getCpf()).isEqualTo("11144477735");
        assertThat(target.getEmail()).isEqualTo("maria.souza@contabilizape.com.br");
        assertThat(response.cpf()).isEqualTo("11144477735");
        assertThat(response.email()).isEqualTo("maria.souza@contabilizape.com.br");
        verify(refreshTokenService).endAllSessions(target);
    }

    @Test
    void keepingCpfAndEmailSkipsUniquenessChecksAndKeepsSessions() {
        User target = user(TARGET_ID, general, UserStatus.ACTIVE);
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(departmentRepository.findById(GENERAL_ID)).thenReturn(Optional.of(general));

        service.update(TARGET_ID, request("Maria S.", "52998224725", "maria.silva@contabilizape.com.br", GENERAL_ID), ADMIN_ID);

        assertThat(target.getFullName()).isEqualTo("Maria S.");
        verify(userRepository, never()).existsByCpf(any());
        verify(userRepository, never()).existsByEmail(any());
        verify(refreshTokenService, never()).endAllSessions(any());
    }

    @Test
    void adminChangingHisOwnEmailKeepsHisSession() {
        User self = user(ADMIN_ID, innovation, UserStatus.ACTIVE);
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(self));
        when(departmentRepository.findById(INNOVATION_ID)).thenReturn(Optional.of(innovation));
        when(userRepository.existsByEmail("ana.souza@contabilizape.com.br")).thenReturn(false);

        service.update(ADMIN_ID, request("Ana Souza", "52998224725", "ana.souza@contabilizape.com.br", INNOVATION_ID), ADMIN_ID);

        assertThat(self.getEmail()).isEqualTo("ana.souza@contabilizape.com.br");
        verify(refreshTokenService, never()).endAllSessions(any());
    }

    @Test
    void cpfOfAnotherAccountIsConflict() {
        User target = user(TARGET_ID, general, UserStatus.ACTIVE);
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(departmentRepository.findById(GENERAL_ID)).thenReturn(Optional.of(general));
        when(userRepository.existsByCpf("11144477735")).thenReturn(true);

        assertThatThrownBy(() -> service.update(TARGET_ID,
                request("Maria da Silva", "11144477735", "maria.silva@contabilizape.com.br", GENERAL_ID), ADMIN_ID))
                .isInstanceOf(CpfAlreadyRegisteredException.class);
        assertThat(target.getCpf()).isEqualTo("52998224725");
    }

    @Test
    void emailOfAnotherAccountIsConflict() {
        User target = user(TARGET_ID, general, UserStatus.ACTIVE);
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(departmentRepository.findById(GENERAL_ID)).thenReturn(Optional.of(general));
        when(userRepository.existsByEmail("joao.souza@contabilizape.com.br")).thenReturn(true);

        assertThatThrownBy(() -> service.update(TARGET_ID,
                request("Maria da Silva", "52998224725", "joao.souza@contabilizape.com.br", GENERAL_ID), ADMIN_ID))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
        assertThat(target.getEmail()).isEqualTo("maria.silva@contabilizape.com.br");
        verify(refreshTokenService, never()).endAllSessions(any());
    }

    @Test
    void administratorCannotLeaveInnovation() {
        User target = user(TARGET_ID, innovation, UserStatus.ACTIVE);
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(departmentRepository.findById(GENERAL_ID)).thenReturn(Optional.of(general));
        when(adminRepository.existsById(TARGET_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.update(TARGET_ID,
                request("Maria da Silva", "52998224725", "maria.silva@contabilizape.com.br", GENERAL_ID), ADMIN_ID))
                .isInstanceOf(InvalidAccountStatusException.class)
                .hasMessageContaining("Administradores precisam estar na Inovação");
        assertThat(target.getDepartment()).isSameAs(innovation);
        verifyNoInteractions(ticketRepository);
    }

    @Test
    void departmentIsLockedWhileARequestIsPending() {
        User target = user(TARGET_ID, general, UserStatus.ACTIVE);
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(departmentRepository.findById(INNOVATION_ID)).thenReturn(Optional.of(innovation));
        when(departmentRequestRepository.existsByUserIdAndStatus(TARGET_ID, DepartmentRequestStatus.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> service.update(TARGET_ID,
                request("Maria da Silva", "52998224725", "maria.silva@contabilizape.com.br", INNOVATION_ID), ADMIN_ID))
                .isInstanceOf(InvalidAccountStatusException.class);
        assertThat(target.getDepartment()).isSameAs(general);
    }

    @Test
    void leavingInnovationByEditReturnsTicketsToTheQueue() {
        User target = user(TARGET_ID, innovation, UserStatus.ACTIVE);
        Ticket inProgress = ticketAssignedTo(target);
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(departmentRepository.findById(GENERAL_ID)).thenReturn(Optional.of(general));
        when(ticketRepository.findByAssigneeIdAndStatus(TARGET_ID, TicketStatus.IN_PROGRESS)).thenReturn(List.of(inProgress));

        AdminUserDetailResponse response = service.update(TARGET_ID,
                request("Maria da Silva", "52998224725", "maria.silva@contabilizape.com.br", GENERAL_ID), ADMIN_ID);

        assertThat(target.getDepartment()).isSameAs(general);
        assertThat(response.ticketHandler()).isFalse();
        assertThat(inProgress.getStatus()).isEqualTo(TicketStatus.OPEN);
    }

    @Test
    void onlyActiveAccountsCanBeEdited() {
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(user(TARGET_ID, general, UserStatus.DEACTIVATED)));

        assertThatThrownBy(() -> service.update(TARGET_ID,
                request("Maria da Silva", "52998224725", "maria.silva@contabilizape.com.br", GENERAL_ID), ADMIN_ID))
                .isInstanceOf(InvalidAccountStatusException.class);
        verifyNoInteractions(departmentRepository, refreshTokenService);
    }

    // ---------- auxiliares ----------

    private static AdminUserUpdateRequest request(String fullName, String cpf, String email, UUID departmentId) {
        return new AdminUserUpdateRequest(fullName, cpf, email, departmentId);
    }

    private static User user(UUID id, Department department, UserStatus status) {
        User user = new User("Maria da Silva", "52998224725", "maria.silva@contabilizape.com.br", "hash", department);
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "status", status);
        return user;
    }

    private static Ticket ticketAssignedTo(User assignee) {
        Ticket ticket = new Ticket("Impressora", "Sem papel", mock(Category.class), mock(Priority.class), mock(User.class));
        ticket.assign(assignee, OffsetDateTime.now(ZoneOffset.UTC));
        return ticket;
    }

    private static Department department(UUID id, String name) {
        Department department = mock(Department.class, withSettings().strictness(Strictness.LENIENT));
        when(department.getId()).thenReturn(id);
        when(department.getName()).thenReturn(name);
        return department;
    }
}
