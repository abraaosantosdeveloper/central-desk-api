package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.dto.AdminUserResponse;
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
    @Mock private RefreshTokenService refreshTokenService;

    private UserAdministrationService service;

    private final Department general = department(GENERAL_ID, "Geral");
    private final Department innovation = department(INNOVATION_ID, "Inovação");

    @BeforeEach
    void setUp() {
        AccountOffboarding offboarding = new AccountOffboarding(userRepository, adminRepository, ticketRepository,
                refreshTokenService);
        service = new UserAdministrationService(userRepository, adminRepository, departmentRequestRepository,
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

        AdminUserResponse response = service.get(TARGET_ID);

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

    // ---------- auxiliares ----------

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
