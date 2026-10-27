package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.exception.AccountBlockedException;
import br.com.contabilizape.centraldesk.exception.EmailNotVerifiedException;
import br.com.contabilizape.centraldesk.exception.LoginRateLimitException;
import br.com.contabilizape.centraldesk.model.SecurityEventType;
import br.com.contabilizape.centraldesk.security.ClientInfo;
import br.com.contabilizape.centraldesk.security.LoginAttemptLimiter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import br.com.contabilizape.centraldesk.dto.CurrentUserResponse;
import br.com.contabilizape.centraldesk.dto.LoginRequest;
import br.com.contabilizape.centraldesk.dto.PasswordChangeRequest;
import br.com.contabilizape.centraldesk.exception.AccountNotActiveException;
import br.com.contabilizape.centraldesk.exception.CurrentPasswordMismatchException;
import br.com.contabilizape.centraldesk.exception.InvalidCredentialsException;
import br.com.contabilizape.centraldesk.exception.InvalidSessionException;
import br.com.contabilizape.centraldesk.exception.NewPasswordSameAsCurrentException;
import br.com.contabilizape.centraldesk.model.Department;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.model.UserStatus;
import br.com.contabilizape.centraldesk.repository.AdminRepository;
import br.com.contabilizape.centraldesk.repository.UserRepository;
import br.com.contabilizape.centraldesk.security.Roles;
import br.com.contabilizape.centraldesk.security.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

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
import static org.mockito.Mockito.withSettings;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final UUID INNOVATION_ID = UUID.fromString("e9790f1f-41df-4369-bfe7-1be4be535561");
    private static final UUID GENERAL_ID = UUID.fromString("3094ed3f-42ab-4bc0-9d8d-f6eb08891016");
    private static final UUID USER_ID = UUID.fromString("0f8b2c1e-6d3a-4f5e-9b7c-1a2b3c4d5e6f");
    private static final String EMAIL = "maria.silva@contabilizape.com.br";
    private static final String PASSWORD = "senhaForte123";
    private static final String DUMMY_HASH = "hash-ficticio";
    private static final Instant NOW = Instant.parse("2026-10-27T12:00:00Z");
    private static final ClientInfo CLIENT = new ClientInfo("203.0.113.7", "Mozilla/5.0 (Windows NT 10.0) Chrome/130");

    @Mock private UserRepository userRepository;
    @Mock private AdminRepository adminRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TokenService tokenService;
    @Mock private UserService userService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private LoginAttemptLimiter loginAttemptLimiter;
    @Mock private LoginLockout loginLockout;
    @Mock private SecurityEventService securityEvents;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        // O AuthService gera um hash fictício ao ser criado (usado quando o e-mail não existe)
        when(passwordEncoder.encode(anyString())).thenReturn(DUMMY_HASH);
        authService = new AuthService(userRepository, adminRepository, passwordEncoder,
                tokenService, userService, refreshTokenService, loginAttemptLimiter, loginLockout, securityEvents,
                Clock.fixed(NOW, ZoneOffset.UTC), INNOVATION_ID);
    }

    @Test
    void validCredentialsReturnTokenAndCurrentUser() {
        User maria = user(GENERAL_ID, UserStatus.ACTIVE);
        CurrentUserResponse current = currentUser(List.of());
        when(userRepository.findByEmailForUpdate(EMAIL)).thenReturn(Optional.of(maria));
        when(passwordEncoder.matches(PASSWORD, "hash-da-maria")).thenReturn(true);
        when(tokenService.issueAccessToken(USER_ID, EMAIL, List.of())).thenReturn("jwt-123");
        when(userService.getCurrentUser(USER_ID, List.of())).thenReturn(current);
        when(refreshTokenService.issue(maria)).thenReturn("refresh-123");

        AuthenticatedSession session = authService.login(new LoginRequest(EMAIL, PASSWORD), CLIENT);

        assertThat(session.accessToken()).isEqualTo("jwt-123");
        assertThat(session.refreshToken()).isEqualTo("refresh-123");
        assertThat(session.user()).isSameAs(current);
    }

    @Test
    void normalizesEmailBeforeSearching() {
        when(userRepository.findByEmailForUpdate(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("  Maria.Silva@ContabilizaPE.com.br ", PASSWORD), CLIENT))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(userRepository).findByEmailForUpdate(EMAIL);
    }

    @Test
    void unknownEmailIsRejectedAfterComparingWithDummyHash() {
        when(userRepository.findByEmailForUpdate(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, PASSWORD), CLIENT))
                .isInstanceOf(InvalidCredentialsException.class);
        // Compara mesmo assim: a resposta leva o mesmo tempo de uma senha errada
        verify(passwordEncoder).matches(PASSWORD, DUMMY_HASH);
        verify(tokenService, never()).issueAccessToken(any(), any(), any());
    }

    @Test
    void wrongPasswordIsRejectedWithTheSameException() {
        // Criado fora do thenReturn: user() configura um mock (ver execução 4 no registro de testes)
        User maria = user(GENERAL_ID, UserStatus.ACTIVE);
        when(userRepository.findByEmailForUpdate(EMAIL)).thenReturn(Optional.of(maria));
        when(passwordEncoder.matches(PASSWORD, "hash-da-maria")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, PASSWORD), CLIENT))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage(new InvalidCredentialsException().getMessage());
        verify(tokenService, never()).issueAccessToken(any(), any(), any());
    }

    @Test
    void inactiveAccountIsRejectedEvenWithCorrectPassword() {
        // Criado fora do thenReturn: user() configura um mock (ver execução 4 no registro de testes)
        User maria = user(GENERAL_ID, UserStatus.DEACTIVATED);
        when(userRepository.findByEmailForUpdate(EMAIL)).thenReturn(Optional.of(maria));
        when(passwordEncoder.matches(PASSWORD, "hash-da-maria")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, PASSWORD), CLIENT))
                .isInstanceOf(AccountNotActiveException.class);
        verify(tokenService, never()).issueAccessToken(any(), any(), any());
    }

    // ---------- SCRUM-202: bloqueio por tentativas e limite por IP ----------

    @Test
    void ipOverTheLimitIsRefusedBeforeLookingAtTheAccount() {
        doThrow(new LoginRateLimitException(Duration.ofMinutes(3))).when(loginAttemptLimiter).ensureAllowed(CLIENT.ip());

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, PASSWORD), CLIENT))
                .isInstanceOf(LoginRateLimitException.class);
        verifyNoInteractions(userRepository);
    }

    @Test
    void wrongPasswordOnAnActiveAccountIsCountedForTheLockoutAndForTheIp() {
        User maria = user(GENERAL_ID, UserStatus.ACTIVE);
        when(userRepository.findByEmailForUpdate(EMAIL)).thenReturn(Optional.of(maria));
        when(passwordEncoder.matches(PASSWORD, "hash-da-maria")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, PASSWORD), CLIENT))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(loginLockout).registerFailure(maria, CLIENT, SecurityEventType.LOGIN_FAILED);
        verify(loginAttemptLimiter).recordFailure(CLIENT.ip());
    }

    @Test
    void unknownEmailIsCountedForTheIpAndAudited() {
        when(userRepository.findByEmailForUpdate(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, PASSWORD), CLIENT))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(loginAttemptLimiter).recordFailure(CLIENT.ip());
        verify(securityEvents).record(eq(SecurityEventType.LOGIN_UNKNOWN_EMAIL), isNull(), eq(CLIENT), anyString());
    }

    @Test
    void wrongPasswordOnABlockedAccountIsNotCountedAgain() {
        User maria = user(GENERAL_ID, UserStatus.BLOCKED);
        when(userRepository.findByEmailForUpdate(EMAIL)).thenReturn(Optional.of(maria));
        when(passwordEncoder.matches(PASSWORD, "hash-da-maria")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, PASSWORD), CLIENT))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(loginLockout, never()).registerFailure(any(), any(), any());
    }

    @Test
    void rightPasswordOnABlockedAccountSaysItIsBlocked() {
        User maria = user(GENERAL_ID, UserStatus.BLOCKED);
        when(userRepository.findByEmailForUpdate(EMAIL)).thenReturn(Optional.of(maria));
        when(passwordEncoder.matches(PASSWORD, "hash-da-maria")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, PASSWORD), CLIENT))
                .isInstanceOf(AccountBlockedException.class);
        verify(tokenService, never()).issueAccessToken(any(), any(), any());
    }

    @Test
    void rightPasswordOnAnUnconfirmedAccountAsksForTheEmailConfirmation() {
        User maria = user(GENERAL_ID, UserStatus.PENDING_VERIFICATION);
        when(userRepository.findByEmailForUpdate(EMAIL)).thenReturn(Optional.of(maria));
        when(passwordEncoder.matches(PASSWORD, "hash-da-maria")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, PASSWORD), CLIENT))
                .isInstanceOf(EmailNotVerifiedException.class);
        verify(tokenService, never()).issueAccessToken(any(), any(), any());
    }

    @Test
    void expiredTemporaryPasswordLooksLikeAWrongPassword() {
        User maria = user(GENERAL_ID, UserStatus.ACTIVE);
        maria.resetPasswordByAdmin("hash-da-maria", NOW.atOffset(ZoneOffset.UTC).minusMinutes(1));
        when(userRepository.findByEmailForUpdate(EMAIL)).thenReturn(Optional.of(maria));
        when(passwordEncoder.matches(PASSWORD, "hash-da-maria")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, PASSWORD), CLIENT))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(tokenService, never()).issueAccessToken(any(), any(), any());
    }

    @Test
    void successfulLoginClearsThePreviousMistakes() {
        User maria = user(GENERAL_ID, UserStatus.ACTIVE);
        maria.registerFailedLogin(NOW.atOffset(ZoneOffset.UTC), 5);
        when(userRepository.findByEmailForUpdate(EMAIL)).thenReturn(Optional.of(maria));
        when(passwordEncoder.matches(PASSWORD, "hash-da-maria")).thenReturn(true);
        when(userService.getCurrentUser(USER_ID, List.of())).thenReturn(currentUser(List.of()));

        authService.login(new LoginRequest(EMAIL, PASSWORD), CLIENT);

        assertThat(maria.getFailedLoginAttempts()).isZero();
    }

    @Test
    void adminReceivesRoleAdmin() {
        loginReturnsRolesFor(user(GENERAL_ID, UserStatus.ACTIVE), true, List.of(Roles.ADMIN));
    }

    @Test
    void innovationMemberReceivesTicketHandler() {
        loginReturnsRolesFor(user(INNOVATION_ID, UserStatus.ACTIVE), false, List.of(Roles.TICKET_HANDLER));
    }

    @Test
    void adminFromInnovationReceivesBothRoles() {
        loginReturnsRolesFor(user(INNOVATION_ID, UserStatus.ACTIVE), true, List.of(Roles.ADMIN, Roles.TICKET_HANDLER));
    }

    // ---------- renovação e logout ----------

    @Test
    void refreshRotatesTheTokenAndRecalculatesRoles() {
        // Virou admin depois do login: a renovação já traz a permissão nova
        User maria = user(INNOVATION_ID, UserStatus.ACTIVE);
        List<String> roles = List.of(Roles.ADMIN, Roles.TICKET_HANDLER);
        CurrentUserResponse current = currentUser(roles);
        when(refreshTokenService.rotate("refresh-velho")).thenReturn(new RotatedRefreshToken(maria, "refresh-novo"));
        when(adminRepository.existsById(USER_ID)).thenReturn(true);
        when(tokenService.issueAccessToken(USER_ID, EMAIL, roles)).thenReturn("jwt-novo");
        when(userService.getCurrentUser(USER_ID, roles)).thenReturn(current);

        AuthenticatedSession session = authService.refresh("refresh-velho");

        assertThat(session.accessToken()).isEqualTo("jwt-novo");
        assertThat(session.refreshToken()).isEqualTo("refresh-novo");
        assertThat(session.user()).isSameAs(current);
    }

    @Test
    void logoutRevokesTheRefreshToken() {
        authService.logout("refresh-123");

        verify(refreshTokenService).revoke("refresh-123");
    }

    @Test
    void logoutWithoutRefreshTokenDoesNothing() {
        authService.logout(null);
        authService.logout("");

        verifyNoInteractions(refreshTokenService);
    }


    // ---------- troca de senha (PATCH /users/me/password) ----------

    @Test
    void changePasswordStoresNewHashRevokesEverySessionAndOpensANewOne() {
        User maria = user(GENERAL_ID, UserStatus.ACTIVE);
        CurrentUserResponse current = currentUser(List.of());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(maria));
        when(passwordEncoder.matches(PASSWORD, "hash-da-maria")).thenReturn(true);
        when(passwordEncoder.matches("novaSenha456", "hash-da-maria")).thenReturn(false);
        when(passwordEncoder.encode("novaSenha456")).thenReturn("hash-novo");
        when(refreshTokenService.issue(maria)).thenReturn("refresh-novo");
        when(tokenService.issueAccessToken(USER_ID, EMAIL, List.of())).thenReturn("jwt-novo");
        when(userService.getCurrentUser(USER_ID, List.of())).thenReturn(current);

        AuthenticatedSession session = authService.changePassword(USER_ID, new PasswordChangeRequest(PASSWORD, "novaSenha456"));

        assertThat(maria.getPasswordHash()).isEqualTo("hash-novo");
        verify(refreshTokenService).endAllSessions(maria);
        assertThat(session.refreshToken()).isEqualTo("refresh-novo");
        assertThat(session.accessToken()).isEqualTo("jwt-novo");
    }

    @Test
    void changePasswordWithWrongCurrentPasswordIsRejected() {
        User maria = user(GENERAL_ID, UserStatus.ACTIVE);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(maria));
        when(passwordEncoder.matches("errada123", "hash-da-maria")).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(USER_ID, new PasswordChangeRequest("errada123", "novaSenha456")))
                .isInstanceOf(CurrentPasswordMismatchException.class);
        assertThat(maria.getPasswordHash()).isEqualTo("hash-da-maria");
        verify(refreshTokenService, never()).endAllSessions(any());
    }

    @Test
    void changePasswordToTheSamePasswordIsRejected() {
        User maria = user(GENERAL_ID, UserStatus.ACTIVE);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(maria));
        when(passwordEncoder.matches(PASSWORD, "hash-da-maria")).thenReturn(true);

        assertThatThrownBy(() -> authService.changePassword(USER_ID, new PasswordChangeRequest(PASSWORD, PASSWORD)))
                .isInstanceOf(NewPasswordSameAsCurrentException.class);
        verify(refreshTokenService, never()).endAllSessions(any());
    }

    @Test
    void changePasswordOfMissingUserIsInvalidSession() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.changePassword(USER_ID, new PasswordChangeRequest(PASSWORD, "novaSenha456")))
                .isInstanceOf(InvalidSessionException.class);
    }

    @Test
    void changePasswordOfDeactivatedUserIsRejected() {
        User deactivated = user(GENERAL_ID, UserStatus.DEACTIVATED);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(deactivated));

        assertThatThrownBy(() -> authService.changePassword(USER_ID, new PasswordChangeRequest(PASSWORD, "novaSenha456")))
                .isInstanceOf(AccountNotActiveException.class);
        verify(refreshTokenService, never()).endAllSessions(any());
    }
    // ---------- auxiliares ----------

    private void loginReturnsRolesFor(User user, boolean isAdmin, List<String> expectedRoles) {
        CurrentUserResponse current = currentUser(expectedRoles);
        when(userRepository.findByEmailForUpdate(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, "hash-da-maria")).thenReturn(true);
        when(adminRepository.existsById(USER_ID)).thenReturn(isAdmin);
        when(tokenService.issueAccessToken(USER_ID, EMAIL, expectedRoles)).thenReturn("jwt-123");
        when(userService.getCurrentUser(USER_ID, expectedRoles)).thenReturn(current);

        AuthenticatedSession session = authService.login(new LoginRequest(EMAIL, PASSWORD), CLIENT);

        assertThat(session.user().roles()).isEqualTo(expectedRoles);
    }

    private static User user(UUID departmentId, UserStatus status) {
        Department department = mock(Department.class, withSettings().strictness(Strictness.LENIENT));
        when(department.getId()).thenReturn(departmentId);
        User user = new User("Maria da Silva", "52998224725", EMAIL, "hash-da-maria", department);
        ReflectionTestUtils.setField(user, "id", USER_ID);
        ReflectionTestUtils.setField(user, "status", status);
        return user;
    }

    private static CurrentUserResponse currentUser(List<String> roles) {
        return new CurrentUserResponse(USER_ID, "Maria da Silva", EMAIL, UserStatus.ACTIVE,
                GENERAL_ID, "Geral", false, null, roles);
    }
}
