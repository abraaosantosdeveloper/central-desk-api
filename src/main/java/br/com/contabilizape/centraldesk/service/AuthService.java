package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.dto.LoginRequest;
import br.com.contabilizape.centraldesk.exception.AccountNotActiveException;
import br.com.contabilizape.centraldesk.exception.InvalidCredentialsException;
import br.com.contabilizape.centraldesk.exception.InvalidSessionException;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.model.UserStatus;
import br.com.contabilizape.centraldesk.repository.AdminRepository;
import br.com.contabilizape.centraldesk.repository.UserRepository;
import br.com.contabilizape.centraldesk.security.Roles;
import br.com.contabilizape.centraldesk.security.TokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final UUID handlerDepartmentId;

    // Comparado quando o e-mail não existe, para a resposta levar o mesmo tempo de uma senha errada
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository,
                       AdminRepository adminRepository,
                       PasswordEncoder passwordEncoder,
                       TokenService tokenService,
                       UserService userService,
                       RefreshTokenService refreshTokenService,
                       @Value("${app.tickets.handler-department-id}") UUID handlerDepartmentId) {
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.userService = userService;
        this.refreshTokenService = refreshTokenService;
        this.handlerDepartmentId = handlerDepartmentId;
        this.dummyPasswordHash = passwordEncoder.encode("senha-ficticia-" + UUID.randomUUID());
    }

    @Transactional
    public AuthenticatedSession login(LoginRequest request) {
        User user = authenticate(Emails.normalize(request.email()), request.password());

        // A senha confere antes de olhar o status: só quem sabe a senha descobre que a conta está inativa
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountNotActiveException();
        }

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

    private AuthenticatedSession openSession(User user, String rawRefreshToken) {
        List<String> roles = rolesOf(user);
        String accessToken = tokenService.issueAccessToken(user.getId(), user.getEmail(), roles);
        return new AuthenticatedSession(userService.getCurrentUser(user.getId(), roles), accessToken, rawRefreshToken);
    }

    private User authenticate(String email, String password) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            passwordEncoder.matches(password, dummyPasswordHash);
            throw new InvalidCredentialsException();
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return user;
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
