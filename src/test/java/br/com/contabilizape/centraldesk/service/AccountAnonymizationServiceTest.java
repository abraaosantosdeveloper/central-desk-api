package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.exception.AccountNotActiveException;
import br.com.contabilizape.centraldesk.exception.AccountNotDeactivatedException;
import br.com.contabilizape.centraldesk.exception.InvalidSessionException;
import br.com.contabilizape.centraldesk.exception.LastAdminException;
import br.com.contabilizape.centraldesk.exception.PasswordConfirmationMismatchException;
import br.com.contabilizape.centraldesk.exception.UserNotFoundException;
import br.com.contabilizape.centraldesk.model.Category;
import br.com.contabilizape.centraldesk.model.Department;
import br.com.contabilizape.centraldesk.model.Priority;
import br.com.contabilizape.centraldesk.model.Ticket;
import br.com.contabilizape.centraldesk.model.TicketStatus;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.model.UserStatus;
import br.com.contabilizape.centraldesk.repository.AdminRepository;
import br.com.contabilizape.centraldesk.repository.NotificationCleanupRepository;
import br.com.contabilizape.centraldesk.repository.TicketRepository;
import br.com.contabilizape.centraldesk.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountAnonymizationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");
    private static final OffsetDateTime NOW_UTC = NOW.atOffset(ZoneOffset.UTC);
    private static final UUID USER_ID = UUID.fromString("0f8b2c1e-6d3a-4f5e-9b7c-1a2b3c4d5e6f");
    private static final String PASSWORD = "senhaForte123";

    @Mock private UserRepository userRepository;
    @Mock private AdminRepository adminRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private NotificationCleanupRepository notificationCleanup;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private PasswordEncoder passwordEncoder;

    private AccountAnonymizationService service;

    @BeforeEach
    void setUp() {
        // A parte compartilhada com a desativação é real; as dependências dela são os mocks acima
        AccountOffboarding offboarding = new AccountOffboarding(userRepository, adminRepository, ticketRepository,
                refreshTokenService);
        service = new AccountAnonymizationService(userRepository, adminRepository, notificationCleanup, offboarding,
                passwordEncoder, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // ---------- pelo próprio usuário (DELETE /users/me) ----------

    @Test
    void ownAccountIsAnonymizedWithEverythingInTheSameTransaction() {
        User maria = user(UserStatus.ACTIVE);
        Ticket assigned = ticketInProgressAssignedTo(maria);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(maria));
        when(passwordEncoder.matches(PASSWORD, "hash-da-maria")).thenReturn(true);
        when(passwordEncoder.encode(anyString())).thenReturn("hash-aleatorio");
        when(ticketRepository.findByAssigneeIdAndStatus(USER_ID, TicketStatus.IN_PROGRESS)).thenReturn(List.of(assigned));

        service.anonymizeOwnAccount(USER_ID, PASSWORD);

        assertThat(maria.getFullName()).isEqualTo("Usuário removido");
        assertThat(maria.getCpf()).isNull();
        assertThat(maria.getEmail()).isEqualTo("anonimizado-" + USER_ID + "@anonimizado.invalid");
        assertThat(maria.getPasswordHash()).isEqualTo("hash-aleatorio");
        assertThat(maria.getStatus()).isEqualTo(UserStatus.ANONYMIZED);
        assertThat(maria.getDeletedAt()).isEqualTo(NOW_UTC);

        verify(refreshTokenService).revokeAll(USER_ID);
        verify(notificationCleanup).deleteByRecipientId(USER_ID);
        assertThat(assigned.getAssignee()).isNull();
        assertThat(assigned.getAssignedAt()).isNull();
        assertThat(assigned.getStatus()).isEqualTo(TicketStatus.OPEN);
    }

    @Test
    void ownAccountWithWrongPasswordIsRejected() {
        User maria = user(UserStatus.ACTIVE);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(maria));
        when(passwordEncoder.matches("errada", "hash-da-maria")).thenReturn(false);

        assertThatThrownBy(() -> service.anonymizeOwnAccount(USER_ID, "errada"))
                .isInstanceOf(PasswordConfirmationMismatchException.class);
        assertThat(maria.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verifyNoInteractions(refreshTokenService, notificationCleanup, ticketRepository);
    }

    @Test
    void ownAccountOfMissingUserIsInvalidSession() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.anonymizeOwnAccount(USER_ID, PASSWORD))
                .isInstanceOf(InvalidSessionException.class);
    }

    @Test
    void ownAccountMustBeActive() {
        User maria = user(UserStatus.DEACTIVATED);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(maria));

        assertThatThrownBy(() -> service.anonymizeOwnAccount(USER_ID, PASSWORD))
                .isInstanceOf(AccountNotActiveException.class);
    }

    @Test
    void lastAdminCannotCloseOwnAccount() {
        User maria = user(UserStatus.ACTIVE);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(maria));
        when(passwordEncoder.matches(PASSWORD, "hash-da-maria")).thenReturn(true);
        when(adminRepository.existsById(USER_ID)).thenReturn(true);
        when(userRepository.findAdminsByStatus(UserStatus.ACTIVE)).thenReturn(List.of(maria));

        assertThatThrownBy(() -> service.anonymizeOwnAccount(USER_ID, PASSWORD))
                .isInstanceOf(LastAdminException.class);
        assertThat(maria.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(adminRepository, never()).deleteById(any());
    }

    @Test
    void adminWithAnotherActiveAdminLosesTheAdminRecord() {
        User maria = user(UserStatus.ACTIVE);
        User other = mock(User.class);
        when(other.getId()).thenReturn(UUID.randomUUID());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(maria));
        when(passwordEncoder.matches(PASSWORD, "hash-da-maria")).thenReturn(true);
        when(adminRepository.existsById(USER_ID)).thenReturn(true);
        when(userRepository.findAdminsByStatus(UserStatus.ACTIVE)).thenReturn(List.of(maria, other));

        service.anonymizeOwnAccount(USER_ID, PASSWORD);

        verify(adminRepository).deleteById(USER_ID);
        assertThat(maria.getStatus()).isEqualTo(UserStatus.ANONYMIZED);
    }

    // ---------- pelo administrador (PATCH /users/{id}/anonymize) ----------

    @Test
    void adminAnonymizesDeactivatedAccount() {
        User maria = user(UserStatus.DEACTIVATED);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(maria));
        when(passwordEncoder.encode(anyString())).thenReturn("hash-aleatorio");

        service.anonymizeByAdmin(USER_ID);

        assertThat(maria.getStatus()).isEqualTo(UserStatus.ANONYMIZED);
        verify(refreshTokenService).revokeAll(USER_ID);
        verify(notificationCleanup).deleteByRecipientId(USER_ID);
    }

    @Test
    void adminCannotAnonymizeActiveAccount() {
        User maria = user(UserStatus.ACTIVE);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(maria));

        assertThatThrownBy(() -> service.anonymizeByAdmin(USER_ID))
                .isInstanceOf(AccountNotDeactivatedException.class);
        verifyNoInteractions(refreshTokenService, notificationCleanup);
    }

    @Test
    void adminCannotAnonymizeAlreadyAnonymizedAccount() {
        User maria = user(UserStatus.ANONYMIZED);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(maria));

        assertThatThrownBy(() -> service.anonymizeByAdmin(USER_ID))
                .isInstanceOf(AccountNotDeactivatedException.class);
    }

    @Test
    void adminAnonymizingUnknownUserIsNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.anonymizeByAdmin(USER_ID))
                .isInstanceOf(UserNotFoundException.class);
    }

    // ---------- auxiliares ----------

    private static User user(UserStatus status) {
        User user = new User("Maria da Silva", "52998224725", "maria.silva@contabilizape.com.br",
                "hash-da-maria", mock(Department.class));
        ReflectionTestUtils.setField(user, "id", USER_ID);
        ReflectionTestUtils.setField(user, "status", status);
        return user;
    }

    private static Ticket ticketInProgressAssignedTo(User assignee) {
        Ticket ticket = new Ticket("Impressora", "Sem papel", mock(Category.class), mock(Priority.class), mock(User.class));
        ReflectionTestUtils.setField(ticket, "status", TicketStatus.IN_PROGRESS);
        ReflectionTestUtils.setField(ticket, "assignee", assignee);
        ReflectionTestUtils.setField(ticket, "assignedAt", NOW_UTC.minusDays(1));
        return ticket;
    }
}
