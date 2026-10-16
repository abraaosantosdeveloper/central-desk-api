package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.dto.CurrentUserResponse;
import br.com.contabilizape.centraldesk.dto.LoginRequest;
import br.com.contabilizape.centraldesk.exception.AccountNotActiveException;
import br.com.contabilizape.centraldesk.exception.InvalidCredentialsException;
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

    @Mock private UserRepository userRepository;
    @Mock private AdminRepository adminRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TokenService tokenService;
    @Mock private UserService userService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        // O AuthService gera um hash fictício ao ser criado (usado quando o e-mail não existe)
        when(passwordEncoder.encode(anyString())).thenReturn(DUMMY_HASH);
        authService = new AuthService(userRepository, adminRepository, passwordEncoder,
                tokenService, userService, INNOVATION_ID);
    }

    @Test
    void validCredentialsReturnTokenAndCurrentUser() {
        User maria = user(GENERAL_ID, UserStatus.ACTIVE);
        CurrentUserResponse current = currentUser(List.of());
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(maria));
        when(passwordEncoder.matches(PASSWORD, "hash-da-maria")).thenReturn(true);
        when(tokenService.issueAccessToken(USER_ID, EMAIL, List.of())).thenReturn("jwt-123");
        when(userService.getCurrentUser(USER_ID, List.of())).thenReturn(current);

        AuthenticatedSession session = authService.login(new LoginRequest(EMAIL, PASSWORD));

        assertThat(session.accessToken()).isEqualTo("jwt-123");
        assertThat(session.user()).isSameAs(current);
    }

    @Test
    void normalizesEmailBeforeSearching() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("  Maria.Silva@ContabilizaPE.com.br ", PASSWORD)))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(userRepository).findByEmail(EMAIL);
    }

    @Test
    void unknownEmailIsRejectedAfterComparingWithDummyHash() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, PASSWORD)))
                .isInstanceOf(InvalidCredentialsException.class);
        // Compara mesmo assim: a resposta leva o mesmo tempo de uma senha errada
        verify(passwordEncoder).matches(PASSWORD, DUMMY_HASH);
        verify(tokenService, never()).issueAccessToken(any(), any(), any());
    }

    @Test
    void wrongPasswordIsRejectedWithTheSameException() {
        // Criado fora do thenReturn: user() configura um mock (ver execução 4 no registro de testes)
        User maria = user(GENERAL_ID, UserStatus.ACTIVE);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(maria));
        when(passwordEncoder.matches(PASSWORD, "hash-da-maria")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, PASSWORD)))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage(new InvalidCredentialsException().getMessage());
        verify(tokenService, never()).issueAccessToken(any(), any(), any());
    }

    @Test
    void inactiveAccountIsRejectedEvenWithCorrectPassword() {
        // Criado fora do thenReturn: user() configura um mock (ver execução 4 no registro de testes)
        User maria = user(GENERAL_ID, UserStatus.DEACTIVATED);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(maria));
        when(passwordEncoder.matches(PASSWORD, "hash-da-maria")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, PASSWORD)))
                .isInstanceOf(AccountNotActiveException.class);
        verify(tokenService, never()).issueAccessToken(any(), any(), any());
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

    // ---------- auxiliares ----------

    private void loginReturnsRolesFor(User user, boolean isAdmin, List<String> expectedRoles) {
        CurrentUserResponse current = currentUser(expectedRoles);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, "hash-da-maria")).thenReturn(true);
        when(adminRepository.existsById(USER_ID)).thenReturn(isAdmin);
        when(tokenService.issueAccessToken(USER_ID, EMAIL, expectedRoles)).thenReturn("jwt-123");
        when(userService.getCurrentUser(USER_ID, expectedRoles)).thenReturn(current);

        AuthenticatedSession session = authService.login(new LoginRequest(EMAIL, PASSWORD));

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
