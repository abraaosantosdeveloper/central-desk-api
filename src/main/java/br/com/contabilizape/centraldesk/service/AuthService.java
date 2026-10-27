package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.dto.LoginRequest;
import br.com.contabilizape.centraldesk.dto.PasswordChangeRequest;
import br.com.contabilizape.centraldesk.exception.AccountBlockedException;
import br.com.contabilizape.centraldesk.exception.AccountNotActiveException;
import br.com.contabilizape.centraldesk.exception.EmailNotVerifiedException;
import br.com.contabilizape.centraldesk.exception.CurrentPasswordMismatchException;
import br.com.contabilizape.centraldesk.exception.InvalidCredentialsException;
import br.com.contabilizape.centraldesk.exception.InvalidSessionException;
import br.com.contabilizape.centraldesk.exception.NewPasswordSameAsCurrentException;
import br.com.contabilizape.centraldesk.model.SecurityEventType;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.repository.AdminRepository;
import br.com.contabilizape.centraldesk.repository.UserRepository;
import br.com.contabilizape.centraldesk.security.ClientInfo;
import br.com.contabilizape.centraldesk.security.LoginAttemptLimiter;
import br.com.contabilizape.centraldesk.security.Roles;
import br.com.contabilizape.centraldesk.security.TokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final UserService userService;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final LoginLockout loginLockout;
    private final SecurityEventService securityEvents;
    private final Clock clock;
    private final UUID handlerDepartmentId;

    // Comparado quando o e-mail não existe, para a resposta levar o mesmo tempo de uma senha errada
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository,
                       AdminRepository adminRepository,
                       PasswordEncoder passwordEncoder,
                       TokenService tokenService,
                       UserService userService,
                       RefreshTokenService refreshTokenService,
                       LoginAttemptLimiter loginAttemptLimiter,
                       LoginLockout loginLockout,
                       SecurityEventService securityEvents,
                       Clock clock,
                       @Value("${app.tickets.handler-department-id}") UUID handlerDepartmentId) {
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.userService = userService;
        this.refreshTokenService = refreshTokenService;
        this.loginAttemptLimiter = loginAttemptLimiter;
        this.loginLockout = loginLockout;
        this.securityEvents = securityEvents;
        this.clock = clock;
        this.handlerDepartmentId = handlerDepartmentId;
        this.dummyPasswordHash = passwordEncoder.encode("senha-ficticia-" + UUID.randomUUID());
    }

    /**
     * Confere e-mail e senha (PLANO-SEGURANCA §5). As recusas não desfazem a transação: a falha contada,
     * o bloqueio e a auditoria precisam ficar gravados.
     *
     * Senha errada → 401 genérico, em qualquer estado da conta (conta ativa soma uma falha; na 5ª, bloqueia).
     * Senha certa → aí sim o estado decide: bloqueada (403), e-mail não confirmado (403), inativa (403).
     */
    @Transactional(noRollbackFor = {InvalidCredentialsException.class, AccountBlockedException.class,
            EmailNotVerifiedException.class, AccountNotActiveException.class})
    public AuthenticatedSession login(LoginRequest request, ClientInfo client) {
        loginAttemptLimiter.ensureAllowed(client.ip());
        User user = authenticate(Emails.normalize(request.email()), request.password(), client);
        ensureCanEnter(user, client);
        user.resetFailedLogins();
        return openSession(user, refreshTokenService.issue(user));
    }

    /**
     * Troca o refresh token por um novo e emite outro access token. As permissões são recalculadas,
     * então uma mudança de papel vale a partir da renovação. Recusas não desfazem a transação
     * (ver RefreshTokenService.rotate).
     */
    @Transactional(noRollbackFor = {InvalidSessionException.class, AccountNotActiveException.class})
    public AuthenticatedSession refresh(String rawRefreshToken) {
        RotatedRefreshToken rotated = refreshTokenService.rotate(rawRefreshToken);
        return openSession(rotated.user(), rotated.rawToken());
    }

    /** Encerra a sessão no servidor. Sem refresh token (cookie ausente), não há o que revogar. */
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokenService.revoke(rawRefreshToken);
        }
    }

    /**
     * Troca a senha do usuário logado e encerra todas as sessões dele. Como o cookie do refresh token
     * só vai para /auth, a sessão atual não é identificável aqui: ela é reaberta com tokens novos,
     * devolvidos para o controller gravar nos cookies.
     */
    @Transactional
    public AuthenticatedSession changePassword(UUID userId, PasswordChangeRequest request) {
        User user = userRepository.findById(userId).orElseThrow(InvalidSessionException::new);
        if (!user.isActive()) {
            throw new AccountNotActiveException();
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new CurrentPasswordMismatchException();
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new NewPasswordSameAsCurrentException();
        }

        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        // As outras sessões caem na hora; a atual é reaberta logo abaixo com tokens novos
        refreshTokenService.endAllSessions(user);
        return openSession(user, refreshTokenService.issue(user));
    }

    private AuthenticatedSession openSession(User user, String rawRefreshToken) {
        List<String> roles = rolesOf(user);
        String accessToken = tokenService.issueAccessToken(user.getId(), user.getEmail(), roles);
        return new AuthenticatedSession(userService.getCurrentUser(user.getId(), roles), accessToken, rawRefreshToken);
    }

    private User authenticate(String email, String password, ClientInfo client) {
        User user = userRepository.findByEmailForUpdate(email).orElse(null);
        if (user == null) {
            // Compara mesmo assim: a resposta leva o mesmo tempo de uma senha errada
            passwordEncoder.matches(password, dummyPasswordHash);
            loginAttemptLimiter.recordFailure(client.ip());
            securityEvents.record(SecurityEventType.LOGIN_UNKNOWN_EMAIL, null, client, "e-mail: " + email);
            throw new InvalidCredentialsException();
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            loginAttemptLimiter.recordFailure(client.ip());
            if (user.isActive()) {
                loginLockout.registerFailure(user, client, SecurityEventType.LOGIN_FAILED);
            } else {
                // Conta bloqueada ou inativa não acumula falhas; fica só na auditoria
                securityEvents.record(SecurityEventType.LOGIN_FAILED, user, client, "conta " + user.getStatus());
            }
            throw new InvalidCredentialsException();
        }
        return user;
    }

    // A senha confere antes de olhar o status: só quem sabe a senha descobre o estado da conta (B2)
    private void ensureCanEnter(User user, ClientInfo client) {
        switch (user.getStatus()) {
            case ACTIVE -> {
                // Senha temporária vencida: como uma senha errada; é preciso pedir outra ao administrador
                if (user.isTemporaryPasswordExpiredAt(OffsetDateTime.now(clock))) {
                    throw new InvalidCredentialsException();
                }
            }
            case BLOCKED -> {
                securityEvents.record(SecurityEventType.LOGIN_BLOCKED_ACCOUNT, user, client, null);
                throw new AccountBlockedException();
            }
            case PENDING_VERIFICATION -> throw new EmailNotVerifiedException();
            default -> throw new AccountNotActiveException();
        }
    }

    private List<String> rolesOf(User user) {
        List<String> roles = new ArrayList<>();
        if (adminRepository.existsById(user.getId())) {
            roles.add(Roles.ADMIN);
        }
        if (user.getDepartment().getId().equals(handlerDepartmentId)) {
            roles.add(Roles.TICKET_HANDLER);
        }
        return List.copyOf(roles);
    }
}
